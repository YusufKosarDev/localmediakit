package com.localmediakit.lead;

import com.localmediakit.shared.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public contact-form endpoint. Like the view beacon it ALWAYS answers 202 for
 * a well-formed body: whether the slug exists, the kit accepts contact, or the
 * submission was dropped as spam is never revealed to the caller.
 */
@RestController
@RequestMapping("/api/public/kits")
public class PublicLeadController {

    private static final Logger log = LoggerFactory.getLogger(PublicLeadController.class);

    private final LeadService leadService;

    public PublicLeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping("/{slug}/contact")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void contact(@PathVariable String slug,
                        @Valid @RequestBody ContactRequest request,
                        HttpServletRequest http) {
        try {
            leadService.submit(slug, request, ClientIp.resolve(http), http.getHeader("User-Agent"));
        } catch (Exception e) {
            // Ingestion must never fail the caller; drop and log.
            log.warn("Lead ingestion failed for slug '{}': {}", slug, e.getMessage());
        }
    }
}
