package com.localmediakit.recovery;

import com.localmediakit.notification.MailDeliveryException;
import com.localmediakit.notification.MailSender;
import com.localmediakit.notification.NotificationStatus;
import com.localmediakit.observability.OperationalMetrics;
import com.localmediakit.domain.ReentrancyGuard;
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
 * Drains the password-reset outbox.
 *
 * <p>Each attempt mints the secret it is about to mail. That is the whole
 * design in one sentence, and it buys two things at once: the plaintext never
 * exists outside this method, so the queue cannot leak working links; and the
 * token's 30 minutes start when the mail actually leaves, so a delivery that
 * waited out a backoff — or a sleeping instance — still carries a link that
 * works when it lands.
 *
 * <p>Batch discipline is the lead outbox's: one transaction per row so a bad
 * address cannot roll back the deliveries around it, a reentrancy guard so runs
 * cannot overlap, and failures recorded on the row rather than thrown into the
 * scheduler.
 */
@Service
public class PasswordResetNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetNotificationService.class);

    private final PasswordResetNotificationRepository notificationRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final MailSender mailSender;
    private final OperationalMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final String frontendUrl;
    private final Duration ttl;
    private final int batchSize;

    private final ReentrancyGuard batchGuard = new ReentrancyGuard();

    public PasswordResetNotificationService(
            PasswordResetNotificationRepository notificationRepository,
            PasswordResetTokenRepository tokenRepository,
            MailSender mailSender,
            OperationalMetrics metrics,
            TransactionTemplate transactionTemplate,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes,
            @Value("${app.password-reset.batch-size:20}") int batchSize) {
        this.notificationRepository = notificationRepository;
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.batchSize = batchSize;
    }

    /**
     * @return how many rows were attempted, or -1 if a previous run is still
     *         going — "nothing was due" and "this did not run" are different
     *         answers and the other batches in this application already tell
     *         them apart.
     */
    public int runDispatchBatch() {
        if (!mailSender.available()) {
            return 0;
        }
        int[] attempted = {0};
        boolean ran = batchGuard.tryRun(() -> {
            List<Long> dueIds = transactionTemplate.execute(status ->
                    notificationRepository.findDue(Instant.now(), PageRequest.of(0, batchSize))
                            .stream().map(PasswordResetNotification::getId).toList());
            if (dueIds == null) {
                return;
            }
            for (Long id : dueIds) {
                try {
                    transactionTemplate.executeWithoutResult(status -> deliver(id));
                } catch (RuntimeException e) {
                    log.warn("Password reset notification {} could not be processed: {}",
                            id, e.getMessage());
                }
                attempted[0]++;
            }
        });
        return ran ? attempted[0] : -1;
    }

    /** Called only from inside a transaction opened by {@link #runDispatchBatch()}. */
    private void deliver(Long notificationId) {
        PasswordResetNotification notification =
                notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }
        PasswordResetToken token = tokenRepository.findById(notification.getTokenId()).orElse(null);
        if (token == null || token.getUsedAt() != null) {
            // Redeemed already, or invalidated by a later request. Mailing a new
            // secret now would revive a permission the account has moved past.
            notification.markObsolete();
            return;
        }

        String plaintext = PasswordResetService.newToken();
        token.rotate(PasswordResetService.hash(plaintext), ttl);

        String locale = Locales.orDefault(notification.getLocale());
        try {
            mailSender.send(notification.getRecipientEmail(), subjectFor(locale),
                    bodyFor(plaintext, locale));
            notification.markSent();
            metrics.passwordResetMailSent();
        } catch (MailDeliveryException e) {
            notification.markAttemptFailed(e.getMessage());
            if (notification.getStatus() == NotificationStatus.FAILED) {
                // Terminal. Somebody who asked to get back into their account
                // never heard anything, and the only honest description of that
                // is an error, not a warning.
                metrics.passwordResetMailFailed();
                log.error("Password reset notification {} gave up after {} attempts: {}",
                        notificationId, notification.getAttempts(), e.getMessage());
            }
        }
    }

    private String subjectFor(String locale) {
        return "en".equals(locale)
                ? "Reset your LocalMediaKit password"
                : "LocalMediaKit sifrenizi sifirlayin";
    }

    /**
     * Deliberately short, and says what to do if it was not you.
     *
     * <p>An unrequested reset mail is the first sign somebody is trying an
     * address, and the useful advice is "ignore it" — the link expires and the
     * current password still works until it is used.
     */
    private String bodyFor(String plaintextToken, String locale) {
        String link = frontendUrl + "/reset/" + plaintextToken;
        long minutes = ttl.toMinutes();
        return "en".equals(locale)
                ? """
                  Hello,

                  Use this link to choose a new password:
                  %s

                  It works once and expires in %d minutes.

                  If you did not ask for this, you can ignore this message --
                  your current password still works and nothing has changed.
                  """.formatted(link, minutes)
                : """
                  Merhaba,

                  Yeni sifre belirlemek icin bu linki kullanin:
                  %s

                  Link tek kullanimliktir ve %d dakika sonra gecersiz olur.

                  Bu istegi siz yapmadiysaniz bu mesaji yok sayabilirsiniz --
                  mevcut sifreniz gecerli kalir ve hicbir sey degismez.
                  """.formatted(link, minutes);
    }
}
