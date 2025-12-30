package xyz.tcheeric.bottin.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration for Bottin web endpoints.
 *
 * <p>The .well-known/nostr.json endpoint must be accessible from any origin
 * per NIP-05 specification, as Nostr clients need to verify NIP-05 identifiers
 * from browser contexts.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow all origins for the well-known endpoint (NIP-05 requirement)
        registry.addMapping("/.well-known/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        // Allow all origins for public verification endpoint
        registry.addMapping("/api/v1/verify")
                .allowedOrigins("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Provides a CorsConfigurationSource bean for use with Spring Security.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration wellKnownConfig = new CorsConfiguration();
        wellKnownConfig.setAllowedOrigins(List.of("*"));
        wellKnownConfig.setAllowedMethods(List.of("GET", "OPTIONS"));
        wellKnownConfig.setAllowedHeaders(List.of("*"));
        wellKnownConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/.well-known/**", wellKnownConfig);
        source.registerCorsConfiguration("/api/v1/verify", wellKnownConfig);

        return source;
    }
}
