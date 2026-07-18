package com.localmediakit.stats;

import com.localmediakit.mediakit.MediaKit;
import com.localmediakit.mediakit.MediaKitAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DemographicsService {

    private static final BigDecimal MAX_CATEGORY_TOTAL = new BigDecimal("100.01");

    private final AudienceDemographicRepository repository;
    private final MediaKitAccess access;

    public DemographicsService(AudienceDemographicRepository repository, MediaKitAccess access) {
        this.repository = repository;
        this.access = access;
    }

    /** Replaces the kit's whole distribution (a distribution is not a series). */
    @Transactional
    public List<DemographicEntry> replace(String userEmail, Long kitId, UpdateDemographicsRequest request) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        validate(request.entries());
        repository.deleteByMediaKitId(kit.getId());
        repository.saveAll(request.entries().stream()
                .map(e -> new AudienceDemographic(kit.getId(), e.category(), e.label().trim(), e.percentage()))
                .toList());
        return listForKit(kit.getId());
    }

    @Transactional(readOnly = true)
    public List<DemographicEntry> list(String userEmail, Long kitId) {
        MediaKit kit = access.requireOwnedKit(userEmail, kitId);
        return listForKit(kit.getId());
    }

    /** Also used by the publish flow to freeze demographics into the snapshot. */
    @Transactional(readOnly = true)
    public List<DemographicEntry> listForKit(Long kitId) {
        return repository.findByMediaKitIdOrderByCategoryAscPercentageDesc(kitId)
                .stream().map(DemographicEntry::from).toList();
    }

    private void validate(List<DemographicEntry> entries) {
        Set<String> seen = new HashSet<>();
        for (DemographicEntry entry : entries) {
            if (!seen.add(entry.category() + "|" + entry.label().trim().toLowerCase())) {
                throw new InvalidDemographicsException(
                        "Duplicate entry: " + entry.category() + " / " + entry.label());
            }
        }
        Map<DemographicCategory, BigDecimal> totals = entries.stream().collect(Collectors.groupingBy(
                DemographicEntry::category,
                Collectors.reducing(BigDecimal.ZERO, DemographicEntry::percentage, BigDecimal::add)));
        totals.forEach((category, total) -> {
            if (total.compareTo(MAX_CATEGORY_TOTAL) > 0) {
                throw new InvalidDemographicsException(
                        "Percentages for " + category + " exceed 100 (total " + total + ")");
            }
        });
    }
}
