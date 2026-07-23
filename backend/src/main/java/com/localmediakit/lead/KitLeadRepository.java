package com.localmediakit.lead;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KitLeadRepository extends JpaRepository<KitLead, Long> {

    List<KitLead> findByMediaKitIdOrderByCreatedAtDescIdDesc(Long mediaKitId);

    Optional<KitLead> findByIdAndMediaKitId(Long id, Long mediaKitId);

    /** Per-visitor submission cap within the anti-spam window. */
    long countByMediaKitIdAndVisitorHashAndCreatedAtAfter(Long mediaKitId, String visitorHash, Instant after);
}
