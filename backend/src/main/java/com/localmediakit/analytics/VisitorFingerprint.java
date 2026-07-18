package com.localmediakit.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * Anonymous visitor identity: sha256(ip | user-agent | day | salt).
 * The raw IP never leaves this class and is never stored; the hash rotates
 * daily, so visitors cannot be tracked across days either.
 */
@Component
public class VisitorFingerprint {

    private final String salt;

    public VisitorFingerprint(@Value("${app.analytics.salt}") String salt) {
        this.salt = salt;
    }

    public String of(String ip, String userAgent) {
        String material = ip + "|" + (userAgent == null ? "" : userAgent)
                + "|" + LocalDate.now(ZoneOffset.UTC) + "|" + salt;
        return sha256(material);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
