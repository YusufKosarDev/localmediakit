package com.localmediakit.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.localmediakit.shared.ClientIp;
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
    private final long contactCapacity;
    private final long accountCapacity;

    public RateLimitFilter(RateLimiterRegistry registry, boolean enabled,
                           long loginCapacity, long registerCapacity,
                           long trackCapacity, long unlockCapacity,
                           long contactCapacity, long accountCapacity) {
        this.registry = registry;
        this.enabled = enabled;
        this.loginCapacity = loginCapacity;
        this.registerCapacity = registerCapacity;
        this.trackCapacity = trackCapacity;
        this.unlockCapacity = unlockCapacity;
        this.contactCapacity = contactCapacity;
        this.accountCapacity = accountCapacity;
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
        String method = request.getMethod();
        String path = request.getRequestURI();

        // Sensitive account operations. Each one verifies the current
        // password, which makes them a credential oracle if left unthrottled —
        // so they are limited even though the caller is already authenticated.
        // Account deletion is a DELETE, hence the method check comes first.
        if (isAccountOperation(method, path)) {
            return new Rule("account", accountCapacity);
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return null;
        }
        if (path.equals("/api/auth/login")) {
            return new Rule("login", loginCapacity);
        }
        if (path.equals("/api/auth/register")) {
            return new Rule("register", registerCapacity);
        }
        // Both halves of recovery, on the login budget. Requesting is a mail
        // amplifier pointed at somebody else's inbox; confirming is a guess at
        // a token, which is the same shape of attack as guessing a password.
        if (path.startsWith("/api/auth/password-reset/")) {
            return new Rule("login", loginCapacity);
        }
        if (path.equals("/api/track")) {
            return new Rule("track", trackCapacity);
        }
        if (path.startsWith("/api/public/kits/") && path.endsWith("/unlock")) {
            return new Rule("unlock", unlockCapacity);
        }
        if (path.startsWith("/api/public/kits/") && path.endsWith("/contact")) {
            return new Rule("contact", contactCapacity);
        }
        return null;
    }

    /**
     * Password change, email change and account deletion — the three routes
     * that take a password. Profile edits (PUT /api/me) are not included:
     * they verify nothing, so throttling them would only slow honest use.
     */
    private boolean isAccountOperation(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/api/me/password") || path.equals("/api/me/email");
        }
        return "DELETE".equalsIgnoreCase(method) && path.equals("/api/me");
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429); // Too Many Requests (no servlet constant for it)
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too many requests. Please slow down.\"}");
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIp.resolve(request);
    }

    private record Rule(String name, long capacity) {
    }
}
