package com.localmediakit.user;

/** The signed-in user's own account. Never returned for anyone else. */
public record MeResponse(Long id, String email, String displayName,
                         String avatarUrl, String theme, String plan,
                         boolean leadNotificationsEnabled) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getAvatarUrl(), user.getTheme().name(), user.getPlan().name(),
                user.isLeadNotificationsEnabled());
    }
}
