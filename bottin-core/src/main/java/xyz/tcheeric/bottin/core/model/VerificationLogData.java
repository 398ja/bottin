package xyz.tcheeric.bottin.core.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable value object representing a verification log entry.
 * Used for audit trail of NIP-05 verifications.
 */
@Value
@Builder(toBuilder = true)
public class VerificationLogData {

    /**
     * Unique identifier for the log entry.
     */
    Long id;

    /**
     * The NIP-05 identifier that was verified.
     */
    String nip05;

    /**
     * Type of verification performed.
     */
    VerificationType verificationType;

    /**
     * Result of the verification.
     */
    VerificationResultType result;

    /**
     * Error message if verification failed.
     */
    String errorMessage;

    /**
     * Raw response from remote server (for external verifications).
     */
    String remoteResponse;

    /**
     * When the verification was performed.
     */
    Instant verifiedAt;

    /**
     * Creates a successful domain verification log.
     */
    public static VerificationLogData domainSuccess(String domain, VerificationMethod method) {
        return VerificationLogData.builder()
                .nip05(domain)
                .verificationType(VerificationType.DOMAIN)
                .result(VerificationResultType.SUCCESS)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed domain verification log.
     */
    public static VerificationLogData domainFailure(String domain, String errorMessage) {
        return VerificationLogData.builder()
                .nip05(domain)
                .verificationType(VerificationType.DOMAIN)
                .result(VerificationResultType.FAILURE)
                .errorMessage(errorMessage)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a successful external NIP-05 verification log.
     */
    public static VerificationLogData externalSuccess(String nip05, String remoteResponse) {
        return VerificationLogData.builder()
                .nip05(nip05)
                .verificationType(VerificationType.EXTERNAL_NIP05)
                .result(VerificationResultType.SUCCESS)
                .remoteResponse(remoteResponse)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed external NIP-05 verification log.
     */
    public static VerificationLogData externalFailure(String nip05, String errorMessage, String remoteResponse) {
        return VerificationLogData.builder()
                .nip05(nip05)
                .verificationType(VerificationType.EXTERNAL_NIP05)
                .result(VerificationResultType.FAILURE)
                .errorMessage(errorMessage)
                .remoteResponse(remoteResponse)
                .verifiedAt(Instant.now())
                .build();
    }

    /**
     * Type of verification.
     */
    public enum VerificationType {
        DOMAIN,
        EXTERNAL_NIP05
    }

    /**
     * Result type for verification.
     */
    public enum VerificationResultType {
        SUCCESS,
        FAILURE
    }
}
