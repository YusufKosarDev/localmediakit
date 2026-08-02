package com.localmediakit.mediakit;

import jakarta.validation.constraints.Size;

/**
 * The copy's title. Optional, and supplied by the client rather than derived
 * here: the suffix a creator expects ("kopya", "copy") depends on the language
 * they are reading, which the dashboard knows and the server does not.
 */
public record DuplicateKitRequest(@Size(max = 255) String title) {
}
