package com.localmediakit.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redeeming the link from an address-verification mail.
 *
 * <p>Public, and under {@code /api/auth} for the same reason password-reset
 * confirmation is: the person opening this link is doing so in whatever browser
 * their mail client handed it to, which is usually not the one holding the
 * session that asked for the change.
 *
 * <p><b>It deliberately returns no session.</b> Redeeming this link proves
 * somebody can read mail at the new address; it does not prove they are the
 * account's owner, and the two are only the same person when nothing has gone
 * wrong. Handing back a token would turn a forwarded or intercepted mail into
 * full account access. The owner signs in again with the new address, which
 * they can do because they know the password — they typed it to get here.
 *
 * <p>Password reset is the deliberate contrast: it hands out access because
 * proving control of the address is the <em>whole</em> point there, and the
 * secret it was protecting has just been replaced.
 */
@RestController
@RequestMapping("/api/auth/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    public EmailChangeController(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody EmailChangeConfirmRequest request) {
        emailChangeService.confirm(request.token());
    }
}
