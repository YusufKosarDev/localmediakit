package com.localmediakit.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpTest {

    private HttpServletRequest req(String cf, String xff, String remote) {
        HttpServletRequest r = Mockito.mock(HttpServletRequest.class);
        Mockito.when(r.getHeader("CF-Connecting-IP")).thenReturn(cf);
        Mockito.when(r.getHeader("X-Forwarded-For")).thenReturn(xff);
        Mockito.when(r.getRemoteAddr()).thenReturn(remote);
        return r;
    }

    @Test
    void prefersCloudflareConnectingIp() {
        // Even with a forged X-Forwarded-For, the CF header (unspoofable) wins.
        assertEquals("203.0.113.7",
                ClientIp.resolve(req("203.0.113.7", "1.2.3.4, 5.6.7.8", "10.0.0.1")));
    }

    @Test
    void fallsBackToRightmostForwardedHop() {
        // No CF header: take the rightmost (proxy-appended) hop, not the
        // leftmost client-controlled one.
        assertEquals("5.6.7.8",
                ClientIp.resolve(req(null, "1.2.3.4, 5.6.7.8", "10.0.0.1")));
    }

    @Test
    void singleForwardedValueIsUsedDirectly() {
        assertEquals("9.9.9.9", ClientIp.resolve(req(null, "9.9.9.9", "10.0.0.1")));
    }

    @Test
    void fallsBackToRemoteAddrWhenNoHeaders() {
        assertEquals("10.0.0.1", ClientIp.resolve(req(null, null, "10.0.0.1")));
        assertEquals("10.0.0.1", ClientIp.resolve(req("", "  ", "10.0.0.1")));
    }
}
