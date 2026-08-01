package xyz.tcheeric.bottin.client.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SearchService}.
 * Currently a placeholder; expand when service logic is implemented.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    private final SearchService service = new SearchService();

    @Test
    void shouldBeAnnotatedWithService() {
        assertThat(SearchService.class)
                .hasAnnotation(Service.class);
    }

    @Test
    void shouldInstantiateSuccessfully() {
        assertThat(service).isNotNull();
    }
}
