package com.localmediakit.lead;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Owner-facing lead inbox. */
@RestController
@RequestMapping("/api/mediakits/{kitId}/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public List<LeadResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return leadService.list(email(authentication), kitId);
    }

    @PutMapping("/{leadId}/status")
    public LeadResponse changeStatus(Authentication authentication,
                                     @PathVariable Long kitId,
                                     @PathVariable Long leadId,
                                     @Valid @RequestBody LeadStatusRequest request) {
        return leadService.changeStatus(email(authentication), kitId, leadId, request.status());
    }

    @DeleteMapping("/{leadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long leadId) {
        leadService.delete(email(authentication), kitId, leadId);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
