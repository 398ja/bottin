package xyz.tcheeric.bottin.client.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FollowListService}.
 * Currently a placeholder; expand when service logic is implemented.
 */
@ExtendWith(MockitoExtension.class)
class FollowListServiceTest {

    private final FollowListService service = new FollowListService();

    @Test
    void shouldBeAnnotatedWithService() {
        assertThat(FollowListService.class)
                .hasAnnotation(Service.class);
    }

    @Test
    void shouldInstantiateSuccessfully() {
        assertThat(service).isNotNull();
    }
}
