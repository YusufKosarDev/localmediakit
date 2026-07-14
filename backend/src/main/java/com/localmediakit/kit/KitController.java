package com.localmediakit.kit;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Management path. For Step 0 there is no auth yet; a single publish endpoint
 * both updates the content and triggers revalidation, so the whole loop can be
 * exercised with one call.
 */
@RestController
@RequestMapping("/api/kits")
public class KitController {

    private final KitService service;

    public KitController(KitService service) {
        this.service = service;
    }

    @PostMapping("/{slug}/publish")
    public Map<String, Object> publish(@PathVariable String slug, @RequestBody PublishRequest request) {
        int revalidateStatus = service.publish(slug, request.content());
        return Map.of(
                "slug", slug,
                "published", true,
                "revalidateStatus", revalidateStatus
        );
    }
}
