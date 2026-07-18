package com.localmediakit.stats;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateDemographicsRequest(@NotNull List<@Valid DemographicEntry> entries) {
}
