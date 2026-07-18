package com.localmediakit.collab;

public record CollaborationResponse(
        Long id,
        String brandName,
        String campaign,
        String period,
        String resultNote,
        String logoUrl,
        int displayOrder) {

    public static CollaborationResponse from(BrandCollaboration collab) {
        return new CollaborationResponse(
                collab.getId(),
                collab.getBrandName(),
                collab.getCampaign(),
                collab.getPeriod(),
                collab.getResultNote(),
                collab.getLogoUrl(),
                collab.getDisplayOrder());
    }
}
