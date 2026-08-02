package com.localmediakit.mediakit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaKitRepository extends JpaRepository<MediaKit, Long> {

    /** Owner-scoped fetch: the query itself enforces ownership. */
    Optional<MediaKit> findByIdAndUserId(Long id, Long userId);

    List<MediaKit> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    /** Creation-order position of a kit within its owner's kits (ids are monotonic). */
    long countByUserIdAndIdLessThan(Long userId, Long id);

    /** The scheduler's read: every kit whose moment has arrived, oldest first. */
    List<MediaKit> findByScheduledPublishAtLessThanEqualOrderByScheduledPublishAtAsc(Instant now);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
