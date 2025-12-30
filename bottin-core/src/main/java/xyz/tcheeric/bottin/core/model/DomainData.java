package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable value object representing a domain registered in Bottin.
 * This is the core domain model used throughout the application.
 */
@Value
@Builder(toBuilder = true)
public class DomainData {

    /**
     * Unique identifier for the domain.
     */
    Long id;

    /**
     * The domain name (e.g., "example.com").
     */
    String name;

    /**
     * Whether the domain has been verified.
     */
    boolean verified;

    /**
     * The verification token (null if verified or not yet generated).
     */
    String verificationToken;

    /**
     * The method used or to be used for verification.
     */
    VerificationMethod verificationMethod;

    /**
     * When the domain was verified (null if not verified).
     */
    Instant verifiedAt;

    /**
     * The public key of the domain owner (optional).
     */
    String ownerPubkey;

    /**
     * When the domain was registered.
     */
    Instant createdAt;

    /**
     * When the domain was last updated.
     */
    Instant updatedAt;

    /**
     * Number of NIP-05 records under this domain.
     */
    int recordCount;

    /**
     * Creates a new unverified domain.
     */
    public static DomainData createNew(String name, String ownerPubkey) {
        Instant now = Instant.now();
        return DomainData.builder()
                .name(name.toLowerCase())
                .verified(false)
                .ownerPubkey(ownerPubkey)
                .createdAt(now)
                .updatedAt(now)
                .recordCount(0)
                .build();
    }

    /**
     * Creates a verified domain.
     */
    public static DomainData createVerified(String name, String ownerPubkey) {
        Instant now = Instant.now();
        return DomainData.builder()
                .name(name.toLowerCase())
                .verified(true)
                .verifiedAt(now)
                .ownerPubkey(ownerPubkey)
                .createdAt(now)
                .updatedAt(now)
                .recordCount(0)
                .build();
    }

    /**
     * Returns this domain with verification set.
     */
    public DomainData withVerification(VerificationMethod method, String token) {
        return this.toBuilder()
                .verificationMethod(method)
                .verificationToken(token)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns this domain marked as verified.
     */
    public DomainData markVerified() {
        return this.toBuilder()
                .verified(true)
                .verifiedAt(Instant.now())
                .verificationToken(null)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Checks if this domain has a pending verification challenge.
     */
    public boolean hasPendingVerification() {
        return !verified && verificationToken != null;
    }
}
