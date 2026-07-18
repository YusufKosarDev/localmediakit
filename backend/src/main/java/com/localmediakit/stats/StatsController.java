package com.localmediakit.stats;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mediakits/{kitId}/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatsResponse record(Authentication authentication,
                                @PathVariable Long kitId,
                                @Valid @RequestBody RecordStatsRequest request) {
        return StatsResponse.from(
                statsService.record((String) authentication.getPrincipal(), kitId, request));
    }

    @GetMapping
    public List<StatsResponse> latest(Authentication authentication, @PathVariable Long kitId) {
        return statsService.latest((String) authentication.getPrincipal(), kitId)
                .stream().map(StatsResponse::from).toList();
    }
}
