package com.localmediakit.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KitShareLinkRepository extends JpaRepository<KitShareLink, Long> {

    /** The beacon's lookup: it carries the token and nothing else. */
    Optional<KitShareLink> findByToken(String token);

    List<KitShareLink> findByMediaKitIdOrderByCreatedAtDesc(Long mediaKitId);

    /** Owner-scoped read, so a link id from another kit resolves to nothing. */
    Optional<KitShareLink> findByIdAndMediaKitId(Long id, Long mediaKitId);

    long countByMediaKitId(Long mediaKitId);
}
