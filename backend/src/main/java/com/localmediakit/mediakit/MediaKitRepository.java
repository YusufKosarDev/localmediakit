package com.localmediakit.mediakit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaKitRepository extends JpaRepository<MediaKit, Long> {

    /** Owner-scoped fetch: the query itself enforces ownership. */
    Optional<MediaKit> findByIdAndUserId(Long id, Long userId);

    List<MediaKit> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
