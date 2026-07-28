package com.localmediakit.user;

/**
 * Dashboard appearance preference.
 *
 * <p>Scoped to the signed-in app surfaces only. The public media-kit page
 * stamps its own per-kit theme, which always wins there — a visitor's view of
 * a kit must not depend on who happens to be logged in.
 */
public enum Theme {
    LIGHT,
    DARK
}
