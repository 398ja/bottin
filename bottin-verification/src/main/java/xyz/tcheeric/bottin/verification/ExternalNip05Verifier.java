package xyz.tcheeric.bottin.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import xyz.tcheeric.bottin.core.model.VerificationLogData;
import xyz.tcheeric.bottin.persistence.entity.VerificationLogEntity;
import xyz.tcheeric.bottin.persistence.repository.VerificationLogRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for verifying external NIP-05 identifiers.
 *
 * <p>Fetches {@code https://{domain}/.well-known/nostr.json?name={username}}
 * and extracts the pubkey and relay information for the given identifier.
 *
 * <p>Results are cached using Caffeine to reduce load on external servers.
 */
@Service
@Slf4j
public class ExternalNip05Verifier {

    private static final String WELL_KNOWN_NOSTR_PATH = "/.well-known/nostr.json";
    // Pattern allows optional port for development/testing (e.g., localhost:8080)
    private static final Pattern NIP05_PATTERN = Pattern.compile(
            "^([a-z0-9_.-]+)@([a-z0-9.-]+(?:\\.[a-z]{2,})?(?::[0-9]+)?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_PUBKEY_PATTERN = Pattern.compile("^[0-9a-f]{64}$", Pattern.CASE_INSENSITIVE);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final VerificationLogRepository verificationLogRepository;

    @Value("${bottin.verification.external.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${bottin.verification.external.require-https:true}")
    private boolean requireHttps;

    @Value("${bottin.verification.external.max-response-size:102400}")
    private int maxResponseSize;

    public ExternalNip05Verifier(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            VerificationLogRepository verificationLogRepository) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(102400)) // 100KB max
                .build();
        this.objectMapper = objectMapper;
        this.verificationLogRepository = verificationLogRepository;
    }

    /**
     * Verifies an external NIP-05 identifier.
     *
     * @param nip05 the NIP-05 identifier (e.g., "alice@example.com")
     * @return the verification result
     */
    @Cacheable(value = "externalNip05Verifications", key = "#nip05.toLowerCase()")
    public ExternalNip05VerificationResult verify(String nip05) {
        log.debug("external_nip05_verification_start nip05={}", nip05);

        if (nip05 == null || nip05.isBlank()) {
            return logAndReturnFailure(nip05, "NIP-05 identifier is required", null);
        }

        ParsedNip05 parsed = parseNip05(nip05);
        if (parsed == null) {
            return logAndReturnFailure(nip05, "Invalid NIP-05 format. Expected format: username@domain", null);
        }

        String url = buildNostrJsonUrl(parsed.domain(), parsed.username());

        try {
            String response = fetchNostrJson(url);
            return parseAndValidateResponse(nip05, parsed, response);
        } catch (WebClientResponseException e) {
            String errorMessage = "HTTP error " + e.getStatusCode().value() + " fetching nostr.json";
            log.debug("external_nip05_verification_http_error nip05={} status={}", nip05, e.getStatusCode());
            return logAndReturnFailure(nip05, errorMessage, null);
        } catch (Exception e) {
            String errorMessage = extractErrorMessage(e);
            log.error("external_nip05_verification_error nip05={} error={}", nip05, errorMessage, e);
            return logAndReturnFailure(nip05, "Failed to verify: " + errorMessage, null);
        }
    }

    /**
     * Verifies an external NIP-05 identifier without using cache.
     *
     * @param nip05 the NIP-05 identifier
     * @return the verification result
     */
    public ExternalNip05VerificationResult verifyNoCache(String nip05) {
        log.debug("external_nip05_verification_nocache_start nip05={}", nip05);

        if (nip05 == null || nip05.isBlank()) {
            return logAndReturnFailure(nip05, "NIP-05 identifier is required", null);
        }

        ParsedNip05 parsed = parseNip05(nip05);
        if (parsed == null) {
            return logAndReturnFailure(nip05, "Invalid NIP-05 format. Expected format: username@domain", null);
        }

        String url = buildNostrJsonUrl(parsed.domain(), parsed.username());

        try {
            String response = fetchNostrJson(url);
            return parseAndValidateResponse(nip05, parsed, response);
        } catch (WebClientResponseException e) {
            String errorMessage = "HTTP error " + e.getStatusCode().value() + " fetching nostr.json";
            log.debug("external_nip05_verification_http_error nip05={} status={}", nip05, e.getStatusCode());
            return logAndReturnFailure(nip05, errorMessage, null);
        } catch (Exception e) {
            String errorMessage = extractErrorMessage(e);
            log.error("external_nip05_verification_error nip05={} error={}", nip05, errorMessage, e);
            return logAndReturnFailure(nip05, "Failed to verify: " + errorMessage, null);
        }
    }

    /**
     * Parses a NIP-05 identifier into username and domain parts.
     */
    ParsedNip05 parseNip05(String nip05) {
        var matcher = NIP05_PATTERN.matcher(nip05.strip());
        if (!matcher.matches()) {
            return null;
        }
        return new ParsedNip05(matcher.group(1).toLowerCase(), matcher.group(2).toLowerCase());
    }

    /**
     * Builds the URL for fetching the nostr.json file.
     */
    private String buildNostrJsonUrl(String domain, String username) {
        String protocol = requireHttps ? "https" : "http";
        return protocol + "://" + domain + WELL_KNOWN_NOSTR_PATH + "?name=" + username;
    }

    /**
     * Fetches the nostr.json content from the remote server.
     */
    private String fetchNostrJson(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    throw new WebClientResponseException(
                            "HTTP " + response.statusCode().value(),
                            response.statusCode().value(),
                            response.statusCode().toString(),
                            null, null, null);
                })
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    /**
     * Parses and validates the nostr.json response.
     */
    private ExternalNip05VerificationResult parseAndValidateResponse(
            String nip05,
            ParsedNip05 parsed,
            String response) {

        if (response == null || response.isBlank()) {
            return logAndReturnFailure(nip05, "Empty response from server", null);
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            // Validate "names" object exists
            JsonNode names = root.get("names");
            if (names == null || !names.isObject()) {
                return logAndReturnFailure(nip05, "Invalid nostr.json: missing 'names' object", response);
            }

            // Get pubkey for the username
            JsonNode pubkeyNode = names.get(parsed.username());
            if (pubkeyNode == null || pubkeyNode.isNull()) {
                return logAndReturnFailure(nip05, "Username not found in nostr.json", response);
            }

            String pubkey = pubkeyNode.asText();
            if (!isValidPubkey(pubkey)) {
                return logAndReturnFailure(nip05, "Invalid pubkey format in nostr.json", response);
            }

            // Extract relays (optional)
            List<String> relays = extractRelays(root, pubkey);

            log.info("external_nip05_verification_success nip05={} pubkey={} relays_count={}",
                    nip05, pubkey, relays.size());

            return logAndReturnSuccess(nip05, pubkey, relays, response);

        } catch (JsonProcessingException e) {
            return logAndReturnFailure(nip05, "Invalid JSON in nostr.json response", response);
        }
    }

    /**
     * Validates that a pubkey is a valid 64-character hex string.
     */
    private boolean isValidPubkey(String pubkey) {
        return pubkey != null && HEX_PUBKEY_PATTERN.matcher(pubkey).matches();
    }

    /**
     * Extracts relay URLs for a pubkey from the nostr.json response.
     */
    private List<String> extractRelays(JsonNode root, String pubkey) {
        List<String> relays = new ArrayList<>();

        JsonNode relaysNode = root.get("relays");
        if (relaysNode == null || !relaysNode.isObject()) {
            return relays;
        }

        JsonNode pubkeyRelays = relaysNode.get(pubkey);
        if (pubkeyRelays == null || !pubkeyRelays.isArray()) {
            return relays;
        }

        for (JsonNode relay : pubkeyRelays) {
            if (relay.isTextual()) {
                String relayUrl = relay.asText();
                if (isValidRelayUrl(relayUrl)) {
                    relays.add(relayUrl);
                }
            }
        }

        return relays;
    }

    /**
     * Validates that a relay URL starts with wss:// or ws://.
     */
    private boolean isValidRelayUrl(String url) {
        return url != null && (url.startsWith("wss://") || url.startsWith("ws://"));
    }

    /**
     * Extracts a meaningful error message from an exception.
     */
    private String extractErrorMessage(Exception e) {
        if (e.getCause() != null) {
            return e.getCause().getMessage();
        }
        return e.getMessage();
    }

    /**
     * Logs a successful verification and returns the result.
     */
    private ExternalNip05VerificationResult logAndReturnSuccess(
            String nip05,
            String pubkey,
            List<String> relays,
            String remoteResponse) {

        try {
            VerificationLogEntity logEntity = VerificationLogEntity.fromVerificationLogData(
                    VerificationLogData.externalSuccess(nip05, truncateResponse(remoteResponse))
            );
            verificationLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("external_nip05_verification_log_save_failed nip05={} error={}", nip05, e.getMessage());
        }

        return ExternalNip05VerificationResult.success(nip05, pubkey, relays);
    }

    /**
     * Logs a failed verification and returns the result.
     */
    private ExternalNip05VerificationResult logAndReturnFailure(
            String nip05,
            String message,
            String remoteResponse) {

        try {
            VerificationLogEntity logEntity = VerificationLogEntity.fromVerificationLogData(
                    VerificationLogData.externalFailure(nip05 != null ? nip05 : "", message, truncateResponse(remoteResponse))
            );
            verificationLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("external_nip05_verification_log_save_failed nip05={} error={}", nip05, e.getMessage());
        }

        return ExternalNip05VerificationResult.failure(nip05 != null ? nip05 : "", message);
    }

    /**
     * Truncates a response to a reasonable length for logging.
     */
    private String truncateResponse(String response) {
        if (response == null) {
            return null;
        }
        if (response.length() > 2000) {
            return response.substring(0, 2000) + "...";
        }
        return response;
    }

    /**
     * Parsed NIP-05 identifier.
     */
    record ParsedNip05(String username, String domain) {}
}
