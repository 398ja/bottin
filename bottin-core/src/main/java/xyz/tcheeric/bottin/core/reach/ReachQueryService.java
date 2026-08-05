package xyz.tcheeric.bottin.core.reach;

import java.util.Optional;

/**
 * Read-only access to stored profile reach figures.
 *
 * <p>Implementations serve previously calculated figures from durable storage;
 * they MUST NOT perform live calculation in response to a query.
 */
public interface ReachQueryService {

    /**
     * Returns the most recently stored reach for the profile identified by
     * {@code identifier}, regardless of the figure's age.
     *
     * @param identifier the profile's {@code npub} (NIP-19 bech32) or 64-character hex public key
     * @return the stored reach, or {@link Optional#empty()} if none has been calculated
     * @throws xyz.tcheeric.bottin.core.exception.InvalidPubkeyException if the identifier is malformed
     */
    Optional<ProfileReach> findReach(String identifier);
}
