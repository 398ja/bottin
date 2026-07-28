package xyz.tcheeric.bottin.client.dto;

import java.util.List;

/**
 * Handle registration submitted at the end of onboarding. The pubkey is not part
 * of the request: it is taken from the caller's NAP session so a handle can only
 * be claimed for the key that signed in.
 */
public record RegistrationRequest(String username, List<String> relays) {
}
