package com.localmediakit.analytics;

import com.localmediakit.shared.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public beacon endpoint. The static edge-cached page fires a non-blocking
 * POST here AFTER it has already rendered; if this backend is asleep the
 * beacon dies silently in the visitor's background (best-effort analytics)
 * and the page is unaffected. Always answers 202.
 */
@RestController
@RequestMapping("/api/track")
public class TrackController {

    private static final Logger log = LoggerFactory.getLogger(TrackController.class);

    private final AnalyticsService analyticsService;

    public TrackController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void track(@Valid @RequestBody TrackRequest request, HttpServletRequest http) {
        try {
            analyticsService.track(request, ClientIp.resolve(http), http.getHeader("User-Agent"));
        } catch (Exception e) {
            // Analytics must never fail the caller; drop and log.
            log.warn("Track ingestion failed for slug '{}': {}", request.slug(), e.getMessage());
        }
    }
}
