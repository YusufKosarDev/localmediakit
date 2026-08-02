package com.localmediakit.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * A labelled link to one kit, so views through it can be attributed to whoever
 * it was sent to.
 *
 * <p>The label is the creator's own words: they knew who they were sending it
 * to, so nothing has to be inferred about the visitor. The anonymous
 * fingerprint stays exactly as anonymous as it was.
 */
@Entity
@Table(name = "kit_share_links")
public class KitShareLink {

    /**
     * 18 random bytes, url-safe base64 -> 24 characters. Long enough that
     * guessing one is pointless, short enough to sit in a link somebody pastes
     * into a message. Not a secret: it identifies a link, it does not open one.
     */
    private static final int TOKEN_BYTES = 18;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_kit_id", nullable = false)
    private Long mediaKitId;

    @Column(nullable = false, length = 32)
    private String token;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KitShareLink() {
        // for JPA
    }

    public KitShareLink(Long mediaKitId, String label) {
        this.mediaKitId = mediaKitId;
        this.label = label;
        this.token = newToken();
        this.createdAt = Instant.now();
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Stops future views being attributed here. Past ones keep their
     * attribution: they really did come through this link, and rewriting that
     * would make the history a worse record than no history.
     */
    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getMediaKitId() {
        return mediaKitId;
    }

    public String getToken() {
        return token;
    }

    public String getLabel() {
        return label;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
