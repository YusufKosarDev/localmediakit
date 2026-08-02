package com.localmediakit.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

    @Test
    void anIdExactlyAtTheLimitIsKeptWhole() {
        // The boundary itself: truncating at the limit rather than past it would
        // quietly rewrite ids that were already the right size.
        String atTheLimit = "b".repeat(64);

        assertThat(RequestIdFilter.resolve(atTheLimit)).isEqualTo(atTheLimit);
    }

    /* --- the filter body, not just the rule --- */

    @Test
    void theIdReachesTheResponseAndTheRequestReachesTheApplication() throws Exception {
        // Both halves matter and neither was covered here: an id that never
        // makes it onto the response cannot be quoted in a bug report, and a
        // filter that forgets to continue the chain would silently answer every
        // request with an empty 200.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void theLoggingContextIsClearedSoAPooledThreadDoesNotInheritIt() throws Exception {
        // Threads are reused. An id left behind would stamp the previous
        // request's identity onto whatever this thread serves next, which is
        // worse than having no id at all: the log would be confidently wrong.
        new RequestIdFilter().doFilter(
                new MockHttpServletRequest("GET", "/api/me"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
