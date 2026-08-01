package xyz.tcheeric.bottin.client.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the filter that requires an authenticated NAP principal on the
 * protected client API paths.
 */
class RequireNapAuthenticationFilterTest {

    private final RequireNapAuthenticationFilter filter = new RequireNapAuthenticationFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Tests that a request with no authenticated principal is rejected with 401 and never
     * reaches the downstream chain.
     */
    @Test
    void shouldReturnUnauthorizedWhenNoPrincipalIsPresent() throws Exception {
        // Arrange: empty security context (anonymous request)
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/follow");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Act: run the filter
        filter.doFilter(request, response, chain);

        // Then: request is rejected and the chain is not invoked
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("NAP_SESSION_REQUIRED");
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Tests that a request carrying an authenticated principal is allowed to proceed down
     * the chain without modifying the response status.
     */
    @Test
    void shouldProceedWhenAuthenticatedPrincipalIsPresent() throws Exception {
        // Arrange: an authenticated principal in the security context
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("npub1abc", "n/a", List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/follow");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Act: run the filter
        filter.doFilter(request, response, chain);

        // Then: the request passes through untouched
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
