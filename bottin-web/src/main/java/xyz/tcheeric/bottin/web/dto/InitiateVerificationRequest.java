package xyz.tcheeric.bottin.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

/**
 * Request DTO for initiating domain verification.
 */
@Value
@Builder
@Jacksonized
public class InitiateVerificationRequest {

    @NotNull(message = "Verification method is required")
    VerificationMethod method;
}
