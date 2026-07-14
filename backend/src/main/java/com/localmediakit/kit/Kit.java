package com.localmediakit.kit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Minimal media kit record for the Step 0 architecture proof:
 * a slug (public URL segment) mapped to some published content.
 */
@Entity
@Table(name = "kits")
public class Kit {

    @Id
    private String slug;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Kit() {
        // for JPA
    }

    public Kit(String slug, String content) {
        this.slug = slug;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public String getSlug() {
        return slug;
    }

    public String getContent() {
        return content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
