package com.localmediakit.analytics;

import java.util.Locale;

/**
 * Coarse user-agent classification; we never store the raw string.
 * Public because every anonymous public-write path (view beacon, contact form)
 * shares the same bot gate.
 */
public final class UserAgents {

    private UserAgents() {
    }

    public static boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true; // no UA at all: almost certainly not a real browser
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("preview") || ua.contains("curl") || ua.contains("wget")
                || ua.contains("python-requests") || ua.contains("headless");
    }

    static String device(String userAgent) {
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone"))
                ? "MOBILE" : "DESKTOP";
    }
}
