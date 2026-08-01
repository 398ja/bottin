package xyz.tcheeric.bottin.client.service;

import lombok.Getter;

/**
 * Raised when the bottin directory refuses to store a NIP-05 record.
 *
 * <p>Carries the code the browser needs to tell the user what to do next:
 * {@code USERNAME_TAKEN} means pick another handle, {@code DOMAIN_NOT_FOUND}
 * means the operator has not registered the handle suffix yet, and
 * {@code DIRECTORY_UNAVAILABLE} means the call itself failed.
 */
@Getter
public class DirectoryRegistrationException extends RuntimeException {

    public static final String USERNAME_TAKEN = "USERNAME_TAKEN";
    public static final String DOMAIN_NOT_FOUND = "DOMAIN_NOT_FOUND";
    public static final String DIRECTORY_UNAVAILABLE = "DIRECTORY_UNAVAILABLE";

    private final String errorCode;

    public DirectoryRegistrationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
