package com.localmediakit.user;

/**
 * The link in an address-verification mail is unknown, expired, already used,
 * or was superseded by a later request. All four are the same answer to
 * whoever opened it: ask again.
 */
public class InvalidEmailChangeTokenException extends RuntimeException {

    public InvalidEmailChangeTokenException() {
        super("This confirmation link is invalid or has expired. Please request the change again.");
    }
}
