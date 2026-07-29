package com.localmediakit.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Account identity only — shown on the dashboard, never copied into a kit.
     * A kit carries its own avatar because that one is published content;
     * keeping them separate means editing a profile can never rewrite a
     * snapshot that a brand is already looking at.
     */
    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    /** Dashboard appearance. The public page uses the kit's own theme instead. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * When the user dismissed the onboarding guidance; null while it should
     * still be offered. Only the dismissal lives here — the individual steps
     * are derived from the account's real data, so they cannot go stale.
     */
    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    /**
     * Whether to email this user when a brand submits their contact form.
     * Defaults on: publishing a contact form is asking to be contacted.
     */
    @Column(name = "lead_notifications_enabled", nullable = false)
    private boolean leadNotificationsEnabled;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        // The product is fully free: every account starts on PRO. The FREE tier
        // and the PlanPolicy gating around it remain in the codebase (and are
        // still exercised by tests) so paid plans can be reintroduced by
        // flipping this default back — no gating logic was torn out.
        this.plan = Plan.PRO;
        this.theme = Theme.LIGHT;
        this.leadNotificationsEnabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Theme getTheme() {
        return theme;
    }

    public boolean isLeadNotificationsEnabled() {
        return leadNotificationsEnabled;
    }

    public Plan getPlan() {
        return plan;
    }

    /** Plan changes go through billing (or tests); there is no free-form setter. */
    public void changePlan(Plan plan) {
        this.plan = plan;
    }

    /**
     * Editable profile fields, applied together so updatedAt can never drift
     * from the change that caused it. A blank avatar clears the field.
     */
    public void updateProfile(String displayName, String avatarUrl, Theme theme,
                              boolean leadNotificationsEnabled) {
        this.displayName = displayName;
        this.avatarUrl = (avatarUrl == null || avatarUrl.isBlank()) ? null : avatarUrl.trim();
        this.theme = theme;
        this.leadNotificationsEnabled = leadNotificationsEnabled;
        touch();
    }

    /** Callers must pass an already-encoded hash — this never sees a raw password. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }

    /** Callers must pass an already-normalized, uniqueness-checked address. */
    public void changeEmail(String email) {
        this.email = email;
        touch();
    }

    public Instant getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    /** Idempotent: dismissing again keeps the original timestamp. */
    public void dismissOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = Instant.now();
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
