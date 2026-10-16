package com.novabank;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void clientKeysTakeRightmostAndSecondFromRight() {
        assertArrayEquals(new String[] {"5.6.7.8", "1.2.3.4"},
                RateLimitFilter.clientKeys("1.2.3.4, 5.6.7.8", "9.9.9.9"));
    }

    @Test
    void singleForwardedAddressHasNoClientKey() {
        assertArrayEquals(new String[] {"5.6.7.8", null},
                RateLimitFilter.clientKeys("5.6.7.8", "9.9.9.9"));
    }

    @Test
    void missingHeaderFallsBackToRemoteAddress() {
        assertArrayEquals(new String[] {"9.9.9.9", null},
                RateLimitFilter.clientKeys(null, "9.9.9.9"));
    }

    @Test
    void sixthLoginFromSameClientIsBlockedEvenWhenEdgeAddressChanges() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, send("POST", "/auth/login", "49.47.71.85,52.66.62." + i, "172.68.0." + i));
        }
        assertEquals(429, send("POST", "/auth/login", "49.47.71.85,52.66.62.99", "172.68.0.99"));
    }

    @Test
    void differentClientsBehindTheSameEdgeAreIndependent() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertEquals(200, send("POST", "/auth/login", "49.47.71.85,52.66.62.144", "172.68.0.1"));
        }
        assertEquals(429, send("POST", "/auth/login", "49.47.71.85,52.66.62.144", "172.68.0.1"));
        assertEquals(200, send("POST", "/auth/login", "203.0.113.9,52.66.62.144", "172.68.0.1"));
    }

    @Test
    void forgedHeaderCannotBypassTheLimitOnTheDirectPath() throws Exception {
        for (int i = 1; i <= RateLimitFilter.EDGE_LIMIT; i++) {
            assertEquals(200, send("POST", "/auth/login", "10.0.0." + i + ",203.0.113.7", "172.68.0.1"));
        }
        assertEquals(429, send("POST", "/auth/login", "10.0.1.1,203.0.113.7", "172.68.0.1"));
    }

    @Test
    void getRequestsAreNotCounted() throws Exception {
        for (int i = 1; i <= 30; i++) {
            assertEquals(200, send("GET", "/auth/login", "49.47.71.85,52.66.62.144", "172.68.0.1"));
        }
        assertEquals(200, send("POST", "/auth/login", "49.47.71.85,52.66.62.144", "172.68.0.1"));
    }

    @Test
    void otherPathsAreNotLimited() throws Exception {
        for (int i = 1; i <= 30; i++) {
            assertEquals(200, send("POST", "/accounts", "49.47.71.85,52.66.62.144", "172.68.0.1"));
        }
    }

    private int send(String method, String path, String forwardedFor, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}