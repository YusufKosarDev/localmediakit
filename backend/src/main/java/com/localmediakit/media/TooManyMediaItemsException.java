package com.localmediakit.media;

/**
 * A ceiling on showcase items. Not a plan limit -- a page that scrolls past a
 * dozen pieces of work stops being a highlight reel, which is the only thing
 * this section is useful as.
 */
public class TooManyMediaItemsException extends RuntimeException {
    public TooManyMediaItemsException(int max) {
        super("A kit can showcase at most " + max + " pieces. Remove one to add another.");
    }
}
