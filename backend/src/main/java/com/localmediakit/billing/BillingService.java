package com.localmediakit.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.user.Plan;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Stripe TEST MODE billing. Payment happens exclusively on Stripe's hosted
 * Checkout page (never in this deployment); this service only creates the
 * session and mirrors state back from signed webhooks.
 */
@Service
public class BillingService {

    /** Stripe statuses that grant PRO access. cancel_at_period_end keeps
     *  'active' until the period ends, so access naturally survives until then. */
    private static final Set<String> PRO_STATUSES = Set.of("active", "trialing");

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final ProcessedStripeEventRepository processedEventRepository;
    private final UserRepository userRepository;
    private final MediaKitAccess access;
    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String webhookSecret;
    private final String priceId;
    private final String frontendUrl;

    public BillingService(SubscriptionRepository subscriptionRepository,
                          ProcessedStripeEventRepository processedEventRepository,
                          UserRepository userRepository,
                          MediaKitAccess access,
                          ObjectMapper objectMapper,
                          @Value("${app.stripe.secret-key}") String secretKey,
                          @Value("${app.stripe.webhook-secret}") String webhookSecret,
                          @Value("${app.stripe.price-id}") String priceId,
                          @Value("${app.billing.frontend-url}") String frontendUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.processedEventRepository = processedEventRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.priceId = priceId;
        this.frontendUrl = frontendUrl;
        Stripe.apiKey = secretKey;
    }

    /**
     * Graceful-enable switch: real Stripe Checkout when the STRIPE_* env vars
     * are present, the demo plan-switch endpoints when they are not. The two
     * are mutually exclusive — see {@link #demoChangePlan}.
     */
    public boolean stripeConfigured() {
        return secretKey != null && !secretKey.isBlank() && !secretKey.startsWith("sk_test_dummy");
    }

    /** Creates a hosted Checkout session and returns its URL. No DB writes here. */
    public String createCheckoutUrl(String userEmail) {
        if (!stripeConfigured()) {
            throw new BillingNotConfiguredException();
        }
        User user = access.requireUser(userEmail);
        if (user.getPlan() == Plan.PRO) {
            throw new AlreadyProException();
        }
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(frontendUrl + "/dashboard?upgrade=success")
                    .setCancelUrl(frontendUrl + "/dashboard?upgrade=cancelled")
                    .setClientReferenceId(String.valueOf(user.getId()))
                    .setCustomerEmail(user.getEmail())
                    .build();
            return Session.create(params).getUrl();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create checkout session", e);
        }
    }

    @Transactional(readOnly = true)
    public BillingStatusResponse statusFor(String userEmail) {
        User user = access.requireUser(userEmail);
        Optional<Subscription> sub = subscriptionRepository.findByUserId(user.getId());
        return new BillingStatusResponse(
                user.getPlan().name(),
                sub.map(Subscription::getStatus).orElse(null),
                sub.map(Subscription::getCurrentPeriodEnd).map(Instant::toString).orElse(null),
                stripeConfigured());
    }

    /**
     * Demo-mode plan switch: lets an authenticated user flip THEIR OWN plan
     * when (and only when) Stripe is not configured. The guard is not
     * cosmetic: with real billing active this endpoint would be a payment
     * bypass, so it hard-fails with 403 the moment STRIPE_* env vars exist.
     */
    @Transactional
    public BillingStatusResponse demoChangePlan(String userEmail, Plan targetPlan) {
        if (stripeConfigured()) {
            throw new DemoUpgradeDisabledException();
        }
        User user = access.requireUser(userEmail);
        user.changePlan(targetPlan);
        userRepository.save(user);

        String status = targetPlan == Plan.PRO ? "demo" : "canceled";
        Subscription sub = subscriptionRepository.findByUserId(user.getId())
                .orElseGet(() -> new Subscription(user.getId(), null, null, status));
        sub.updateFromStripe(null, null, status, null);
        subscriptionRepository.save(sub);
        log.info("Demo plan switch: user {} -> {}", user.getId(), targetPlan);
        return statusFor(userEmail);
    }

    /**
     * Signed webhook processing. Everything (idempotency marker + effects)
     * commits in one transaction: a redelivery of a processed event is
     * swallowed, and a failed attempt rolls back completely so Stripe's retry
     * starts from a clean slate.
     */
    @Transactional
    public void processWebhook(String payload, String signatureHeader) {
        verifySignature(payload, signatureHeader);

        JsonNode event = parse(payload);
        String eventId = event.path("id").asText("");
        String type = event.path("type").asText("");
        if (eventId.isEmpty()) {
            throw new InvalidWebhookSignatureException();
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate Stripe event {} ({}) swallowed", eventId, type);
            return;
        }
        processedEventRepository.save(new ProcessedStripeEvent(eventId));

        JsonNode object = event.path("data").path("object");
        switch (type) {
            case "checkout.session.completed" -> handleCheckoutCompleted(object);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(object);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(object);
            default -> log.info("Ignoring Stripe event type {}", type);
        }
    }

    private void handleCheckoutCompleted(JsonNode session) {
        long userId = session.path("client_reference_id").asLong(-1);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("checkout.session.completed for unknown user {}", userId);
            return;
        }
        String customerId = session.path("customer").asText(null);
        String subscriptionId = session.path("subscription").asText(null);
        Subscription sub = subscriptionRepository.findByUserId(user.getId())
                .orElseGet(() -> new Subscription(user.getId(), customerId, subscriptionId, "active"));
        sub.updateFromStripe(customerId, subscriptionId, "active", null);
        subscriptionRepository.save(sub);
        user.changePlan(Plan.PRO);
        userRepository.save(user);
        log.info("User {} upgraded to PRO (subscription {})", user.getId(), subscriptionId);
    }

    private void handleSubscriptionUpdated(JsonNode stripeSub) {
        String subscriptionId = stripeSub.path("id").asText("");
        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("subscription.updated for unknown subscription {}", subscriptionId);
            return;
        }
        String status = stripeSub.path("status").asText("unknown");
        Instant periodEnd = stripeSub.hasNonNull("current_period_end")
                ? Instant.ofEpochSecond(stripeSub.path("current_period_end").asLong())
                : null;
        sub.updateFromStripe(null, null, status, periodEnd);
        subscriptionRepository.save(sub);
        applyPlan(sub.getUserId(), PRO_STATUSES.contains(status) ? Plan.PRO : Plan.FREE);
    }

    private void handleSubscriptionDeleted(JsonNode stripeSub) {
        String subscriptionId = stripeSub.path("id").asText("");
        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (sub == null) {
            log.warn("subscription.deleted for unknown subscription {}", subscriptionId);
            return;
        }
        sub.updateFromStripe(null, null, "canceled", null);
        subscriptionRepository.save(sub);
        applyPlan(sub.getUserId(), Plan.FREE);
    }

    private void applyPlan(Long userId, Plan plan) {
        userRepository.findById(userId).ifPresent(user -> {
            user.changePlan(plan);
            userRepository.save(user);
            log.info("User {} plan set to {}", userId, plan);
        });
    }

    private void verifySignature(String payload, String signatureHeader) {
        try {
            Webhook.Signature.verifyHeader(payload, signatureHeader, webhookSecret, 300L);
        } catch (Exception e) {
            throw new InvalidWebhookSignatureException();
        }
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new InvalidWebhookSignatureException();
        }
    }
}
