package com.localmediakit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomDomainRepository extends JpaRepository<CustomDomain, Long> {

    List<CustomDomain> findByMediaKitIdOrderByCreatedAtDesc(Long mediaKitId);

    Optional<CustomDomain> findByDomain(String domain);

    Optional<CustomDomain> findByIdAndMediaKitId(Long id, Long mediaKitId);

    /**
     * PENDING domains due for a check: never checked, or last checked before the
     * cutoff. Ordered oldest-first so no single domain starves the others.
     */
    @Query("""
            select d from CustomDomain d
            where d.status = com.localmediakit.domain.DomainStatus.PENDING
              and (d.lastCheckedAt is null or d.lastCheckedAt < :cutoff)
            order by d.lastCheckedAt asc nulls first""")
    List<CustomDomain> findPendingDue(@Param("cutoff") Instant cutoff);
}
