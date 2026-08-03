package com.localmediakit.recovery;

import com.localmediakit.notification.MailSender;
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
 * <p><b>The mail is queued, not sent here.</b> It used to be sent in this
 * request, on the argument that an outbox would need the plaintext token and
 * the hash exists precisely to withhold it. The argument was sound and the
 * conclusion still cost something: talking to a mail provider inline made a
 * request for an address with an account measurably slower than one without —
 * about 0.2s against 1.5s — and the first promise on this page is that those
 * two are indistinguishable. Timing is an answer. It made this endpoint the
 * membership oracle it was written not to be.
 *
 * <p>The plaintext objection is answered rather than overruled: the queue holds
 * a reference to the token row, never the secret, and
 * {@link PasswordResetNotificationService} mints a fresh one for each delivery
 * attempt. So nothing that can log in is ever at rest, and a mail that waited
 * out a retry still carries a link with its full lifetime ahead of it.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** 32 random bytes, url-safe base64. Guessing is not a strategy against this. */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetNotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;
    private final TransactionTemplate transactionTemplate;
    private final Duration ttl;
    private final int maxRequestsPerHour;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordResetNotificationRepository notificationRepository,
                                PasswordEncoder passwordEncoder,
                                MailSender mailSender,
                                TransactionTemplate transactionTemplate,
                                @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes,
                                @Value("${app.password-reset.max-per-hour:5}") int maxRequestsPerHour) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.transactionTemplate = transactionTemplate;
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
     *
     * <p>Every path does the same small amount of database work and returns.
     * No network call happens here, which is what keeps an address with an
     * account and one without the same length of request — the difference that
     * remains is one insert, far below the noise of the wire, where it used to
     * be a second and a half of SMTP.
     *
     * <p>The token minted here is a placeholder that is never mailed: the
     * dispatcher rotates the row before each send. Writing one now rather than
     * a null keeps the column non-null and the hourly cap counting requests.
     */
    public void requestReset(String email) {
        if (!mailSender.available()) {
            // Nothing can be delivered, so nothing is issued -- a token nobody
            // receives is a row that exists only to be guessed at.
            log.info("Password reset requested while mail is unconfigured; ignoring");
            return;
        }
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(normalised).orElse(null);
            if (user == null) {
                return;
            }
            long recent = tokenRepository.countByUserIdAndCreatedAtAfter(
                    user.getId(), Instant.now().minus(Duration.ofHours(1)));
            if (recent >= maxRequestsPerHour) {
                log.info("Password reset for user {} suppressed by the hourly cap", user.getId());
                return;
            }
            PasswordResetToken token = tokenRepository.save(
                    new PasswordResetToken(user.getId(), hash(newToken()), ttl));
            // Same transaction as the token, the rule the lead outbox set: a
            // queued mail that references a row which was rolled back would be
            // a delivery attempt with nothing behind it.
            notificationRepository.save(new PasswordResetNotification(
                    token.getId(), user.getEmail(), Locales.orDefault(user.getLocale())));
        });
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
}
