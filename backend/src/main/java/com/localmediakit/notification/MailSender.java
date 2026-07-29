package com.localmediakit.notification;

/**
 * Outbound plain-text mail.
 *
 * <p>Deliberately narrow and provider-neutral. The implementation speaks SMTP,
 * which every candidate provider (Brevo, SendGrid, Resend, Mailgun) offers, so
 * switching provider is a change of environment variables rather than of code.
 */
public interface MailSender {

    /** Graceful-enable: false when SMTP is unconfigured, and nothing is queued. */
    boolean available();

    /**
     * @throws MailDeliveryException if the provider rejected or could not be
     *         reached — the caller decides whether to retry
     */
    void send(String to, String subject, String body);
}
