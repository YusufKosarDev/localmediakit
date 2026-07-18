package com.localmediakit.billing;

public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException() {
        super("Invalid webhook signature");
    }
}
