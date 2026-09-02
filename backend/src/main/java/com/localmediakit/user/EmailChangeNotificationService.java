package com.localmediakit.user;

import com.localmediakit.domain.ReentrancyGuard;
import com.localmediakit.notification.MailDeliveryException;
import com.localmediakit.notification.MailSender;
import com.localmediakit.notification.NotificationStatus;
import com.localmediakit.shared.Locales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends the "confirm your new address" mail, out of band.
 *
 * <p>Queued rather than sent inline for the reason the lead outbox exists: a
 * third party's outage should not be able to fail an operation the user already
 * completed. The request writes one row and returns; whether the provider is up
 * is not the caller's problem.
 *
 * <p>The token is minted here, per attempt, not when the change was requested.
 * A stored one would keep its original hour while the retries walk 1, 5 and 25
 * minutes behind it, so the last attempt could deliver a link that expired
 * before it arrived — which looks like a broken product rather than a slow one.
 */
@Service
public class EmailChangeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeNotificationService.class);

    private final EmailChangeRequestRepository requests;
    private final UserRepository userRepository;
    private final MailSender mailSender;
    private final TransactionTemplate transactionTemplate;
    private final ReentrancyGuard batchGuard = new ReentrancyGuard();
    private final String frontendUrl;
    private final Duration ttl;
    private final int batchSize;

    public EmailChangeNotificationService(
            EmailChangeRequestRepository requests,
            UserRepository userRepository,
            MailSender mailSender,
            TransactionTemplate transactionTemplate,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.email-change.ttl-minutes:60}") long ttlMinutes,
            @Value("${app.email-change.batch-size:20}") int batchSize) {
        this.requests = requests;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.transactionTemplate = transactionTemplate;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.batchSize = batchSize;
    }

    /**
     * @return how many rows were attempted, or -1 if a previous run is still
     *         going — "nothing was due" and "this did not run" are different
     *         answers, as they are in the other batches here.
     */
    public int runDispatchBatch() {
        if (!mailSender.available()) {
            return 0;
        }
        int[] attempted = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    requests.findDue(NotificationStatus.PENDING, Instant.now(),
                                    PageRequest.of(0, batchSize))
                            .stream().map(EmailChangeRequest::getId).toList());
            if (dueIds == null) {
                return;
            }
            for (Long id : dueIds) {
                try {
                    transactionTemplate.executeWithoutResult(status -> deliver(id));
                } catch (RuntimeException e) {
                    log.warn("Email change notification {} could not be processed: {}",
                            id, e.getMessage());
                }
                attempted[0]++;
            }
        });
        return ran ? attempted[0] : -1;
    }

    /** Called only from inside a transaction opened by {@link #runDispatchBatch()}. */
    private void deliver(Long requestId) {
        EmailChangeRequest request = requests.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        if (!request.isRedeemable(Instant.now())) {
            // Redeemed, expired, or superseded by a later request while this
            // waited. Mailing a fresh secret now would revive a decision the
            // account has already moved past.
            request.markObsolete();
            return;
        }
        User user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            request.markObsolete();
            return;
        }

        String plaintext = EmailChangeService.newToken();
        request.rotate(EmailChangeService.hash(plaintext), ttl);

        String locale = Locales.orDefault(request.getLocale());
        try {
            mailSender.send(request.getNewEmail(), subjectFor(locale),
                    bodyFor(plaintext, request.getNewEmail(), user.getEmail(), locale));
            request.markSent();
        } catch (MailDeliveryException e) {
            request.markAttemptFailed(e.getMessage());
            if (request.getStatus() == NotificationStatus.FAILED) {
                // Terminal. Somebody asked to move their account and never
                // heard anything; the account is unchanged, which is the safe
                // outcome, but they are owed an explanation nobody can give.
                log.error("Email change notification {} gave up after {} attempts: {}",
                        requestId, request.getAttempts(), e.getMessage());
            }
        }
    }

    private String subjectFor(String locale) {
        return "en".equals(locale)
                ? "Confirm your new LocalMediaKit address"
                : "LocalMediaKit yeni adresinizi doğrulayın";
    }

    /**
     * Names both addresses, and says what to do if this was not you.
     *
     * <p>The old address is in the body because this mail is the only warning
     * an owner gets that their account is being moved. If it arrives at an
     * address the reader does not recognise, "somebody is moving that account"
     * is the useful thing to have said — and doing nothing is the correct
     * response, since the change needs this link to happen at all.
     */
    private String bodyFor(String plaintextToken, String newEmail, String currentEmail, String locale) {
        String link = frontendUrl + "/confirm-email/" + plaintextToken;
        long minutes = ttl.toMinutes();
        return "en".equals(locale)
                ? """
                  Hello,

                  Confirm that you can read mail at %s, and the LocalMediaKit
                  account currently signed in as %s will move to it:
                  %s

                  It works once and expires in %d minutes. Until you open it,
                  nothing changes and you keep signing in with %s.

                  If you did not ask for this, ignore this message -- without
                  this link the account stays exactly where it is.
                  """.formatted(newEmail, currentEmail, link, minutes, currentEmail)
                : """
                  Merhaba,

                  %s adresine ulaşabildiğinizi doğrulayın; şu anda %s ile giriş
                  yapılan LocalMediaKit hesabı bu adrese taşınacak:
                  %s

                  Link tek kullanımlıktır ve %d dakika sonra geçersiz olur. Siz
                  açmadıkça hiçbir şey değişmez, %s ile girmeye devam edersiniz.

                  Bu isteği siz yapmadıysanız bu mesajı yok sayabilirsiniz --
                  bu link olmadan hesap olduğu yerde kalır.
                  """.formatted(newEmail, currentEmail, link, minutes, currentEmail);
    }
}
