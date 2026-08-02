package com.localmediakit.analytics;

/**
 * A ceiling on links per kit. Not a plan limit -- it is a guard against a list
 * nobody can read and a table nobody prunes, which is what an unbounded
 * generator of rows becomes.
 */
public class TooManyShareLinksException extends RuntimeException {
    public TooManyShareLinksException(int max) {
        super("This kit already has the maximum of " + max + " share links. Revoke one to add another.");
    }
}
