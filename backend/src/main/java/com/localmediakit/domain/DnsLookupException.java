package com.localmediakit.domain;

public class DnsLookupException extends RuntimeException {
    public DnsLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
