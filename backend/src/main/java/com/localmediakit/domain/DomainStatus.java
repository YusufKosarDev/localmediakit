package com.localmediakit.domain;

public enum DomainStatus {
    /** Waiting for the DNS TXT record to appear and match the token. */
    PENDING,
    /** TXT record found and matched. */
    VERIFIED,
    /** Gave up after the maximum number of attempts. */
    FAILED
}
