package com.localmediakit.user;

/**
 * Raised when a destructive settings operation targets the shared demo
 * account. Its credentials are published on the login page, so without this
 * guard any visitor could change the demo password (locking everyone else out
 * until the nightly reset) or delete the account outright.
 *
 * <p>Only the destructive operations are blocked. Display name, avatar and
 * theme stay editable so the settings page is still explorable — the nightly
 * reset restores them.
 */
public class ProtectedAccountException extends RuntimeException {

    public ProtectedAccountException(String message) {
        super(message);
    }
}
