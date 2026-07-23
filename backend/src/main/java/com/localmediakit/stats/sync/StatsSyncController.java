package com.localmediakit.stats.sync;

import com.localmediakit.stats.Platform;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing management of external stat sources. */
@RestController
@RequestMapping("/api/mediakits/{kitId}/sources")
public class StatsSyncController {

    private final StatsSyncService syncService;

    public StatsSyncController(StatsSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping
    public SyncStatusResponse status(Authentication authentication, @PathVariable Long kitId) {
        return syncService.status(email(authentication), kitId);
    }

    @PutMapping("/{platform}")
    public SyncSourceResponse connect(Authentication authentication,
                                      @PathVariable Long kitId,
                                      @PathVariable Platform platform,
                                      @Valid @RequestBody ConnectSourceRequest request) {
        return syncService.connect(email(authentication), kitId, platform, request.externalId());
    }

    @DeleteMapping("/{platform}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication,
                           @PathVariable Long kitId,
                           @PathVariable Platform platform) {
        syncService.disconnect(email(authentication), kitId, platform);
    }

    @PostMapping("/{platform}/sync")
    public SyncSourceResponse syncNow(Authentication authentication,
                                      @PathVariable Long kitId,
                                      @PathVariable Platform platform) {
        return syncService.syncNow(email(authentication), kitId, platform);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
