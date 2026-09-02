package com.localmediakit.user;

import com.localmediakit.auth.EmailAlreadyUsedException;
import com.localmediakit.notification.MailSender;
import com.localmediakit.shared.Locales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Moving an account to another address, with proof that somebody can read mail
 * there.
 *
 * <p><b>The password is still the control that matters</b> and is not being
 * softened: it is what stops a session left open in a library from moving the
 * account to an attacker's address, and a link sent to the new address would
 * not stop that at all — the attacker chose the address. Verification answers a
 * different question, and a smaller one that nevertheless costs the whole
 * account when it goes wrong: whether the address was typed correctly. It is
 * the login, so a typo locks the owner out of an account that now belongs to a
 * mailbox nobody reads, and no amount of re-authentication catches it.
 *
 * <p><b>Nothing moves until the link is opened.</b> The requested address sits
 * in its own row and {@code users.email} is untouched, so an unconfirmed
 * request costs nothing and expires on its own.
 *
 * <p><b>When mail is unconfigured the change applies immediately</b>, as it did
 * before any of this existed. That is the graceful-enable rule the rest of the
 * application follows — a blank {@code MAIL_HOST} makes mail features dark
 * rather than making the application refuse to run — and here it degrades to
 * exactly the control the previous design relied on, which is the password
 * that was already checked. A local clone with no SMTP server keeps working;
 * it does not get a feature that silently does nothing.
 */
@Service
public class EmailChangeService {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeService.class);

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailChangeRequestRepository requests;
    private final MailSender mailSender;
    private final Duration ttl;

    public EmailChangeService(UserRepository userRepository,
                              EmailChangeRequestRepository requests,
                              MailSender mailSender,
                              @Value("${app.email-change.ttl-minutes:60}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.requests = requests;
        this.mailSender = mailSender;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Whether a request will be verified rather than applied on the spot. */
    public boolean verificationAvailable() {
        return mailSender.available();
    }

    /**
     * Queues a verification mail to the new address, and returns nothing that
     * would let the caller act as the new address yet.
     *
     * <p>Called after the password has been checked.
     */
    @Transactional
    public void requestChange(User user, String newEmail) {
        EmailChangeRequest request = requests.save(new EmailChangeRequest(
                user.getId(), newEmail, hash(newToken()), ttl,
                Locales.orDefault(user.getLocale())));

        // Asking again replaces the previous ask. Two live links in two inboxes
        // would both move the account, and the owner would have no way to say
        // which one they meant.
        requests.findOtherLive(user.getId(), request.getId())
                .forEach(EmailChangeRequest::markObsolete);

        log.info("Email change requested for user {}", user.getId());
    }

    /**
     * Redeems the link and moves the account.
     *
     * <p>The uniqueness check runs again here, not only when the change was
     * requested: another account can claim the address in the minutes between
     * the two, and the second one to confirm must be told so rather than
     * colliding with a unique constraint.
     *
     * @return the address the account now has
     * @throws InvalidEmailChangeTokenException if unknown, expired or used
     */
    @Transactional
    public String confirm(String token) {
        EmailChangeRequest request = requests.findByTokenHash(hash(token))
                .filter(r -> r.isRedeemable(Instant.now()))
                .orElseThrow(InvalidEmailChangeTokenException::new);

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(InvalidEmailChangeTokenException::new);

        String newEmail = request.getNewEmail();
        if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyUsedException("Bu e-posta başka bir hesapta kayıtlı.");
        }

        user.changeEmail(newEmail);
        request.markUsed();
        log.info("Email change confirmed for user {}", user.getId());
        return newEmail;
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * sha256, for the same reason the reset token uses it: what is hashed is 32
     * bytes this server generated, so there is no dictionary to slow down, and
     * lookup is by hash, which a per-row salt would make impossible.
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
