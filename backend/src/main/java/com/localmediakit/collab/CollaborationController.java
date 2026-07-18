package com.localmediakit.collab;

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
@RequestMapping("/api/mediakits/{kitId}/collaborations")
public class CollaborationController {

    private final CollaborationService collaborationService;

    public CollaborationController(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollaborationResponse create(Authentication authentication,
                                        @PathVariable Long kitId,
                                        @Valid @RequestBody CollaborationRequest request) {
        return collaborationService.create(email(authentication), kitId, request);
    }

    @GetMapping
    public List<CollaborationResponse> list(Authentication authentication, @PathVariable Long kitId) {
        return collaborationService.list(email(authentication), kitId);
    }

    @PutMapping("/{collabId}")
    public CollaborationResponse update(Authentication authentication,
                                        @PathVariable Long kitId,
                                        @PathVariable Long collabId,
                                        @Valid @RequestBody CollaborationRequest request) {
        return collaborationService.update(email(authentication), kitId, collabId, request);
    }

    @DeleteMapping("/{collabId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication,
                       @PathVariable Long kitId,
                       @PathVariable Long collabId) {
        collaborationService.delete(email(authentication), kitId, collabId);
    }

    private String email(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
