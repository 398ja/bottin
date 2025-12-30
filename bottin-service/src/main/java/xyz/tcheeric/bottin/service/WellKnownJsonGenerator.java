package xyz.tcheeric.bottin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.Nip05RecordRepository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service that generates NIP-05 compliant JSON responses for the .well-known/nostr.json endpoint.
 *
 * <p>Per NIP-05 specification, the response format is:
 * <pre>
 * {
 *   "names": {
 *     "alice": "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
 *   },
 *   "relays": {
 *     "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798": [
 *       "wss://relay1.example.com",
 *       "wss://relay2.example.com"
 *     ]
 *   }
 * }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WellKnownJsonGenerator {

    private final Nip05RecordRepository nip05RecordRepository;
    private final DomainRepository domainRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generates NIP-05 JSON for a specific username in a domain.
     *
     * @param domain   the domain name
     * @param username the username to look up
     * @return NIP-05 compliant JSON map, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> generateForUsername(String domain, String username) {
        log.debug("wellknown_lookup domain={} username={}", domain, username);

        String normalizedUsername = username.toLowerCase();
        String normalizedDomain = domain.toLowerCase();

        Optional<Nip05RecordEntity> recordOpt = nip05RecordRepository
                .findByUsernameAndDomainName(normalizedUsername, normalizedDomain);

        if (recordOpt.isEmpty()) {
            log.debug("wellknown_lookup_not_found domain={} username={}", normalizedDomain, normalizedUsername);
            return Optional.empty();
        }

        Nip05RecordEntity record = recordOpt.get();
        if (!record.isEnabled()) {
            log.debug("wellknown_lookup_disabled domain={} username={}", normalizedDomain, normalizedUsername);
            return Optional.empty();
        }

        Map<String, Object> response = buildResponse(List.of(record));
        log.debug("wellknown_lookup_success domain={} username={} pubkey={}",
                normalizedDomain, normalizedUsername, record.getPubkey());
        return Optional.of(response);
    }

    /**
     * Generates NIP-05 JSON for all enabled records in a domain.
     *
     * @param domain the domain name
     * @return NIP-05 compliant JSON map containing all enabled records
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateForDomain(String domain) {
        String normalizedDomain = domain.toLowerCase();
        log.debug("wellknown_domain_lookup domain={}", normalizedDomain);

        if (!domainRepository.existsByName(normalizedDomain)) {
            log.debug("wellknown_domain_not_found domain={}", normalizedDomain);
            return buildEmptyResponse();
        }

        List<Nip05RecordEntity> records = nip05RecordRepository
                .findByDomainNameAndEnabledTrue(normalizedDomain);

        log.debug("wellknown_domain_lookup_success domain={} record_count={}",
                normalizedDomain, records.size());

        return buildResponse(records);
    }

    /**
     * Builds a NIP-05 compliant response from a list of records.
     */
    private Map<String, Object> buildResponse(List<Nip05RecordEntity> records) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<String>> relays = new LinkedHashMap<>();

        for (Nip05RecordEntity record : records) {
            names.put(record.getUsername(), record.getPubkey());

            List<String> relayList = parseRelays(record.getRelaysJson());
            if (!relayList.isEmpty()) {
                relays.put(record.getPubkey(), relayList);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("names", names);
        if (!relays.isEmpty()) {
            response.put("relays", relays);
        }

        return response;
    }

    /**
     * Builds an empty NIP-05 response.
     */
    private Map<String, Object> buildEmptyResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("names", new HashMap<>());
        return response;
    }

    /**
     * Parses the relays JSON string into a list of relay URLs.
     */
    private List<String> parseRelays(String relaysJson) {
        if (relaysJson == null || relaysJson.isBlank() || relaysJson.equals("[]")) {
            return List.of();
        }

        try {
            return objectMapper.readValue(relaysJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("wellknown_relay_parse_failed relays_json={} error={}", relaysJson, e.getMessage());
            return List.of();
        }
    }
}
