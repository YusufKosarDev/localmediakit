package com.localmediakit.lead;

import com.localmediakit.analytics.UserAgents;
import com.localmediakit.analytics.VisitorFingerprint;
import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import com.localmediakit.mediakit.MediaKitRepository;
import com.localmediakit.mediakit.MediaKitVersionRepository;
import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class LeadService {

    /** Anti-spam: one visitor may leave at most {@link #MAX_PER_WINDOW} requests
     *  per kit within this window (mirrors the view beacon's session window). */
    static final Duration SUBMIT_WINDOW = Duration.ofMinutes(30);
    static final int MAX_PER_WINDOW = 3;

    private final KitLeadRepository leadRepository;
    private final MediaKitRepository mediaKitRepository;
    private final MediaKitVersionRepository versionRepository;
    private final MediaKitAccess access;
    private final VisitorFingerprint fingerprint;
    private final PlanPolicy planPolicy;

    public LeadService(KitLeadRepository leadRepository,
                       MediaKitRepository mediaKitRepository,
                       MediaKitVersionRepository versionRepository,
                       MediaKitAccess access,
                       VisitorFingerprint fingerprint,
                       PlanPolicy planPolicy) {
        this.leadRepository = leadRepository;
        this.mediaKitRepository = mediaKitRepository;
        this.versionRepository = versionRepository;
        this.access = access;
        this.fingerprint = fingerprint;
        this.planPolicy = planPolicy;
    }

    /**
     * Best-effort ingestion, same philosophy as the view beacon: bots, honeypot
     * hits, unknown slugs, disabled kits and over-cap visitors are all dropped
     * without telling the caller which case it was (the endpoint always 202s).
     */
    @Transactional
    public void submit(String slug, ContactRequest request, String ip, String userAgent) {
        if (request.website() != null && !request.website().isBlank()) {
            return; // honeypot: a real browser never fills the hidden field
        }
        if (UserAgents.isBot(userAgent)) {
            return;
        }
        // Only ACTIVE published pages are visitable, so resolve through them.
        Long kitId = versionRepository.findActiveBySlug(slug)
                .map(version -> version.getMediaKitId())
                .orElse(null);
        if (kitId == null) {
            return;
        }
        // Kill switch reads the DRAFT flag: disabling stops ingestion instantly,
        // even while the frozen public page still shows the form until republish.
        MediaKit kit = mediaKitRepository.findById(kitId).orElse(null);
        if (kit == null || !kit.isContactEnabled()) {
            return;
        }
        String visitor = fingerprint.of(ip, userAgent);
        long recent = leadRepository.countByMediaKitIdAndVisitorHashAndCreatedAtAfter(
                kitId, visitor, Instant.now().minus(SUBMIT_WINDOW));
        if (recent >= MAX_PER_WINDOW) {
            return;
        }
        leadRepository.save(new KitLead(
                kitId, request.brandName().trim(), request.email().trim(),
                request.message().trim(), visitor));
    }

    /** Owner inbox; FREE sees only the most recent leads (PRO teaser), PRO all. */
    @Transactional(readOnly = true)
    public List<LeadResponse> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        User owner = access.requireUser(userEmail);
        return leadRepository.findByMediaKitIdOrderByCreatedAtDescIdDesc(kit.getId())
                .stream()
                .limit(planPolicy.maxVisibleLeads(owner.getPlan()))
                .map(LeadResponse::from)
                .toList();
    }

    @Transactional
    public LeadResponse changeStatus(String userEmail, Long kitId, Long leadId, LeadStatus status) {
        KitLead lead = requireOwnedLead(userEmail, kitId, leadId);
        lead.changeStatus(status);
        return LeadResponse.from(lead);
    }

    @Transactional
    public void delete(String userEmail, Long kitId, Long leadId) {
        leadRepository.delete(requireOwnedLead(userEmail, kitId, leadId));
    }

    /** Two-level guard: the kit must be the caller's, the lead must be the kit's. */
    private KitLead requireOwnedLead(String userEmail, Long kitId, Long leadId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return leadRepository.findByIdAndMediaKitId(leadId, kit.getId())
                .orElseThrow(LeadNotFoundException::new);
    }
}
