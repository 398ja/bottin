package xyz.tcheeric.bottin.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;
import xyz.tcheeric.nsecbunker.account.nip05.Nip05Manager;
import xyz.tcheeric.nsecbunker.account.registration.AccountManager;

/**
 * Spring Boot auto-configuration for bottin NIP-05 registry.
 *
 * <p>This configuration automatically sets up bottin's services when
 * the starter is on the classpath and bottin.enabled=true (default).</p>
 *
 * <p>Features enabled by this auto-configuration:</p>
 * <ul>
 *   <li>Database-backed NIP-05 record management</li>
 *   <li>Integration with nsecbunker-java via SPI providers</li>
 *   <li>Domain verification services</li>
 *   <li>External NIP-05 verification with caching</li>
 *   <li>REST API endpoints</li>
 *   <li>Admin dashboard (if enabled)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(Nip05Manager.class)
@ConditionalOnProperty(prefix = "bottin", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BottinProperties.class)
@ComponentScan(basePackages = {
        "xyz.tcheeric.bottin.starter",
        "xyz.tcheeric.bottin.persistence",
        "xyz.tcheeric.bottin.service",
        "xyz.tcheeric.bottin.verification",
        "xyz.tcheeric.bottin.web"
})
@Slf4j
public class BottinAutoConfiguration {

    /**
     * Creates the persistent NIP-05 manager if not already defined.
     */
    @Bean
    @ConditionalOnMissingBean(Nip05Manager.class)
    public PersistentNip05Manager persistentNip05Manager(
            Nip05RecordRepository nip05RecordRepository,
            DomainRepository domainRepository,
            ObjectMapper objectMapper) {
        log.info("bottin_autoconfiguration_nip05_manager_created");
        return new PersistentNip05Manager(nip05RecordRepository, domainRepository, objectMapper);
    }

    /**
     * Creates the persistent account manager if not already defined.
     */
    @Bean
    @ConditionalOnMissingBean(AccountManager.class)
    public PersistentAccountManager persistentAccountManager(
            PersistentNip05Manager nip05Manager,
            Nip05RecordRepository nip05RecordRepository) {
        log.info("bottin_autoconfiguration_account_manager_created");
        return new PersistentAccountManager(nip05Manager, nip05RecordRepository);
    }

    /**
     * Creates the Nip05Manager provider for nsecbunker-java SPI.
     */
    @Bean
    @ConditionalOnMissingBean(BottinNip05ManagerProvider.class)
    public BottinNip05ManagerProvider bottinNip05ManagerProvider(PersistentNip05Manager persistentNip05Manager) {
        log.info("bottin_autoconfiguration_nip05_provider_created");
        return new BottinNip05ManagerProvider(persistentNip05Manager);
    }

    /**
     * Creates the AccountManager provider for nsecbunker-java SPI.
     */
    @Bean
    @ConditionalOnMissingBean(BottinAccountManagerProvider.class)
    public BottinAccountManagerProvider bottinAccountManagerProvider(PersistentAccountManager persistentAccountManager) {
        log.info("bottin_autoconfiguration_account_provider_created");
        return new BottinAccountManagerProvider(persistentAccountManager);
    }

    /**
     * Creates the ObjectMapper if not already defined.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
