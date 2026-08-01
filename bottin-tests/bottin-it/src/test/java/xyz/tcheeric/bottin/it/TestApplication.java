package xyz.tcheeric.bottin.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test application for integration tests.
 *
 * <p>Deliberately minimal. Every bottin bean the integration tests need —
 * repositories, entities, services, verification — is contributed by
 * {@code BottinAutoConfiguration}, which the starter registers through
 * {@code AutoConfiguration.imports} and which component-scans the bottin
 * packages including {@code xyz.tcheeric.bottin.api}.
 *
 * <p>This class therefore must <em>not</em> declare its own
 * {@code @EnableJpaRepositories} or {@code @EntityScan}. The auto-configuration's
 * scan picks up {@code BottinApiApplication}, itself a
 * {@code @SpringBootApplication} declaring {@code @EnableJpaRepositories} over
 * the same repository package; a second declaration here registers every
 * repository twice and the context dies with
 * {@code BeanDefinitionOverrideException} before a single test runs.
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
