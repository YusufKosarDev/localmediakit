package com.localmediakit.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PageViewDailyRepository extends JpaRepository<PageViewDaily, Long> {

    Optional<PageViewDaily> findByMediaKitIdAndViewDay(Long mediaKitId, LocalDate viewDay);

    /**
     * The pruned half of a kit's lifetime totals; the live half comes from the
     * raw rows still inside the retention window. Coalesced because a kit whose
     * views have never been rolled up has no rows here, and a null total would
     * turn an honest zero into a NullPointerException.
     */
    @Query("select coalesce(sum(d.views), 0) from PageViewDaily d where d.mediaKitId = :kitId")
    long sumViews(@Param("kitId") Long kitId);

    @Query("select coalesce(sum(d.uniqueVisitors), 0) from PageViewDaily d where d.mediaKitId = :kitId")
    long sumUniqueVisitors(@Param("kitId") Long kitId);
}
