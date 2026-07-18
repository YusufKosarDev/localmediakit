package com.localmediakit.mediakit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read path, served from the immutable published snapshot only.
 * In production this is called by the frontend at (re)generation time,
 * not by end visitors — they get the edge-cached HTML.
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
}
