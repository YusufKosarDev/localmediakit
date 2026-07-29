package com.localmediakit.notification;

/** The provider rejected the message or could not be reached. Retryable. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
