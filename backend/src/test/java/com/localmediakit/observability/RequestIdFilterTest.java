package com.localmediakit.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inbound id is caller-controlled and ends up written into log lines, so
 * the sanitising is the part of this filter worth pinning down.
 */
class RequestIdFilterTest {

    @Test
    void keepsAnIdThatAlreadyLooksLikeOne() {
        assertThat(RequestIdFilter.resolve("7f3c1a9e-2b40-4d8e-9c11-5a6b7c8d9e0f"))
                .isEqualTo("7f3c1a9e-2b40-4d8e-9c11-5a6b7c8d9e0f");
    }

    @Test
    void generatesOneWhenTheCallerSendsNothing() {
        assertThat(RequestIdFilter.resolve(null)).isNotBlank();
        assertThat(RequestIdFilter.resolve("  ")).isNotBlank();
        assertThat(RequestIdFilter.resolve(null)).isNotEqualTo(RequestIdFilter.resolve(null));
    }

    @Test
    void stripsWhatWouldForgeALogLine() {
        // A newline in a log line lets a caller write their own entries; the
        // brackets and spaces let them imitate this application's format.
        String forged = RequestIdFilter.resolve("abc\n2026-01-01 ERROR [x] fake entry");

        assertThat(forged).doesNotContain("\n").doesNotContain(" ").doesNotContain("[");
        assertThat(forged).startsWith("abc");
    }

    @Test
    void generatesOneWhenNothingSurvivesSanitising() {
        // Not the empty string: an id that is blank makes every line it stamps
        // look like a line with no id at all.
        assertThat(RequestIdFilter.resolve("<<<>>>")).isNotBlank();
    }

    @Test
    void capsTheLength() {
        assertThat(RequestIdFilter.resolve("a".repeat(500))).hasSize(64);
    }
}
