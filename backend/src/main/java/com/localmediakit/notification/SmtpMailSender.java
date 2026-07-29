package com.localmediakit.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP delivery.
 *
 * <p>Graceful-enable, the same shape the Stripe and YouTube integrations use:
 * with no host or sender configured the feature reports itself unavailable and
 * nothing is ever queued. The application starts and behaves identically
 * either way — an unconfigured deployment simply has no lead notifications.
 *
 * <p>No provider appears anywhere in this class. The sending account is
 * whatever SMTP credentials the environment supplies.
 */
@Component
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String host;
    private final String from;
    private final String fromName;

    public SmtpMailSender(JavaMailSender javaMailSender,
                          @Value("${spring.mail.host:}") String host,
                          @Value("${app.mail.from:}") String from,
                          @Value("${app.mail.from-name:LocalMediaKit}") String fromName) {
        this.javaMailSender = javaMailSender;
        this.host = host;
        this.from = from;
        this.fromName = fromName;
    }

    @Override
    public boolean available() {
        return host != null && !host.isBlank() && from != null && !from.isBlank();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        // Most providers reject a From that is not the verified sender, so the
        // display name is decorative and the address is the configured one.
        message.setFrom(fromName.isBlank() ? from : "%s <%s>".formatted(fromName, from));
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new MailDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }
}
