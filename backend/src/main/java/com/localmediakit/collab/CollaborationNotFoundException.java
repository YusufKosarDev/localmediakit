package com.localmediakit.collab;

public class CollaborationNotFoundException extends RuntimeException {
    public CollaborationNotFoundException() {
        super("Collaboration not found");
    }
}
