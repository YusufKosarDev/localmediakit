package com.localmediakit.stats.sync;

import com.localmediakit.stats.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StatsSourceRepository extends JpaRepository<StatsSource, Long> {

    Optional<StatsSource> findByMediaKitIdAndPlatform(Long mediaKitId, Platform platform);

    List<StatsSource> findByMediaKitIdOrderByPlatformAsc(Long mediaKitId);

    /** Sources due for a scheduled sync: enabled, never synced or synced before the cutoff. */
    @Query("select s.id from StatsSource s where s.enabled = true"
            + " and (s.lastSyncedAt is null or s.lastSyncedAt < :cutoff) order by s.id")
    List<Long> findDueIds(@Param("cutoff") Instant cutoff);
}
