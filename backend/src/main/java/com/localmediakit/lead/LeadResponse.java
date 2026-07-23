package com.localmediakit.lead;

public record LeadResponse(
        Long id,
        String brandName,
        String email,
        String message,
        String status,
        String createdAt) {

    public static LeadResponse from(KitLead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getBrandName(),
                lead.getEmail(),
                lead.getMessage(),
                lead.getStatus().name(),
                lead.getCreatedAt().toString());
    }
}
