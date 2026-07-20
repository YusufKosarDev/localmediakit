package com.localmediakit.domain;

/**
 * Domain status plus the DNS record the owner must create. dnsRecordHost/Type/
 * Value are exactly what to paste into a DNS provider.
 */
public record DomainResponse(
        Long id,
        String domain,
        String status,
        int attempts,
        String lastCheckedAt,
        String dnsRecordType,
        String dnsRecordHost,
        String dnsRecordValue) {

    public static DomainResponse from(CustomDomain d, String verifyHostPrefix) {
        return new DomainResponse(
                d.getId(),
                d.getDomain(),
                d.getStatus().name(),
                d.getAttempts(),
                d.getLastCheckedAt() == null ? null : d.getLastCheckedAt().toString(),
                "TXT",
                verifyHostPrefix + "." + d.getDomain(),
                d.getVerificationToken());
    }
}
