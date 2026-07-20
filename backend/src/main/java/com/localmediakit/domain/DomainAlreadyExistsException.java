package com.localmediakit.domain;

public class DomainAlreadyExistsException extends RuntimeException {
    public DomainAlreadyExistsException() {
        super("This domain is already registered.");
    }
}
