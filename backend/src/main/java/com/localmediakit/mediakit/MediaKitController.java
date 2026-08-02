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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mediakits")
public class MediaKitController {

    private final MediaKitService mediaKitService;
    private final MediaKitPublicationService publicationService;
    private final KitPreviewService previewService;
    private final KitDuplicationService duplicationService;
    private final ScheduledPublishService scheduledPublishService;
    private final VersionDiffService versionDiffService;

    public MediaKitController(MediaKitService mediaKitService,
                              MediaKitPublicationService publicationService,
                              KitPreviewService previewService,
                              KitDuplicationService duplicationService,
                              ScheduledPublishService scheduledPublishService,
                              VersionDiffService versionDiffService) {
        this.mediaKitService = mediaKitService;
        this.publicationService = publicationService;
        this.previewService = previewService;
        this.duplicationService = duplicationService;
        this.scheduledPublishService = scheduledPublishService;
        this.versionDiffService = versionDiffService;
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

    /**
     * A draft copy. 201 like any other creation, because that is what it is --
     * the response is the new kit, not the source.
     */
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaKitResponse duplicate(Authentication authentication,
                                      @PathVariable Long id,
                                      @Valid @RequestBody(required = false) DuplicateKitRequest request) {
        return duplicationService.duplicate(currentEmail(authentication), id,
                request == null ? null : request.title());
    }

    @PostMapping("/{id}/publish")
    public PublishResponse publish(Authentication authentication, @PathVariable Long id) {
        return publicationService.publish(currentEmail(authentication), id);
    }

    /**
     * Arms a publish for later. PUT because a kit has at most one pending
     * schedule and setting it again replaces it -- there is no queue to append
     * to, which is also why the state lives on the kit.
     */
    @PutMapping("/{id}/schedule")
    public MediaKitResponse schedule(Authentication authentication,
                                     @PathVariable Long id,
                                     @Valid @RequestBody ScheduleRequest request) {
        return scheduledPublishService.schedule(currentEmail(authentication), id, request.publishAt());
    }

    @DeleteMapping("/{id}/schedule")
    public MediaKitResponse cancelSchedule(Authentication authentication, @PathVariable Long id) {
        return scheduledPublishService.cancel(currentEmail(authentication), id);
    }

    @PostMapping("/{id}/preview-link")
    public PreviewLinkResponse previewLink(Authentication authentication, @PathVariable Long id) {
        return previewService.createLink(currentEmail(authentication), id);
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(Authentication authentication, @PathVariable Long id) {
        return publicationService.listVersions(currentEmail(authentication), id);
    }

    @GetMapping("/{id}/versions/diff")
    public VersionDiffResponse diffVersions(Authentication authentication,
                                            @PathVariable Long id,
                                            @RequestParam int from,
                                            @RequestParam int to) {
        return versionDiffService.diff(currentEmail(authentication), id, from, to);
    }

    @PostMapping("/{id}/versions/{versionNumber}/activate")
    public PublishResponse activateVersion(Authentication authentication,
                                           @PathVariable Long id,
                                           @PathVariable int versionNumber) {
        return publicationService.activateVersion(currentEmail(authentication), id, versionNumber);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(Authentication authentication,
                            @PathVariable Long id,
                            @Valid @RequestBody SetPasswordRequest request) {
        mediaKitService.setPassword(currentEmail(authentication), id, request.password());
    }

    @DeleteMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePassword(Authentication authentication, @PathVariable Long id) {
        mediaKitService.removePassword(currentEmail(authentication), id);
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
