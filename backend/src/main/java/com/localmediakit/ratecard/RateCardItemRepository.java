package com.localmediakit.ratecard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RateCardItemRepository extends JpaRepository<RateCardItem, Long> {

    List<RateCardItem> findByMediaKitIdOrderByDisplayOrderAscIdAsc(Long mediaKitId);

    Optional<RateCardItem> findByIdAndMediaKitId(Long id, Long mediaKitId);
}
