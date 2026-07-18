package com.localmediakit.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PageViewRepository extends JpaRepository<PageView, Long> {

    long countByMediaKitId(Long mediaKitId);

    /** Session-window dedup: has this visitor already been counted recently? */
    boolean existsByMediaKitIdAndVisitorHashAndViewedAtAfter(
            Long mediaKitId, String visitorHash, Instant after);

    @Query("select count(distinct v.visitorHash) from PageView v where v.mediaKitId = :kitId")
    long countUniqueVisitors(@Param("kitId") Long kitId);

    /** Daily series: [date, views, unique visitors]. CAST(.. AS DATE) works on H2 and Postgres. */
    @Query(value = """
            select cast(viewed_at as date) as view_day,
                   count(*) as views,
                   count(distinct visitor_hash) as uniques
            from page_views
            where media_kit_id = :kitId and viewed_at >= :since
            group by cast(viewed_at as date)
            order by view_day""", nativeQuery = true)
    List<Object[]> dailyCounts(@Param("kitId") Long kitId, @Param("since") Instant since);

    /** [referrer host or '', count] - top sources. */
    @Query(value = """
            select coalesce(referrer, ''), count(*)
            from page_views
            where media_kit_id = :kitId
            group by coalesce(referrer, '')
            order by count(*) desc
            limit 10""", nativeQuery = true)
    List<Object[]> topReferrers(@Param("kitId") Long kitId);

    /** [device, count]. */
    @Query(value = """
            select coalesce(device, 'UNKNOWN'), count(*)
            from page_views
            where media_kit_id = :kitId
            group by coalesce(device, 'UNKNOWN')""", nativeQuery = true)
    List<Object[]> deviceBreakdown(@Param("kitId") Long kitId);
}
