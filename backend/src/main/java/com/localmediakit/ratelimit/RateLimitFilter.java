package com.localmediakit.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP-based token-bucket throttle for the endpoints most exposed to abuse:
 * auth (credential brute-force), the public view beacon, and password unlock.
 * Runs before Spring Security so brute-force is throttled before any auth work.
 * A rejected request gets 429 with a small JSON body.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterRegistry registry;
    private final boolean enabled;
    private final long loginCapacity;
    private final long registerCapacity;
    private final long trackCapacity;
    private final long unlockCapacity;

    public RateLimitFilter(RateLimiterRegistry registry, boolean enabled,
                           long loginCapacity, long registerCapacity,
                           long trackCapacity, long unlockCapacity) {
        this.registry = registry;
        this.enabled = enabled;
        this.loginCapacity = loginCapacity;
        this.registerCapacity = registerCapacity;
        this.trackCapacity = trackCapacity;
        this.unlockCapacity = unlockCapacity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Rule rule = enabled ? ruleFor(request) : null;
        if (rule != null) {
            String key = rule.name + "|" + clientIp(request);
            if (!registry.tryConsume(key, rule.capacity)) {
                reject(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        if (path.equals("/api/auth/login")) {
            return new Rule("login", loginCapacity);
        }
        if (path.equals("/api/auth/register")) {
            return new Rule("register", registerCapacity);
        }
        if (path.equals("/api/track")) {
            return new Rule("track", trackCapacity);
        }
        if (path.startsWith("/api/public/kits/") && path.endsWith("/unlock")) {
            return new Rule("unlock", unlockCapacity);
        }
        return null;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429); // Too Many Requests (no servlet constant for it)
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too many requests. Please slow down.\"}");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Rule(String name, long capacity) {
    }
}
