package com.localmediakit.mediakit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read path, served from the immutable published snapshot only.
 * Public kits return full content (edge-cacheable); protected kits return only
 * gate metadata here and their content via {@code /unlock}.
 */
@RestController
@RequestMapping("/api/public/kits")
public class PublicMediaKitController {

    private final MediaKitPublicationService publicationService;

    public PublicMediaKitController(MediaKitPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicKitResponse> getPublishedKit(@PathVariable String slug) {
        return publicationService.findPublished(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Unlocks a protected kit with its password. Never cached (POST); the
     * sensitive content is served per-request, so it never touches the edge.
     */
    @PostMapping("/{slug}/unlock")
    public PublicKitResponse unlock(@PathVariable String slug,
                                    @Valid @RequestBody UnlockRequest request,
                                    HttpServletRequest http) {
        String clientKey = slug + "|" + clientIp(http);
        return publicationService.unlock(slug, request.password(), clientKey);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
