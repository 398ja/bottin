package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Represents a domain verification challenge.
 * Contains the token and instructions for the user to complete verification.
 */
@Value
@Builder(toBuilder = true)
public class VerificationChallenge {

    /**
     * The domain being verified.
     */
    String domain;

    /**
     * The verification method to use.
     */
    VerificationMethod method;

    /**
     * The verification token to be placed at the specified location.
     */
    String token;

    /**
     * Human-readable instructions for completing verification.
     */
    String instructions;

    /**
     * When this challenge expires.
     */
    Instant expiresAt;

    /**
     * When this challenge was created.
     */
    Instant createdAt;

    /**
     * Creates a DNS TXT verification challenge.
     */
    public static VerificationChallenge forDnsTxt(String domain, String token, Instant expiresAt) {
        String instructions = String.format(
                "Add a TXT record to _bottin.%s with value: bottin-verify=%s",
                domain, token);
        return VerificationChallenge.builder()
                .domain(domain)
                .method(VerificationMethod.DNS_TXT)
                .token(token)
                .instructions(instructions)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Creates a well-known file verification challenge.
     */
    public static VerificationChallenge forWellKnown(String domain, String token, Instant expiresAt) {
        String instructions = String.format(
                "Create a file at https://%s/.well-known/bottin-verification.txt with content: %s",
                domain, token);
        return VerificationChallenge.builder()
                .domain(domain)
                .method(VerificationMethod.WELL_KNOWN_FILE)
                .token(token)
                .instructions(instructions)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Checks if this challenge has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns the full verification value for DNS TXT records.
     */
    public String getDnsTxtValue() {
        return "bottin-verify=" + token;
    }
}
