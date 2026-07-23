package com.localmediakit.mediakit;

import com.localmediakit.security.JwtService;
import com.localmediakit.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Draft preview: the owner mints a short-lived signed link; anyone holding it
 * sees the CURRENT DRAFT rendered exactly like the public page would render it.
 * The deliberate inverse of the publish path — published pages are frozen
 * snapshots served from the edge cache, previews are live drafts served
 * per-request with no caching anywhere.
 */
@Service
public class KitPreviewService {

    private final MediaKitAccess access;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitPublicationService publicationService;
    private final JwtService jwtService;
    private final long ttlMinutes;

    public KitPreviewService(MediaKitAccess access,
                             MediaKitRepository mediaKitRepository,
                             MediaKitPublicationService publicationService,
                             JwtService jwtService,
                             @Value("${app.preview.ttl-minutes:30}") long ttlMinutes) {
        this.access = access;
        this.mediaKitRepository = mediaKitRepository;
        this.publicationService = publicationService;
        this.jwtService = jwtService;
        this.ttlMinutes = ttlMinutes;
    }

    /** Owner-only: mints the signed link token. Stateless — nothing is stored. */
    public PreviewLinkResponse createLink(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        String token = jwtService.generatePreviewToken(kit.getId(), ttlMinutes);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        return new PreviewLinkResponse(token, expiresAt.toString());
    }

    /**
     * Renders the draft for a valid preview token. Every failure mode (bad
     * signature, expired, wrong token type, deleted kit) collapses into the
     * same 404 — a preview URL leaks nothing about what exists.
     *
     * The password gate is intentionally skipped: the token itself is the
     * authorization, and it only ever comes from the owner.
     */
    @Transactional(readOnly = true)
    public PublicKitResponse renderDraft(String token) {
        Long kitId;
        try {
            kitId = jwtService.extractPreviewKitId(token);
        } catch (Exception e) {
            throw new MediaKitNotFoundException();
        }
        MediaKit kit = mediaKitRepository.findById(kitId)
                .orElseThrow(MediaKitNotFoundException::new);
        User owner = access.requireOwner(kit);
        return PublicKitResponse.preview(publicationService.buildSnapshot(kit, owner));
    }
}
