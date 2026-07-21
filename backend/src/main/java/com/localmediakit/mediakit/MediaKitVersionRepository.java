package com.localmediakit.mediakit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaKitVersionRepository extends JpaRepository<MediaKitVersion, Long> {

    List<MediaKitVersion> findByMediaKitIdOrderByVersionNumberDesc(Long mediaKitId);

    /** Batch fetch by id — used to resolve many kits' published slugs in one query. */
    List<MediaKitVersion> findByIdIn(List<Long> ids);

    Optional<MediaKitVersion> findTopByMediaKitIdOrderByVersionNumberDesc(Long mediaKitId);

    Optional<MediaKitVersion> findByMediaKitIdAndVersionNumber(Long mediaKitId, int versionNumber);

    /** How many versions are newer than a given one — its rank in the history. */
    long countByMediaKitIdAndVersionNumberGreaterThan(Long mediaKitId, int versionNumber);

    /** The snapshot currently live at {@code slug}: the one some kit's published_version_id points at. */
    @Query("""
            select v from MediaKitVersion v, MediaKit k
            where k.publishedVersionId = v.id and v.slug = :slug""")
    Optional<MediaKitVersion> findActiveBySlug(@Param("slug") String slug);

    /**
     * True if another kit's ACTIVE snapshot occupies {@code slug}. Draft slugs are
     * covered by the unique column on media_kits; this guards the published URLs,
     * which may differ from drafts (slug renamed after publish).
     */
    @Query("""
            select count(v) > 0 from MediaKitVersion v, MediaKit k
            where k.publishedVersionId = v.id and v.slug = :slug and k.id <> :excludeKitId""")
    boolean activeSlugTakenByOtherKit(@Param("slug") String slug, @Param("excludeKitId") Long excludeKitId);
}
