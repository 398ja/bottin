package xyz.tcheeric.bottin.core.model;

/**
 * Methods available for domain verification.
 */
public enum VerificationMethod {

    /**
     * DNS TXT record verification.
     * User adds a TXT record to _nostr-verification.{domain} with the verification token.
     */
    DNS_TXT("DNS TXT Record", "_nostr-verification.{domain}"),

    /**
     * Well-known file verification.
     * User creates a file at https://{domain}/.well-known/nostr-verification.txt
     */
    WELL_KNOWN_FILE("Well-Known File", "/.well-known/nostr-verification.txt");

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
