package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when attempting to create a NIP-05 record that already exists.
 */
public class Nip05RecordExistsException extends BottinException {

    private static final String ERROR_CODE = "NIP05_RECORD_EXISTS";
    private static final String DEFAULT_SUGGESTION =
            "Use a different username or update the existing record instead of creating a new one.";

    public Nip05RecordExistsException(String nip05) {
        super(ERROR_CODE, false,
                "NIP-05 record already exists: " + nip05,
                DEFAULT_SUGGESTION);
    }

    public Nip05RecordExistsException(String username, String domain) {
        super(ERROR_CODE, false,
                "NIP-05 record already exists: " + username + "@" + domain,
                DEFAULT_SUGGESTION);
    }
}
