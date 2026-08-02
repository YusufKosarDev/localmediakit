package com.localmediakit.analytics;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner-only. Nested under the kit and resolved through the same ownership
 * guard as everything else, so a link id from someone else's kit is a 404
 * rather than a leak.
 */
@RestController
@RequestMapping("/api/mediakits/{kitId}/share-links")
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @GetMapping
    public List<ShareLinkResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return shareLinkService.list((String) authentication.getPrincipal(), kitId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse create(Authentication authentication,
                                    @PathVariable Long kitId,
                                    @Valid @RequestBody CreateShareLinkRequest request) {
        return shareLinkService.create((String) authentication.getPrincipal(), kitId, request.label());
    }

    /**
     * Revokes rather than deletes: the views already attributed to this link
     * are history, and a DELETE that quietly reassigned them would make the
     * record worse than keeping it.
     */
    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long linkId) {
        shareLinkService.revoke((String) authentication.getPrincipal(), kitId, linkId);
    }
}
