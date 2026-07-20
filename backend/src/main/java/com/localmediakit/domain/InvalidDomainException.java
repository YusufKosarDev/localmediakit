package com.localmediakit.domain;

public class InvalidDomainException extends RuntimeException {
    public InvalidDomainException(String message) {
        super(message);
    }
}
