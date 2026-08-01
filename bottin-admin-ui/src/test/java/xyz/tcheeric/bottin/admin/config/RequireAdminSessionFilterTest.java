package xyz.tcheeric.bottin.admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
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
 * Unit tests for RequireAdminSessionFilter.
 *
 * <p>The dashboard's protected surface is server-rendered pages, so an anonymous
 * browser must be sent somewhere it can act rather than shown a bare error. These
 * tests pin that behaviour, and the exception to it.
 */
class RequireAdminSessionFilterTest {

    private static final String SIGN_IN_PATH = "/admin/login";

    private final RequireAdminSessionFilter filter = new RequireAdminSessionFilter(SIGN_IN_PATH);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Tests that a browser with no session is sent to the sign-in page, rather
     * than shown the blank error a bare 401 renders as.
     */
    @Test
    void shouldRedirectABrowserWithNoSessionToSignIn() throws Exception {
        // Given: a browser navigating to an admin page with no session
        MockHttpServletRequest request = browserRequest("/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When
        filter.doFilter(request, response, chain);

        // Then: redirected, and the page was never rendered
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_TEMPORARILY);
        assertThat(response.getRedirectedUrl()).isEqualTo(SIGN_IN_PATH);
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Tests that a caller which does not want HTML gets a status code instead of
     * a redirect, so an API added to the dashboard later reports failure rather
     * than returning a sign-in page.
     */
    @Test
    void shouldAnswerUnauthorizedWhenTheCallerDoesNotWantHtml() throws Exception {
        // Given: a request that asks for JSON
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/records");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getRedirectedUrl()).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Tests that the sign-in page is reachable without a session. Redirecting it
     * would send an anonymous visitor round a loop.
     */
    @Test
    void shouldAllowTheSignInPageWithoutASession() throws Exception {
        // Given: an anonymous browser asking for the sign-in page itself
        MockHttpServletRequest request = browserRequest(SIGN_IN_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When
        filter.doFilter(request, response, chain);

        // Then: served, not redirected
        assertThat(response.getRedirectedUrl()).isNull();
        verify(chain).doFilter(request, response);
    }

    /**
     * Tests that a caller whose authentication is not a NAP session is turned
     * away, so a leftover authentication of some other kind cannot stand in for
     * proof of the administrator's key.
     */
    @Test
    void shouldRejectAnAuthenticationThatIsNotANapSession() throws Exception {
        // Given: a principal established by something other than NAP
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "secret", List.of()));
        MockHttpServletRequest request = browserRequest("/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When
        filter.doFilter(request, response, chain);

        // Then: redirected to sign in regardless
        assertThat(response.getRedirectedUrl()).isEqualTo(SIGN_IN_PATH);
        verify(chain, never()).doFilter(request, response);
    }

    private MockHttpServletRequest browserRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        return request;
    }
}
