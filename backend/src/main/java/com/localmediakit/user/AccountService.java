package com.localmediakit.user;

import com.localmediakit.auth.AuthService;
import com.localmediakit.auth.EmailAlreadyUsedException;
import com.localmediakit.auth.InvalidCredentialsException;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.mediakit.MediaKitService;
import com.localmediakit.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import java.util.List;

/**
 * Self-service account management.
 *
 * <p><b>Ownership.</b> Every method starts from the authenticated principal's
 * email and nothing else. No user id is ever accepted from a path, query or
 * request body, so there is no identifier for a caller to tamper with — an
 * IDOR is not defended against here, it is unrepresentable.
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitService mediaKitService;
    private final EmailChangeService emailChangeService;
    private final TransactionTemplate transactionTemplate;
    private final String protectedEmail;

    public AccountService(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          MediaKitRepository mediaKitRepository,
                          MediaKitService mediaKitService,
                          EmailChangeService emailChangeService,
                          TransactionTemplate transactionTemplate,
                          @Value("${app.demo.email:demo@localmediakit.app}") String protectedEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mediaKitRepository = mediaKitRepository;
        this.mediaKitService = mediaKitService;
        this.emailChangeService = emailChangeService;
        this.transactionTemplate = transactionTemplate;
        this.protectedEmail = protectedEmail;
    }

    @Transactional(readOnly = true)
    public MeResponse me(String email) {
        return MeResponse.from(require(email));
    }

    /**
     * Display name, avatar and theme. Allowed on the demo account too — none
     * of it can lock anyone out, and the nightly reset undoes it.
     */
    @Transactional
    public MeResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = require(email);
        Theme theme = request.theme() == null ? user.getTheme() : request.theme();
        boolean leadEmails = request.leadNotificationsEnabled() == null
                ? user.isLeadNotificationsEnabled()
                : request.leadNotificationsEnabled();
        String locale = request.locale() == null || request.locale().isBlank()
                ? user.getLocale()
                : request.locale();
        user.updateProfile(request.displayName().trim(), request.avatarUrl(), theme, leadEmails, locale);
        return MeResponse.from(user);
    }

    /**
     * The current password is verified before anything changes. The session
     * token stays valid: its subject is the email, which this does not touch.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = require(email);
        requireNotProtected(user, "Demo hesabının şifresi değiştirilemez.");
        requireCurrentPassword(user, request.currentPassword());
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * Verifies the password, then either sends the new address a link or, when
     * mail is unavailable, applies the change on the spot.
     *
     * <p>The password is the control that stops somebody else's session moving
     * this account. Verification is the control that catches the owner's own
     * typo, which is the failure that costs the whole account, because the
     * address is the login. They guard different things and this method runs
     * both when it can.
     *
     * <p>Uniqueness is checked here so a doomed request is refused before a
     * mail goes out, and again at confirmation, because another account can
     * claim the address in between.
     *
     * @return a replacement session token when the change applied immediately,
     *         or empty when a verification link was sent instead and nothing
     *         has moved yet
     */
    @Transactional
    public Optional<String> changeEmail(String email, ChangeEmailRequest request) {
        User user = require(email);
        requireNotProtected(user, "Demo hesabının e-postası değiştirilemez.");
        requireCurrentPassword(user, request.currentPassword());

        String newEmail = normalizeEmail(request.newEmail());
        if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyUsedException("Bu e-posta başka bir hesapta kayıtlı.");
        }

        if (!emailChangeService.verificationAvailable()) {
            // Graceful-enable, as everywhere else mail is involved: with no
            // provider configured the feature degrades to what it was before
            // verification existed rather than disappearing, and what it
            // degrades to is the password check that has already passed.
            user.changeEmail(newEmail);
            return Optional.of(jwtService.generateToken(newEmail));
        }

        emailChangeService.requestChange(user, newEmail);
        return Optional.empty();
    }

    /**
     * Hard-deletes the account and everything under it.
     *
     * <p>Kits are removed one at a time through {@link MediaKitService#delete},
     * not by a bulk query, because that path is what takes a published page
     * offline: it detaches the version pointer, lets the cascade run, and then
     * revalidates the live slug after the transaction commits. Leaving a
     * deleted user's kit reachable on the open web would be the one outcome
     * this operation must never produce.
     *
     * <p>The user row is removed last, once nothing references it.
     */
    public void deleteAccount(String email, DeleteAccountRequest request) {
        User user = require(email);
        requireNotProtected(user, "Demo hesabı silinemez.");
        requireCurrentPassword(user, request.currentPassword());
        if (!DeleteAccountRequest.REQUIRED_CONFIRMATION.equals(request.confirmation().trim())) {
            throw new InvalidCredentialsException("Onay metni hatali.");
        }

        List<MediaKit> kits = mediaKitRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        for (MediaKit kit : kits) {
            // Same call the dashboard's delete button makes: cascade + evict
            // the public page. Reused rather than reimplemented so account
            // deletion can never drift from kit deletion.
            mediaKitService.delete(email, kit.getId());
        }
        // Runs after the kits are gone, so no foreign key can block it. Driven
        // by TransactionTemplate rather than @Transactional: this is a
        // self-invocation, which the annotation proxy would not intercept.
        Long userId = user.getId();
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.deleteById(userId);
        });
    }

    private User require(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private void requireCurrentPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Mevcut şifre hatalı.");
        }
    }

    private void requireNotProtected(User user, String message) {
        if (user.getEmail().equalsIgnoreCase(protectedEmail)) {
            throw new ProtectedAccountException(message);
        }
    }

    /**
     * Shared with registration and login on purpose: if this drifted, the
     * uniqueness check here and the lookup that signs a user in would disagree
     * about which addresses are the same address.
     */
    public static String normalizeEmail(String email) {
        return AuthService.normalizeEmail(email);
    }
}
