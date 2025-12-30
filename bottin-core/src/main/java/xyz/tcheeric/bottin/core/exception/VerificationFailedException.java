package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when domain verification fails.
 */
public class VerificationFailedException extends BottinException {

    private static final String ERROR_CODE = "VERIFICATION_FAILED";
    private static final String DNS_SUGGESTION =
            "Ensure the DNS TXT record '_bottin.{domain}' contains the verification token. DNS propagation may take up to 24 hours.";
    private static final String WELLKNOWN_SUGGESTION =
            "Ensure the file at 'https://{domain}/.well-known/bottin-verification.txt' contains the exact verification token.";

    public VerificationFailedException(String domain, String method, String reason) {
        super(ERROR_CODE, true,
                "Domain verification failed for " + domain + " via " + method + ": " + reason,
                "DNS_TXT".equals(method) ? DNS_SUGGESTION : WELLKNOWN_SUGGESTION);
    }

    public VerificationFailedException(String domain, String method, Throwable cause) {
        super(ERROR_CODE, true,
                "Domain verification failed for " + domain + " via " + method + ": " + cause.getMessage(),
                "DNS_TXT".equals(method) ? DNS_SUGGESTION : WELLKNOWN_SUGGESTION,
                cause);
    }

    public static VerificationFailedException dnsTimeout(String domain) {
        return new VerificationFailedException(domain, "DNS_TXT", "DNS lookup timed out");
    }

    public static VerificationFailedException tokenMismatch(String domain, String method) {
        return new VerificationFailedException(domain, method, "Verification token does not match");
    }
}
