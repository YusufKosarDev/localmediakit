package com.localmediakit.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own account.
 *
 * <p>Every route is rooted at {@code /api/me} and derives the subject from the
 * JWT principal. There is deliberately no {@code /api/users/{id}} counterpart:
 * with no identifier in the path or body, one user cannot address another's
 * record at all.
 *
 * <p>Profile edits use PUT rather than PATCH because the CORS policy allows
 * GET/POST/PUT/DELETE — adding PATCH would widen it for no benefit.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AccountService accountService;

    public MeController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public MeResponse me(Authentication authentication) {
        return accountService.me(email(authentication));
    }

    @PutMapping
    public MeResponse updateProfile(Authentication authentication,
                                    @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(email(authentication), request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(email(authentication), request);
    }

    /**
     * @return a replacement session token. The caller must swap it in: the
     *         token it authenticated with names the old address and is dead.
     */
    @PostMapping("/email")
    public ChangeEmailResponse changeEmail(Authentication authentication,
                                           @Valid @RequestBody ChangeEmailRequest request) {
        String token = accountService.changeEmail(email(authentication), request);
        return new ChangeEmailResponse(token,
                accountService.me(AccountService.normalizeEmail(request.newEmail())));
    }

    /** Irreversible: takes every published page of this account offline too. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication authentication,
                              @Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(email(authentication), request);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }

    public record ChangeEmailResponse(String token, MeResponse user) {
    }
}
