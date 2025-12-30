package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when a requested domain is not found in the registry.
 */
public class DomainNotFoundException extends BottinException {

    private static final String ERROR_CODE = "DOMAIN_NOT_FOUND";
    private static final String DEFAULT_SUGGESTION =
            "Verify the domain name is correct and has been registered in Bottin.";

    public DomainNotFoundException(String domain) {
        super(ERROR_CODE, false,
                "Domain not found: " + domain,
                DEFAULT_SUGGESTION);
    }

    public DomainNotFoundException(String domain, Throwable cause) {
        super(ERROR_CODE, false,
                "Domain not found: " + domain,
                DEFAULT_SUGGESTION, cause);
    }
}
