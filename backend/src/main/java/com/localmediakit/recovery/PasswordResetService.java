package com.localmediakit.recovery;

import com.localmediakit.notification.MailDeliveryException;
import com.localmediakit.notification.MailSender;
import com.localmediakit.observability.OperationalMetrics;
import com.localmediakit.shared.Locales;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Forgotten-password recovery.
 *
 * <p><b>The endpoint never says whether the address exists.</b> Every path
 * returns quietly, so an unknown address, a demo account and a real one are
 * indistinguishable from outside. A "no such user" would turn this into a free
 * membership oracle for anyone holding a list of email addresses, which is
 * worth more than the reset itself.
 *
 * <p><b>The token is stored as a hash.</b> A leaked database must not hand
 * somebody a working link for every account that recently used this flow.
 *
 * <p><b>The mail is sent after the commit, in this request.</b> Not through the
 * outbox, and the two constraints are why: a background job would need the
 * plaintext, which is exactly what the hash exists to withhold. The outbox
 * protects leads, which are valuable and unrecoverable; a lost reset mail costs
 * one more click. So this follows the publish path's rule instead -- commit
 * first, then make the network call, never hold a transaction across it.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** 32 random bytes, url-safe base64. Guessing is not a strategy against this. */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;
    private final OperationalMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final String frontendUrl;
    private final Duration ttl;
    private final int maxRequestsPerHour;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                MailSender mailSender,
                                OperationalMetrics metrics,
                                TransactionTemplate transactionTemplate,
                                @Value("${app.frontend-url}") String frontendUrl,
                                @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes,
                                @Value("${app.password-reset.max-per-hour:5}") int maxRequestsPerHour) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    /**
     * Issues a reset link if the address belongs to an account, and says
     * nothing either way.
     *
     * <p>Two throttles, because they stop different things. The rate-limit
     * filter caps requests per client IP; this per-account cap stops many
     * clients being used to bury one person in mail.
     */
    public void requestReset(String email) {
        if (!mailSender.available()) {
            // Nothing can be delivered, so nothing is issued -- a token nobody
            // receives is a row that exists only to be guessed at.
            log.info("Password reset requested while mail is unconfigured; ignoring");
            return;
        }
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        Issued issued = transactionTemplate.execute(status -> {
            User user = userRepository.findByEmail(normalised).orElse(null);
            if (user == null) {
                return null;
            }
            long recent = tokenRepository.countByUserIdAndCreatedAtAfter(
                    user.getId(), Instant.now().minus(Duration.ofHours(1)));
            if (recent >= maxRequestsPerHour) {
                log.info("Password reset for user {} suppressed by the hourly cap", user.getId());
                return null;
            }
            String token = newToken();
            tokenRepository.save(new PasswordResetToken(user.getId(), hash(token), ttl));
            return new Issued(user.getEmail(), Locales.orDefault(user.getLocale()), token);
        });

        if (issued == null) {
            return;
        }
        // After the commit, exactly like the publish path's revalidation call.
        deliver(issued);
    }

    /**
     * Consumes a token and sets the new password.
     *
     * @throws InvalidResetTokenException if it is unknown, expired or used
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken row = tokenRepository.findByTokenHash(hash(token))
                .filter(t -> t.isRedeemable(Instant.now()))
                .orElseThrow(InvalidResetTokenException::new);
        User user = userRepository.findById(row.getUserId())
                .orElseThrow(InvalidResetTokenException::new);

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        row.markUsed();
        tokenRepository.invalidateOthers(user.getId(), row.getId(), Instant.now());
        log.info("Password reset completed for user {}", user.getId());
    }

    private void deliver(Issued issued) {
        try {
            mailSender.send(issued.email(), subjectFor(issued.locale()), bodyFor(issued));
        } catch (MailDeliveryException e) {
            // Counted rather than surfaced: telling the caller the send failed
            // would also tell them the address exists.
            metrics.passwordResetMailFailed();
            log.error("Password reset mail could not be delivered: {}", e.getMessage());
        }
    }

    private String subjectFor(String locale) {
        return "en".equals(locale) ? "Reset your LocalMediaKit password" : "LocalMediaKit sifrenizi sifirlayin";
    }

    /**
     * Deliberately short, and says what to do if it was not you.
     *
     * <p>An unrequested reset mail is the first sign somebody is trying an
     * address, and the useful advice is "ignore it" -- the link expires and the
     * current password still works until it is used.
     */
    private String bodyFor(Issued issued) {
        String link = frontendUrl + "/reset/" + issued.token();
        long minutes = ttl.toMinutes();
        return "en".equals(issued.locale())
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

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * sha256, not bcrypt. What is hashed is 32 bytes this server generated, so
     * there is no dictionary to slow down -- and lookup is by hash, which a
     * per-row salt would make impossible.
     */
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (token == null ? "" : token).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** What the committed transaction hands to the send that follows it. */
    private record Issued(String email, String locale, String token) {
    }
}
