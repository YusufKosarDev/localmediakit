package com.localmediakit.kit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read path. In production this is only ever called by the frontend at
 * (re)generation time, not by end visitors.
 */
@RestController
@RequestMapping("/api/public/kits")
public class PublicKitController {

    private final KitService service;

    public PublicKitController(KitService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<KitResponse> getKit(@PathVariable String slug) {
        return service.findBySlug(slug)
                .map(kit -> ResponseEntity.ok(new KitResponse(
                        kit.getSlug(),
                        kit.getContent(),
                        kit.getUpdatedAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }
}
