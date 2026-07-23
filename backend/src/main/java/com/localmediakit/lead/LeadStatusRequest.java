package com.localmediakit.lead;

import jakarta.validation.constraints.NotNull;

public record LeadStatusRequest(@NotNull LeadStatus status) {
}
