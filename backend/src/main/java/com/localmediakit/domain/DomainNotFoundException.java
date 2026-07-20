package com.localmediakit.domain;

public class DomainNotFoundException extends RuntimeException {
    public DomainNotFoundException() {
        super("Domain not found");
    }
}
