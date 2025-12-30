package xyz.tcheeric.bottin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Test configuration for web module tests.
 * Provides a minimal Spring Boot context for testing controllers.
 */
@SpringBootApplication(scanBasePackages = "xyz.tcheeric.bottin.web")
public class TestWebConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
