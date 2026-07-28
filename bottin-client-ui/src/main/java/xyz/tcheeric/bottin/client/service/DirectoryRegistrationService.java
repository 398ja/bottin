package xyz.tcheeric.bottin.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import xyz.tcheeric.bottin.client.config.ClientProperties;

import java.util.List;
import java.util.Map;

/**
 * Registers a freshly onboarded user's NIP-05 handle with the bottin directory.
 *
 * <p>The directory's {@code /api/v1/records} endpoint is behind HTTP Basic
 * authentication, so the browser cannot call it: this server holds the
 * credentials and makes the call on the user's behalf.
 */
@Service
@Slf4j
public class DirectoryRegistrationService {

    private final RestClient restClient;
    private final String domain;

    public DirectoryRegistrationService(ClientProperties clientProperties) {
        this.domain = clientProperties.getDomain();
        this.restClient = RestClient.builder()
                .baseUrl(clientProperties.getDirectoryUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        clientProperties.getDirectoryUsername(),
                        clientProperties.getDirectoryPassword()))
                .build();
    }

    /**
     * Stores {@code username@domain} against the given pubkey and returns the
     * resulting NIP-05 identifier.
     */
    public String register(String username, String pubkey, List<String> relays) {
        try {
            restClient.post()
                    .uri("/api/v1/records")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "username", username,
                            "domain", domain,
                            "pubkey", pubkey,
                            "relays", relays == null ? List.of() : relays))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw registrationFailure(username, pubkey, e);
        } catch (RestClientException e) {
            log.error("nip05_registration_failed nip05={}@{} pubkey={} reason=directory_unreachable error={}",
                    username, domain, pubkey, e.getMessage());
            throw new DirectoryRegistrationException(DirectoryRegistrationException.DIRECTORY_UNAVAILABLE,
                    "Could not reach the bottin directory to register " + username + "@" + domain, e);
        }

        log.info("nip05_registration_created nip05={}@{} pubkey={}", username, domain, pubkey);
        return username + "@" + domain;
    }

    private DirectoryRegistrationException registrationFailure(String username, String pubkey,
                                                               RestClientResponseException cause) {
        HttpStatus status = HttpStatus.resolve(cause.getStatusCode().value());
        String errorCode = switch (status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status) {
            case CONFLICT -> DirectoryRegistrationException.USERNAME_TAKEN;
            case NOT_FOUND -> DirectoryRegistrationException.DOMAIN_NOT_FOUND;
            default -> DirectoryRegistrationException.DIRECTORY_UNAVAILABLE;
        };

        log.warn("nip05_registration_rejected nip05={}@{} pubkey={} status={} code={}",
                username, domain, pubkey, cause.getStatusCode().value(), errorCode);

        return new DirectoryRegistrationException(errorCode,
                "Directory rejected " + username + "@" + domain + " with status " + cause.getStatusCode().value(),
                cause);
    }
}
