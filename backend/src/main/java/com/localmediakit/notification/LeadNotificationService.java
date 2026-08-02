package com.localmediakit.notification;

import com.localmediakit.domain.ReentrancyGuard;
import com.localmediakit.lead.KitLead;
import com.localmediakit.lead.KitLeadRepository;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.observability.OperationalMetrics;
import com.localmediakit.shared.Locales;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Queues and delivers "a brand contacted you" emails.
 *
 * <p><b>Why an outbox rather than sending inline.</b> A lead is the valuable
 * thing; the email about it is not. Enqueuing happens in the caller's
 * transaction — a cheap insert next to the lead itself — and delivery happens
 * later in this class's own transactions. The mail provider is therefore never
 * touched while a brand's submission is being handled: it cannot slow that
 * request down, fail it, or lose the lead. If SMTP is down, rows simply wait.
 */
@Service
public class LeadNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LeadNotificationService.class);

    /** Message excerpt length in the email. The full text lives in the dashboard. */
    private static final int EXCERPT_LENGTH = 300;

    private final LeadNotificationRepository notificationRepository;
    private final KitLeadRepository leadRepository;
    private final MediaKitRepository mediaKitRepository;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    private final OperationalMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final String dashboardUrl;
    private final int hourlyCap;
    private final int batchSize;

    /** Own instance, not the shared bean: this batch and the domain job must
     *  not be able to block each other. */
    private final ReentrancyGuard batchGuard = new ReentrancyGuard();

    public LeadNotificationService(LeadNotificationRepository notificationRepository,
                                   KitLeadRepository leadRepository,
                                   MediaKitRepository mediaKitRepository,
                                   UserRepository userRepository,
                                   MailSender mailSender,
                                   OperationalMetrics metrics,
                                   TransactionTemplate transactionTemplate,
                                   // No inline default: the property is defined in
                                   // application.yml for every profile, and a missing
                                   // one should stop startup rather than quietly send
                                   // creators a link to somebody else's idea of home.
                                   @Value("${app.frontend-url}") String frontendUrl,
                                   @Value("${app.notifications.hourly-cap:12}") int hourlyCap,
                                   @Value("${app.notifications.batch-size:20}") int batchSize) {
        this.notificationRepository = notificationRepository;
        this.leadRepository = leadRepository;
        this.mediaKitRepository = mediaKitRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.dashboardUrl = frontendUrl.replaceAll("/+$", "") + "/dashboard";
        this.hourlyCap = hourlyCap;
        this.batchSize = batchSize;
    }

    /**
     * Records that a lead should be announced. Runs inside the caller's
     * transaction so a lead and its notification are committed together.
     *
     * <p>Every exit that does not queue anything is silent and harmless: an
     * unconfigured mailer, an owner who turned notifications off, or a burst
     * that has already used up the hourly allowance. None of them affect the
     * lead, which is already saved by the time this returns.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueFor(KitLead lead, MediaKit kit) {
        if (!mailSender.available()) {
            return;
        }
        User owner = userRepository.findById(kit.getUserId()).orElse(null);
        if (owner == null || !owner.isLeadNotificationsEnabled()) {
            return;
        }
        // Step 12 already caps one visitor at 3 submissions per kit per 30
        // minutes. This is the second layer: many different visitors (or many
        // kits) must not add up to a flooded mailbox.
        long recent = notificationRepository.countByRecipientEmailAndCreatedAtAfter(
                owner.getEmail(), Instant.now().minus(Duration.ofHours(1)));
        NotificationStatus status = recent >= hourlyCap
                ? NotificationStatus.SUPPRESSED
                : NotificationStatus.PENDING;

        notificationRepository.save(new LeadNotification(
                lead.getId(), owner.getEmail(), owner.getLocale(), status));
    }

    /**
     * Delivers whatever is due. Each row is handled in its own transaction so
     * one bad address cannot roll back the rest of the batch, and a thrown
     * exception is recorded on the row instead of escaping into the scheduler.
     *
     * @return how many rows were attempted, or -1 if a previous run is still
     *         going. The two are worth telling apart, and were not: "nothing
     *         was due" and "this did not run at all" both answered 0, which is
     *         exactly the ambiguity that makes a silent skip hard to diagnose.
     *         {@code StatsSyncService.runSyncBatch} already used -1 for it.
     */
    public int runDispatchBatch() {
        if (!mailSender.available()) {
            return 0;
        }
        int[] attempted = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    notificationRepository.findDue(Instant.now(), PageRequest.of(0, batchSize))
                            .stream().map(LeadNotification::getId).toList());
            if (dueIds == null) {
                return;
            }
            for (Long id : dueIds) {
                try {
                    // Each row commits on its own, so one unreachable address
                    // cannot roll back the deliveries that already succeeded.
                    transactionTemplate.executeWithoutResult(status -> deliver(id));
                } catch (RuntimeException e) {
                    log.warn("Lead notification {} could not be processed: {}", id, e.getMessage());
                }
                attempted[0]++;
            }
        });
        return ran ? attempted[0] : -1;
    }

    /** Called only from inside a transaction opened by {@link #runDispatchBatch()}. */
    private void deliver(Long notificationId) {
        LeadNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        KitLead lead = leadRepository.findById(notification.getLeadId()).orElse(null);
        if (lead == null) {
            // The lead was deleted while queued — nothing left to announce.
            notification.markAttemptFailed("Lead no longer exists");
            return;
        }
        MediaKit kit = mediaKitRepository.findById(lead.getMediaKitId()).orElse(null);
        // The locale was frozen when the lead arrived, so the mail reads in the
        // language the owner had then.
        String locale = Locales.orDefault(notification.getLocale());
        String fallbackTitle = "en".equals(locale) ? "your media kit" : "medya kitiniz";
        String kitTitle = kit == null ? fallbackTitle : kit.getTitle();

        try {
            mailSender.send(notification.getRecipientEmail(),
                    subjectFor(lead, locale), bodyFor(lead, kitTitle, locale));
            notification.markSent();
            metrics.leadNotificationSent();
        } catch (MailDeliveryException e) {
            notification.markAttemptFailed(e.getMessage());
            if (notification.getStatus() == NotificationStatus.FAILED) {
                // Terminal: the retry budget is gone. A creator will not be told
                // a brand contacted them, and until this was counted the only
                // trace was a column nobody queried. The lead itself is safe in
                // the inbox, so this is lost promptness, not lost data.
                metrics.leadNotificationFailed();
                log.error("Lead notification {} gave up after {} attempts: {}",
                        notificationId, notification.getAttempts(), e.getMessage());
            }
        }
    }

    private String subjectFor(KitLead lead, String locale) {
        return ("en".equals(locale) ? "New brand enquiry: " : "Yeni marka teklifi: ") + lead.getBrandName();
    }

    /**
     * Plain text, and deliberately incomplete.
     *
     * <p>The brand's email address is not included. It reaches the third-party
     * mail provider for no good reason if it is, and the owner is one click
     * away from it in their inbox. The message is excerpted for the same
     * reason. Nothing here identifies the visitor: no fingerprint, no IP, no
     * link that carries a token.
     */
    private String bodyFor(KitLead lead, String kitTitle, String locale) {
        String template = "en".equals(locale)
                ? """
                  Hello,

                  You have received a new brand enquiry through your "%s" media kit.

                  Brand   : %s
                  Message : %s

                  The full enquiry and the brand's contact details are in the Inbox tab
                  of your dashboard:
                  %s

                  You can switch these notifications off in your account settings.
                  """
                : """
                  Merhaba,

                  "%s" medya kitiniz uzerinden yeni bir marka teklifi aldiniz.

                  Marka   : %s
                  Mesaj   : %s

                  Teklifin tamamini ve marka iletisim bilgisini panonuzdaki Gelen Kutusu
                  sekmesinden gorebilirsiniz:
                  %s

                  Bu bildirimleri hesap ayarlarinizdan kapatabilirsiniz.
                  """;
        return template.formatted(kitTitle, lead.getBrandName(), excerpt(lead.getMessage()), dashboardUrl);
    }

    private String excerpt(String message) {
        String collapsed = message.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= EXCERPT_LENGTH
                ? collapsed
                : collapsed.substring(0, EXCERPT_LENGTH) + "...";
    }
}
