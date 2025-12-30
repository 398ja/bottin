package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when a requested NIP-05 record is not found.
 */
public class Nip05RecordNotFoundException extends BottinException {

    private static final String ERROR_CODE = "NIP05_RECORD_NOT_FOUND";
    private static final String DEFAULT_SUGGESTION =
            "Verify the NIP-05 identifier is correct. The record may have been deleted or never created.";

    public Nip05RecordNotFoundException(String nip05) {
        super(ERROR_CODE, false,
                "NIP-05 record not found: " + nip05,
                DEFAULT_SUGGESTION);
    }

    public Nip05RecordNotFoundException(Long id) {
        super(ERROR_CODE, false,
                "NIP-05 record not found with ID: " + id,
                DEFAULT_SUGGESTION);
    }

    public Nip05RecordNotFoundException(String username, String domain) {
        super(ERROR_CODE, false,
                "NIP-05 record not found: " + username + "@" + domain,
                DEFAULT_SUGGESTION);
    }
}
