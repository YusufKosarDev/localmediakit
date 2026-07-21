package com.localmediakit.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP in a spoofing-resistant way. This deployment runs
 * behind Cloudflare (Render's edge), which sets {@code CF-Connecting-IP} to the
 * true client address and overwrites any client-supplied value — so a client
 * cannot forge it. We therefore trust that header first.
 *
 * Only when it is absent (local dev, other hosting) do we fall back to
 * X-Forwarded-For, and then to the RIGHTMOST hop — the one appended by the
 * closest proxy — rather than the leftmost, which is the client-controlled
 * (spoofable) end of the chain. Socket address is the last resort.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
