package com.localmediakit.mediakit;

import com.localmediakit.user.PlanPolicy;
import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MediaKitService {

    private final MediaKitRepository mediaKitRepository;
    private final UserRepository userRepository;
    private final SlugService slugService;
    private final PlanPolicy planPolicy;

    public MediaKitService(MediaKitRepository mediaKitRepository,
                           UserRepository userRepository,
                           SlugService slugService,
                           PlanPolicy planPolicy) {
        this.mediaKitRepository = mediaKitRepository;
        this.userRepository = userRepository;
        this.slugService = slugService;
        this.planPolicy = planPolicy;
    }

    @Transactional
    public MediaKitResponse create(String userEmail, CreateMediaKitRequest request) {
        User user = requireUser(userEmail);
        planPolicy.assertCanCreateMediaKit(user.getPlan(), mediaKitRepository.countByUserId(user.getId()));

        String slug = resolveSlug(request.slug(), request.title(), null);
        MediaKit kit = new MediaKit(
                user.getId(), slug, request.title().trim(),
                request.headline(), request.avatarUrl(), request.theme());
        mediaKitRepository.save(kit);
        return MediaKitResponse.from(kit);
    }

    @Transactional(readOnly = true)
    public List<MediaKitResponse> list(String userEmail) {
        User user = requireUser(userEmail);
        return mediaKitRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(MediaKitResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MediaKitResponse get(String userEmail, Long id) {
        return MediaKitResponse.from(requireOwnedKit(userEmail, id));
    }

    @Transactional
    public MediaKitResponse update(String userEmail, Long id, UpdateMediaKitRequest request) {
        MediaKit kit = requireOwnedKit(userEmail, id);
        kit.updateDetails(request.title().trim(), request.headline(), request.avatarUrl(), request.theme());

        if (request.slug() != null && !request.slug().isBlank()) {
            String desired = slugService.slugify(request.slug());
            if (!desired.equals(kit.getSlug())) {
                if (slugService.isReserved(desired)) {
                    throw new ReservedSlugException(desired);
                }
                String unique = slugService.makeUnique(
                        desired, candidate -> mediaKitRepository.existsBySlugAndIdNot(candidate, id));
                kit.changeSlug(unique);
            }
        }
        return MediaKitResponse.from(kit);
    }

    @Transactional
    public void delete(String userEmail, Long id) {
        MediaKit kit = requireOwnedKit(userEmail, id);
        mediaKitRepository.delete(kit);
    }

    /**
     * Resolves a slug from an explicit value (if provided) or the title.
     * An explicitly provided reserved slug is rejected; collisions get a suffix.
     */
    private String resolveSlug(String explicitSlug, String title, Long excludeId) {
        boolean explicit = explicitSlug != null && !explicitSlug.isBlank();
        String base = slugService.slugify(explicit ? explicitSlug : title);
        if (explicit && slugService.isReserved(base)) {
            throw new ReservedSlugException(base);
        }
        return slugService.makeUnique(base, candidate -> excludeId == null
                ? mediaKitRepository.existsBySlug(candidate)
                : mediaKitRepository.existsBySlugAndIdNot(candidate, excludeId));
    }

    private MediaKit requireOwnedKit(String userEmail, Long id) {
        User user = requireUser(userEmail);
        // Ownership is enforced by the query: another user's kit is simply "not found".
        return mediaKitRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(MediaKitNotFoundException::new);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
