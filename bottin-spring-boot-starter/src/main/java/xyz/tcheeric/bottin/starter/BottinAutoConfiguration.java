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
 *   <li>REST API endpoints</li>
 *   <li>Admin dashboard (if enabled)</li>
 * </ul>
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
        "xyz.tcheeric.bottin.reach",
        "xyz.tcheeric.bottin.api"
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
