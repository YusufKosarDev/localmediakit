package com.localmediakit.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {

    List<MediaItem> findByMediaKitIdOrderByDisplayOrderAscIdAsc(Long mediaKitId);

    /** Owner-scoped: an id from another kit resolves to nothing. */
    Optional<MediaItem> findByIdAndMediaKitId(Long id, Long mediaKitId);

    long countByMediaKitId(Long mediaKitId);
}
