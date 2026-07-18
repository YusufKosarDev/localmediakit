package com.localmediakit.collab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandCollaborationRepository extends JpaRepository<BrandCollaboration, Long> {

    List<BrandCollaboration> findByMediaKitIdOrderByDisplayOrderAscIdAsc(Long mediaKitId);

    /** Kit-scoped fetch: a collaboration of another kit is simply "not found". */
    Optional<BrandCollaboration> findByIdAndMediaKitId(Long id, Long mediaKitId);
}
