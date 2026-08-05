package xyz.tcheeric.bottin.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bound configuration for NIP-05 record creation ({@code bottin.record.*}).
 *
 * <p>{@link #defaultRelays} is the application's own relay list — "the relay
 * associated with this deployment". On record creation it is <em>merged</em>
 * (union, de-duplicated) with any caller-supplied relays so that every record
 * is always reachable on the app's infra relay, regardless of what the caller
 * (customer registration, merchant onboarding, admin form) sends.
 *
 * <p>Deliberately empty by default — no environment-specific hostname is baked
 * into code (see the {@code no_hardcoded_domains} rule). The value is supplied
 * per deployment via {@code BOTTIN_RECORD_DEFAULTRELAYS_0}, e.g.
 * {@code wss://relay.staging.398ja.xyz} on staging.
 *
 * <p>Uses {@code @ConfigurationProperties} (not {@code @Value}) so the indexed
 * list env var binds correctly, mirroring {@code bottin.reach.default-relays}.
 */
@Component
@Data
@ConfigurationProperties(prefix = "bottin.record")
public class Nip05RecordProperties {

    /** App relays merged into every created record (union with caller-supplied). */
    private List<String> defaultRelays = new ArrayList<>();
}
