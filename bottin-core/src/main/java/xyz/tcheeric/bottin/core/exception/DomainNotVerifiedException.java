package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when attempting to use a domain that has not been verified.
 */
public class DomainNotVerifiedException extends BottinException {

    private static final String ERROR_CODE = "DOMAIN_NOT_VERIFIED";
    private static final String DEFAULT_SUGGESTION =
            "Complete domain verification using DNS TXT record or well-known file method before creating NIP-05 records.";

    public DomainNotVerifiedException(String domain) {
        super(ERROR_CODE, false,
                "Domain not verified: " + domain,
                DEFAULT_SUGGESTION);
    }

    public DomainNotVerifiedException(String domain, String additionalInfo) {
        super(ERROR_CODE, false,
                "Domain not verified: " + domain + ". " + additionalInfo,
                DEFAULT_SUGGESTION);
    }
}
