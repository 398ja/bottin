package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.core.exception.SettingsNotFoundException;
import xyz.tcheeric.bottin.core.model.SettingsData;
import xyz.tcheeric.bottin.persistence.entity.SettingsEntity;
import xyz.tcheeric.bottin.persistence.repository.SettingsRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service managing the deployment's admin-maintained settings: the media server,
 * the system relays, the profile discovery relays, and the public rate limit.
 *
 * <p>The relay-scheme rule lives here rather than only on the admin form, so
 * that no caller can store a URL a Nostr client could never connect to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final String EMPTY_RELAYS_JSON = "[]";

    /**
     * Nostr relays speak WebSocket, so a relay URL that is not {@code ws://} or
     * {@code wss://} is unreachable no matter what else is configured.
     */
    private static final Pattern RELAY_URL = Pattern.compile("wss?://\\S+");

    private final SettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves the deployment settings.
     */
    @Transactional(readOnly = true)
    public SettingsData find() {
        return toSettingsData(loadSettings());
    }

    /**
     * Stores the deployment settings and returns them as persisted.
     *
     * <p>Both relay lists are normalised before anything is read or written, so
     * a rejected URL leaves the stored settings untouched rather than applying
     * in part.
     */
    @Transactional
    public SettingsData update(SettingsData settings) {
        List<String> systemRelays = normalizeRelays(settings.getDefaultRelays());
        List<String> discoveryRelays = normalizeRelays(settings.getDiscoveryRelays());

        SettingsEntity entity = loadSettings();
        entity.setBlossomUrl(trimToNull(settings.getBlossomUrl()));
        entity.setDefaultRelaysJson(serializeRelays(systemRelays));
        entity.setDiscoveryRelaysJson(serializeRelays(discoveryRelays));
        entity.setRateLimitPerMinute(settings.getRateLimitPerMinute());

        entity = settingsRepository.save(entity);

        log.info("settings_updated media_server_configured={} system_relays={} discovery_relays={} rate_limit_per_minute={}",
                entity.getBlossomUrl() != null, systemRelays.size(), discoveryRelays.size(),
                entity.getRateLimitPerMinute());

        return toSettingsData(entity);
    }

    private SettingsEntity loadSettings() {
        return settingsRepository.findById(SettingsEntity.SINGLETON_ID)
                .orElseThrow(SettingsNotFoundException::new);
    }

    private SettingsData toSettingsData(SettingsEntity entity) {
        return SettingsData.builder()
                .blossomUrl(entity.getBlossomUrl())
                .defaultRelays(deserializeRelays(entity.getDefaultRelaysJson()))
                .discoveryRelays(deserializeRelays(entity.getDiscoveryRelaysJson()))
                .rateLimitPerMinute(entity.getRateLimitPerMinute())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Trims each entry, drops blanks left by stray newlines in a textarea, and
     * collapses duplicates while keeping the order the operator typed.
     *
     * @throws IllegalArgumentException naming the first URL whose scheme is not
     *                                  {@code ws://} or {@code wss://}
     */
    private List<String> normalizeRelays(List<String> relays) {
        if (relays == null) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String relay : relays) {
            if (relay == null || relay.isBlank()) {
                continue;
            }
            String url = relay.trim();
            if (!RELAY_URL.matcher(url).matches()) {
                throw new IllegalArgumentException("Relay URL must start with ws:// or wss://: " + url);
            }
            normalized.add(url);
        }
        return List.copyOf(normalized);
    }

    private String serializeRelays(List<String> relays) {
        if (relays.isEmpty()) {
            return EMPTY_RELAYS_JSON;
        }
        try {
            return objectMapper.writeValueAsString(relays);
        } catch (JsonProcessingException e) {
            log.warn("settings_relay_serialize_failed relay_count={} error={}", relays.size(), e.getMessage());
            return EMPTY_RELAYS_JSON;
        }
    }

    private List<String> deserializeRelays(String relaysJson) {
        if (relaysJson == null || relaysJson.isBlank() || EMPTY_RELAYS_JSON.equals(relaysJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(relaysJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("settings_relay_parse_failed relays_json={} error={}", relaysJson, e.getMessage());
            return List.of();
        }
    }

    /**
     * A blank media server means unconfigured, and unconfigured has one
     * representation: null. Storing "" alongside null would give it two.
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
