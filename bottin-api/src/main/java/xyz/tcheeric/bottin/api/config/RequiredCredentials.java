package xyz.tcheeric.bottin.api.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Refuses to start a production instance whose credentials were never configured.
 *
 * <p>Both passwords fall back to a fresh random UUID when unset, which fails closed
 * rather than shipping a known credential — but in production it fails silently:
 * every configured caller is locked out, and differently after each restart. Saying
 * so at startup turns that into an obvious misconfiguration.
 */
@Configuration
@Profile("prod")
@RequiredArgsConstructor
public class RequiredCredentials {

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "bottin.admin.password",
            "bottin.api.password"
    );

    private final Environment environment;

    @PostConstruct
    void verifyConfigured() {
        List<String> missing = REQUIRED_PROPERTIES.stream()
                .filter(property -> !hasText(environment.getProperty(property)))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Production startup blocked: " + String.join(", ", missing) + " not configured. "
                            + "Suggestion: set BOTTIN_ADMIN_PASSWORD and BOTTIN_API_PASSWORD, or run with a "
                            + "non-production profile where the random per-restart defaults are acceptable.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
