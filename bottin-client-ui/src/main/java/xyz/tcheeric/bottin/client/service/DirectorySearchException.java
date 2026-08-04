package xyz.tcheeric.bottin.client.service;

/**
 * Raised when the bottin directory could not be searched.
 *
 * <p>Deliberately not folded into an empty result. "Nobody matches" is a claim
 * about the directory's contents, and this client may only make it when the
 * directory actually answered. Returning an empty list from an unreachable
 * directory would tell a searcher that the person they are looking for is not
 * registered, which is a different and possibly false statement.
 */
public class DirectorySearchException extends RuntimeException {

    public DirectorySearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
