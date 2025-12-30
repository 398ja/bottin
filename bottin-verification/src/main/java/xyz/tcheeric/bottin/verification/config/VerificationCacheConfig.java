package xyz.tcheeric.bottin.verification.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for caching external NIP-05 verification results.
 *
 * <p>Uses Caffeine cache with configurable TTL and size limits to reduce
 * load on external NIP-05 servers while maintaining reasonable freshness.
 */
@Configuration
@EnableCaching
public class VerificationCacheConfig {

    @Value("${bottin.verification.cache.ttl-minutes:5}")
    private int cacheTtlMinutes;

    @Value("${bottin.verification.cache.max-size:1000}")
    private int cacheMaxSize;

    /**
     * Creates a cache manager for external NIP-05 verification results.
     *
     * @return configured cache manager
     */
    @Bean
    public CacheManager verificationCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("externalNip05Verifications");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    /**
     * Configures the Caffeine cache with expiration and size limits.
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .recordStats();
    }
}
