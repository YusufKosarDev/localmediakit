package com.localmediakit.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which requests the throttle applies to.
 *
 * <p>The existing integration test proves the bucket works, using the view
 * beacon. What it does not check is <em>routing</em> — which of the six rules
 * a given request falls under. That matters more than the counting: a rule
 * that silently stops matching leaves an endpoint unthrottled, and the ones
 * here guard credential brute-force and a password-verifying account API.
 *
 * <p>Plain unit test on the filter, no Spring context: routing is a pure
 * function of method and path.
 */
class RateLimitRoutingTest {

    /** Capacity 1 makes "was this throttled at all" observable in one repeat. */
    private RateLimitFilter filter() {
        return new RateLimitFilter(new RateLimiterRegistry(), true, 1, 1, 1, 1, 1, 1);
    }

    private int statusAfterTwoRequests(String method, String path, String clientIp) throws Exception {
        RateLimitFilter filter = filter();
        MockHttpServletResponse second = new MockHttpServletResponse();
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            request.setRequestURI(path);
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = i == 0 ? new MockHttpServletResponse() : second;
            FilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
        }
        return second.getStatus();
    }

    /** 429 on the second call means the path is covered by a rule. */
    private boolean isThrottled(String method, String path) throws Exception {
        return statusAfterTwoRequests(method, path, "203.0.113.9") == 429;
    }

    /**
     * The other half of a throttle's job, and the half nothing was checking:
     * a request it allows has to actually reach the application.
     *
     * <p>Every assertion in this class reads a status code, and a filter that
     * dropped allowed requests on the floor would leave the response at its
     * default 200 and satisfy all of them. Mutation testing found it by deleting
     * the chain call and watching the suite stay green.
     */
    @Test
    void anAllowedRequestIsPassedOn() throws Exception {
        RateLimitFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("203.0.113.10");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("the throttle must not swallow what it allows").isNotNull();
    }

    /** And a request it refuses must NOT reach the application. */
    @Test
    void aThrottledRequestIsStoppedBeforeTheApplication() throws Exception {
        RateLimitFilter filter = filter();
        MockFilterChain secondChain = new MockFilterChain();
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            request.setRemoteAddr("203.0.113.11");
            MockFilterChain chain = i == 0 ? new MockFilterChain() : secondChain;
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        assertThat(secondChain.getRequest())
                .as("a 429 that still ran the request would throttle nothing")
                .isNull();
    }

    @Test
    void everyAbuseProneEndpointIsThrottled() throws Exception {
        assertThat(isThrottled("POST", "/api/auth/login")).as("login: credential brute-force").isTrue();
        assertThat(isThrottled("POST", "/api/auth/register")).as("register: account flooding").isTrue();
        assertThat(isThrottled("POST", "/api/track")).as("view beacon").isTrue();
        assertThat(isThrottled("POST", "/api/public/kits/demo/unlock")).as("password unlock").isTrue();
        assertThat(isThrottled("POST", "/api/public/kits/demo/contact")).as("contact form").isTrue();
        // Both halves of recovery. Requesting is a mail amplifier aimed at
        // somebody else's inbox; confirming is a guess at a token, which is the
        // same shape of attack as guessing a password.
        assertThat(isThrottled("POST", "/api/auth/password-reset/request"))
                .as("reset request: mail amplification").isTrue();
        assertThat(isThrottled("POST", "/api/auth/password-reset/confirm"))
                .as("reset confirm: token guessing").isTrue();
    }

    /**
     * The three routes that verify a password. Each is a credential oracle if
     * left open, which is why they are throttled even though the caller is
     * already authenticated — and why deletion is matched on DELETE rather
     * than POST.
     */
    @Test
    void thePasswordVerifyingAccountRoutesAreThrottled() throws Exception {
        assertThat(isThrottled("POST", "/api/me/password")).as("password change").isTrue();
        assertThat(isThrottled("POST", "/api/me/email")).as("email change").isTrue();
        assertThat(isThrottled("DELETE", "/api/me")).as("account deletion").isTrue();
    }

    /**
     * Profile edits verify nothing, so throttling them would only slow honest
     * use. This pins that exclusion so it cannot be widened by accident.
     */
    @Test
    void ordinaryReadsAndProfileEditsAreNotThrottled() throws Exception {
        assertThat(isThrottled("PUT", "/api/me")).as("profile edit").isFalse();
        assertThat(isThrottled("GET", "/api/me")).as("read own account").isFalse();
        assertThat(isThrottled("GET", "/api/mediakits")).as("list kits").isFalse();
        assertThat(isThrottled("POST", "/api/mediakits")).as("create a kit").isFalse();
    }

    /**
     * A GET to a throttled path must not consume the POST's budget: the rules
     * are about the write, and matching on path alone would let a crawler
     * exhaust a real user's allowance.
     */
    @Test
    void theMethodIsPartOfTheRule() throws Exception {
        assertThat(isThrottled("GET", "/api/auth/login")).isFalse();
        assertThat(isThrottled("GET", "/api/track")).isFalse();
        // DELETE /api/me is throttled, but DELETE elsewhere is not.
        assertThat(isThrottled("DELETE", "/api/mediakits/1")).isFalse();
    }

    /** Buckets are per client, so one abuser cannot lock everyone else out. */
    @Test
    void budgetsAreCountedPerClient() throws Exception {
        RateLimitFilter filter = filter();
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            request.setRemoteAddr("203.0.113.1");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest otherClient = new MockHttpServletRequest("POST", "/api/auth/login");
        otherClient.setRequestURI("/api/auth/login");
        otherClient.setRemoteAddr("203.0.113.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(otherClient, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** Disabled means disabled: nothing is throttled, whatever the path. */
    @Test
    void theKillSwitchTurnsEveryRuleOff() throws Exception {
        RateLimitFilter disabled = new RateLimitFilter(new RateLimiterRegistry(), false, 1, 1, 1, 1, 1, 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            request.setRemoteAddr("203.0.113.5");
            response = new MockHttpServletResponse();
            disabled.doFilter(request, response, new MockFilterChain());
        }
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * An allowed request must actually continue down the chain. Asserting only
     * on the status would not notice a filter that quietly swallowed every
     * request: the response would still read 200, just empty.
     */
    @Test
    void anAllowedRequestIsPassedDownTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        request.setRemoteAddr("203.0.113.11");
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("the chain must have run").isSameAs(request);
    }

    @Test
    void aRejectionSaysWhyInJson() throws Exception {
        RateLimitFilter filter = filter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRequestURI("/api/auth/login");
            request.setRemoteAddr("203.0.113.7");
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("429");
    }
}
