package xyz.tcheeric.bottin.client.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NostrIdentityService}.
 * Currently a placeholder; expand when service logic is implemented.
 */
@ExtendWith(MockitoExtension.class)
class NostrIdentityServiceTest {

    private final NostrIdentityService service = new NostrIdentityService();

    @Test
    void shouldBeAnnotatedWithService() {
        assertThat(NostrIdentityService.class)
                .hasAnnotation(Service.class);
    }

    @Test
    void shouldInstantiateSuccessfully() {
        assertThat(service).isNotNull();
    }
}
