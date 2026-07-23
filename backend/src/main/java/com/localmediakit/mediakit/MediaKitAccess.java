package com.localmediakit.mediakit;

import com.localmediakit.user.User;
import com.localmediakit.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ownership guard shared by every media-kit use case: kits are only ever
 * loaded through owner-scoped queries, so another user's kit is
 * indistinguishable from a missing one (404, no existence leak).
 */
@Component
public class MediaKitAccess {

    private final UserRepository userRepository;
    private final MediaKitRepository mediaKitRepository;

    public MediaKitAccess(UserRepository userRepository, MediaKitRepository mediaKitRepository) {
        this.userRepository = userRepository;
        this.mediaKitRepository = mediaKitRepository;
    }

    public User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    public MediaKit requireOwnedKit(String userEmail, Long kitId) {
        User user = requireUser(userEmail);
        return mediaKitRepository.findByIdAndUserId(kitId, user.getId())
                .orElseThrow(MediaKitNotFoundException::new);
    }

    /**
     * Resolves a kit's owner when the caller is authorized by something other
     * than a session (e.g. a signed preview token). A missing owner presents
     * as a missing kit — same no-leak rule as everywhere else.
     */
    public User requireOwner(MediaKit kit) {
        return userRepository.findById(kit.getUserId())
                .orElseThrow(MediaKitNotFoundException::new);
    }
}
