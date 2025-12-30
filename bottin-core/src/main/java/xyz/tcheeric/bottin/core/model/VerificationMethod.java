package xyz.tcheeric.bottin.core.model;

/**
 * Methods available for domain verification.
 */
public enum VerificationMethod {

    /**
     * DNS TXT record verification.
     * User adds a TXT record to _bottin.{domain} with the verification token.
     */
    DNS_TXT("DNS TXT Record", "_bottin.{domain}"),

    /**
     * Well-known file verification.
     * User creates a file at https://{domain}/.well-known/bottin-verification.txt
     */
    WELL_KNOWN_FILE("Well-Known File", "/.well-known/bottin-verification.txt");

    private final String displayName;
    private final String location;

    VerificationMethod(String displayName, String location) {
        this.displayName = displayName;
        this.location = location;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocation() {
        return location;
    }

    public String getLocationForDomain(String domain) {
        return location.replace("{domain}", domain);
    }
}
