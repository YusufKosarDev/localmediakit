package com.localmediakit.mediakit;

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

import java.util.List;

@RestController
@RequestMapping("/api/mediakits")
public class MediaKitController {

    private final MediaKitService mediaKitService;

    public MediaKitController(MediaKitService mediaKitService) {
        this.mediaKitService = mediaKitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaKitResponse create(Authentication authentication,
                                   @Valid @RequestBody CreateMediaKitRequest request) {
        return mediaKitService.create(currentEmail(authentication), request);
    }

    @GetMapping
    public List<MediaKitResponse> list(Authentication authentication) {
        return mediaKitService.list(currentEmail(authentication));
    }

    @GetMapping("/{id}")
    public MediaKitResponse get(Authentication authentication, @PathVariable Long id) {
        return mediaKitService.get(currentEmail(authentication), id);
    }

    @PutMapping("/{id}")
    public MediaKitResponse update(Authentication authentication,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateMediaKitRequest request) {
        return mediaKitService.update(currentEmail(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        mediaKitService.delete(currentEmail(authentication), id);
    }

    private String currentEmail(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
