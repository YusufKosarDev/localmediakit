package com.localmediakit.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AudienceDemographicRepository extends JpaRepository<AudienceDemographic, Long> {

    List<AudienceDemographic> findByMediaKitIdOrderByCategoryAscPercentageDesc(Long mediaKitId);

    void deleteByMediaKitId(Long mediaKitId);
}
