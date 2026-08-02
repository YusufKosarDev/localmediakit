package com.localmediakit.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id, puts it in the logging context, and returns it to
 * the caller.
 *
 * <p>Without one, a production log is a flat stream of lines from concurrent
 * requests and there is no way to ask "what else happened while handling the
 * one that failed" — which is the first question anybody asks. The id is in the
 * log pattern (see logback-spring.xml), so every line a request produces
 * carries it, including the ones from the scheduled jobs' own transactions.
 *
 * <p>It is echoed as {@code X-Request-Id} so a user reporting a problem can
 * quote something that finds the exact request, and an inbound value is
 * honoured so a chain of services shares one id. Inbound ids are length-capped
 * and stripped of anything but the characters an id may contain: this value is
 * written into log lines, and an unbounded caller-controlled string in a log is
 * how log forging works.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    /** Long enough to stay unique across services, short enough to read. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled: leaving the id behind would stamp it onto
            // whatever request is served next by this thread.
            MDC.remove(MDC_KEY);
        }
    }

    /** Package-private for the test: the sanitising is the part worth pinning. */
    static String resolve(String inbound) {
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = inbound.trim().replaceAll("[^A-Za-z0-9_.-]", "");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
