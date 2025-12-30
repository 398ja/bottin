package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when external NIP-05 verification fails.
 */
public class ExternalVerificationFailedException extends BottinException {

    private static final String ERROR_CODE = "EXTERNAL_VERIFICATION_FAILED";
    private static final String DEFAULT_SUGGESTION =
            "The remote server may be temporarily unavailable. Retry later or verify the NIP-05 identifier is correct.";

    public ExternalVerificationFailedException(String nip05, String reason) {
        super(ERROR_CODE, true,
                "External NIP-05 verification failed for " + nip05 + ": " + reason,
                DEFAULT_SUGGESTION);
    }

    public ExternalVerificationFailedException(String nip05, Throwable cause) {
        super(ERROR_CODE, true,
                "External NIP-05 verification failed for " + nip05 + ": " + cause.getMessage(),
                DEFAULT_SUGGESTION, cause);
    }

    public static ExternalVerificationFailedException notFound(String nip05) {
        return new ExternalVerificationFailedException(nip05,
                "Remote server returned 404 - NIP-05 identifier not found");
    }

    public static ExternalVerificationFailedException invalidResponse(String nip05) {
        return new ExternalVerificationFailedException(nip05,
                "Remote server returned invalid JSON response");
    }

    public static ExternalVerificationFailedException timeout(String nip05, String domain) {
        return new ExternalVerificationFailedException(nip05,
                "Connection to " + domain + " timed out");
    }
}
