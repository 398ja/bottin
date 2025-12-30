package xyz.tcheeric.bottin.verification;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates secure random verification tokens for domain ownership verification.
 *
 * <p>Tokens are 32 characters long, using a URL-safe alphabet (lowercase letters and digits).
 */
@Component
public class VerificationTokenGenerator {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TOKEN_LENGTH = 32;

    private final SecureRandom secureRandom;

    public VerificationTokenGenerator() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generates a new verification token.
     *
     * @return a 32-character random token
     */
    public String generateToken() {
        StringBuilder token = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            int index = secureRandom.nextInt(ALPHABET.length());
            token.append(ALPHABET.charAt(index));
        }
        return token.toString();
    }

    /**
     * Validates the format of a verification token.
     *
     * @param token the token to validate
     * @return true if the token has valid format
     */
    public boolean isValidTokenFormat(String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (char c : token.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
