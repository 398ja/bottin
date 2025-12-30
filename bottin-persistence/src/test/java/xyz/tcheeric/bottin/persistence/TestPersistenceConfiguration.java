package xyz.tcheeric.bottin.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test configuration for @DataJpaTest.
 * This class provides the Spring Boot context needed for repository tests.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "xyz.tcheeric.bottin.persistence.repository")
@EntityScan(basePackages = "xyz.tcheeric.bottin.persistence.entity")
public class TestPersistenceConfiguration {
}
