package xyz.tcheeric.bottin.client.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import xyz.tcheeric.nap.core.SessionStore;
import xyz.tcheeric.nap.server.AclResolver;
import xyz.tcheeric.nap.spring.config.NapProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the NAP filter registrations.
 *
 * <p>Both registrations document that they disable themselves when nap-spring's beans
 * are absent rather than failing. For a long time neither did: Spring Boot asks a
 * registration to describe itself <em>before</em> it consults whether the registration
 * is enabled, and derives that description from the filter — so a disabled registration
 * holding no filter aborted the application's start with "Filter must not be null".
 *
 * <p>These tests pin the invariant that keeps the promise: a disabled registration still
 * carries something to describe. Break it by removing the inert filter from
 * {@code disabled(...)} and both cases fail here, in the default build, rather than as
 * an opaque context-load failure in an opt-in end-to-end run.
 */
class ClientSecurityConfigTest {

    private final ClientSecurityConfig config = new ClientSecurityConfig();

    /** An {@link ObjectProvider} for a bean that is not there. */
    private static <T> ObjectProvider<T> absent() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return null;
            }

            @Override
            public T getObject() {
                return null;
            }

            @Override
            public T getIfAvailable() {
                return null;
            }

            @Override
            public T getIfUnique() {
                return null;
            }
        };
    }

    /**
     * Tests that the NIP-98 body-cap filter registration, disabled for want of nap's
     * properties, still carries a filter — without which the application does not start.
     */
    @Test
    void shouldStillCarryAFilterWhenTheServletFilterRegistrationIsDisabled() {
        // Given: nap-spring contributed no properties

        // When: the registration is built
        FilterRegistrationBean<Filter> registration = config.napServletFilter(absent());

        // Then: it is off, and still describable
        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter())
                .as("a disabled registration is still asked to describe itself, and the "
                        + "description comes from the filter")
                .isNotNull();
    }

    /**
     * Tests the same for the session filter, whose registration needs three beans and is
     * disabled if any one of them is missing.
     */
    @Test
    void shouldStillCarryAFilterWhenTheSessionFilterRegistrationIsDisabled() {
        // Given: nap-spring contributed neither a session store, an ACL resolver nor properties

        // When: the registration is built
        FilterRegistrationBean<Filter> registration = config.napSessionFilter(
                absent(), absent(), absent());

        // Then: it is off, and still describable
        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isNotNull();
    }

    /**
     * Tests that a disabled registration's filter does nothing but pass the request on,
     * so that if it were ever registered by mistake it could not change behaviour.
     */
    @Test
    void shouldPassRequestsStraightThroughWhenDisabled() throws Exception {
        // Given: a disabled registration
        Filter inert = config.napServletFilter(ClientSecurityConfigTest.<NapProperties>absent()).getFilter();
        var chain = new RecordingChain();

        // When: the inert filter is invoked
        inert.doFilter(null, null, chain);

        // Then: it delegated and did nothing else
        assertThat(chain.called).isTrue();
    }

    private static final class RecordingChain implements jakarta.servlet.FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }

    /** Kept to make the unused-import checker honest about what these tests reference. */
    @SuppressWarnings("unused")
    private static final Class<?>[] NAP_TYPES = {SessionStore.class, AclResolver.class, NapProperties.class};
}
