package xyz.tcheeric.bottin.core.exception;

/**
 * Exception thrown when a provided public key is invalid.
 */
public class InvalidPubkeyException extends BottinException {

    private static final String ERROR_CODE = "INVALID_PUBKEY";
    private static final String DEFAULT_SUGGESTION =
            "Provide a valid 64-character hex-encoded public key or a valid npub (bech32-encoded).";

    public InvalidPubkeyException(String pubkey) {
        super(ERROR_CODE, false,
                "Invalid public key format: " + pubkey,
                DEFAULT_SUGGESTION);
    }

    public InvalidPubkeyException(String pubkey, String reason) {
        super(ERROR_CODE, false,
                "Invalid public key: " + pubkey + ". " + reason,
                DEFAULT_SUGGESTION);
    }

    public InvalidPubkeyException(String pubkey, Throwable cause) {
        super(ERROR_CODE, false,
                "Invalid public key format: " + pubkey,
                DEFAULT_SUGGESTION, cause);
    }
}
