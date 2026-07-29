package com.localmediakit.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlatformStatsRepository extends JpaRepository<PlatformStats, Long> {

    @Query("select distinct s.platform from PlatformStats s where s.mediaKitId = :kitId")
    List<Platform> platformsWithData(@Param("kitId") Long kitId);

    /** Whether any of these kits has a measurement — one query for the whole set. */
    boolean existsByMediaKitIdIn(List<Long> mediaKitIds);

    Optional<PlatformStats> findFirstByMediaKitIdAndPlatformOrderByRecordedAtDescIdDesc(
            Long mediaKitId, Platform platform);

    /** Trend baseline: the most recent measurement at or before the cutoff. */
    Optional<PlatformStats> findFirstByMediaKitIdAndPlatformAndRecordedAtLessThanEqualOrderByRecordedAtDescIdDesc(
            Long mediaKitId, Platform platform, Instant cutoff);

    /** Fallback baseline when the whole series is younger than the cutoff. */
    Optional<PlatformStats> findFirstByMediaKitIdAndPlatformOrderByRecordedAtAscIdAsc(
            Long mediaKitId, Platform platform);
}
