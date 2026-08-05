package xyz.tcheeric.bottin.api.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the client IP the rate limiters key on comes from the connection
 * rather than from headers the caller controls.
 */
class ClientIpTest {

    /**
     * Tests that a request with no proxy headers resolves to its remote address.
     */
    @Test
    void shouldResolveTheRemoteAddress() {
        // Given: a direct request
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");

        // When: the client IP is resolved
        String clientIp = ClientIp.resolve(request);

        // Then: it is the address the request came from
        assertThat(clientIp).isEqualTo("198.51.100.7");
    }

    /**
     * Tests that a forged X-Forwarded-For header is ignored. Honouring it would let
     * any caller present a fresh address per request and bypass the rate limit;
     * where a real proxy sets the header, the container has already applied it to
     * the remote address.
     */
    @Test
    void shouldIgnoreAForgedForwardedForHeader() {
        // Given: a direct request claiming to have been forwarded for someone else
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 203.0.113.2");

        // When: the client IP is resolved
        String clientIp = ClientIp.resolve(request);

        // Then: the header is disregarded in favour of the actual peer
        assertThat(clientIp).isEqualTo("198.51.100.7");
    }
}
