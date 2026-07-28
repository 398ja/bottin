package xyz.tcheeric.bottin.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration for the API module's tests.
 * Provides a minimal Spring Boot context for testing controllers.
 * Only scans the api package to avoid loading JPA dependencies.
 */
@SpringBootApplication(scanBasePackages = "xyz.tcheeric.bottin.api")
public class TestApiConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
