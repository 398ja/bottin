package xyz.tcheeric.bottin.verification;

import lombok.Builder;
import lombok.Value;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.time.Instant;

/**
 * Current verification status of a domain.
 */
@Value
@Builder
public class VerificationStatus {

    /**
     * The domain ID.
     */
    Long domainId;

    /**
     * The domain name.
     */
    String domainName;

    /**
     * Whether the domain is verified.
     */
    boolean verified;

    /**
     * When the domain was verified.
     */
    Instant verifiedAt;

    /**
     * The verification method used or configured.
     */
    VerificationMethod method;

    /**
     * Whether there is a pending verification challenge.
     */
    boolean hasPendingVerification;
}
