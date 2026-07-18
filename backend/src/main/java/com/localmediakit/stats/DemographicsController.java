package com.localmediakit.stats;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mediakits/{kitId}/demographics")
public class DemographicsController {

    private final DemographicsService demographicsService;

    public DemographicsController(DemographicsService demographicsService) {
        this.demographicsService = demographicsService;
    }

    @GetMapping
    public List<DemographicEntry> list(Authentication authentication, @PathVariable Long kitId) {
        return demographicsService.list((String) authentication.getPrincipal(), kitId);
    }

    @PutMapping
    public List<DemographicEntry> replace(Authentication authentication,
                                          @PathVariable Long kitId,
                                          @Valid @RequestBody UpdateDemographicsRequest request) {
        return demographicsService.replace((String) authentication.getPrincipal(), kitId, request);
    }
}
