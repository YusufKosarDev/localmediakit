package com.localmediakit.mediakit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read path for draft previews. Unauthenticated by design: the signed
 * short-lived token in the URL is the authorization. Served per-request and
 * never cached — the exact opposite of the published snapshot path.
 */
@RestController
@RequestMapping("/api/public/preview")
public class PublicPreviewController {

    private final KitPreviewService previewService;

    public PublicPreviewController(KitPreviewService previewService) {
        this.previewService = previewService;
    }

    @GetMapping("/{token}")
    public PublicKitResponse preview(@PathVariable String token) {
        return previewService.renderDraft(token);
    }
}
