package xyz.tcheeric.bottin.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

/**
 * Spring Boot auto-configuration for bottin NIP-05 registry.
 *
 * <p>This configuration automatically sets up bottin's services when
 * the starter is on the classpath and bottin.enabled=true (default).</p>
 *
 * <p>Features enabled by this auto-configuration:</p>
 * <ul>
 *   <li>Database-backed NIP-05 record management</li>
 *   <li>Domain verification services</li>
 *   <li>External NIP-05 verification with caching</li>
 *   <li>Profile reach calculation</li>
 * </ul>
 *
 * <p>The delivery layer ({@code xyz.tcheeric.bottin.api}) is deliberately
 * <em>not</em> scanned. Scanning it from an auto-configuration grafted bottin's
 * REST controllers and, worse, its "any request" security filter chain into
 * every application that merely put this starter on the classpath. Because
 * auto-configuration runs after user configuration, that chain arrived too late
 * for {@code @ConditionalOnDefaultWebSecurity} to stand down, and the context
 * failed to start with two chains matching every request. The same scan pulled
 * in {@code BottinApiApplication} — itself a {@code @SpringBootApplication} with
 * its own {@code @EnableJpaRepositories} — registering every repository twice.
 *
 * <p>An application that wants the REST layer declares it explicitly, as
 * {@code BottinApiApplication} does. A starter should offer services, not decide
 * how its consumer is secured.
 */
@AutoConfiguration
@ConditionalOnClass(Nip05RecordRepository.class)
@ConditionalOnProperty(prefix = "bottin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BottinProperties.class)
@ComponentScan(basePackages = {
        "xyz.tcheeric.bottin.starter",
        "xyz.tcheeric.bottin.persistence",
        "xyz.tcheeric.bottin.service",
        "xyz.tcheeric.bottin.verification",
        "xyz.tcheeric.bottin.reach"
})
public class BottinAutoConfiguration {

    /**
     * Creates the ObjectMapper if not already defined.
     * Configures support for Java 8 date/time types.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }
}
