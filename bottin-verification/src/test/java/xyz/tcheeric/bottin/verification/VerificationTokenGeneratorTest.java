package xyz.tcheeric.bottin.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VerificationTokenGenerator.
 * Tests token generation, format, and uniqueness.
 */
class VerificationTokenGeneratorTest {

    private VerificationTokenGenerator tokenGenerator;

    @BeforeEach
    void setUp() {
        tokenGenerator = new VerificationTokenGenerator();
    }

    /**
     * Tests that generated tokens have the expected length of 32 characters.
     */
    @Test
    void shouldGenerateTokenWithCorrectLength() {
        // Arrange: Token generator is ready

        // Act: Generate a token
        String token = tokenGenerator.generateToken();

        // Assert: Token has 32 characters
        assertThat(token).hasSize(32);
    }

    /**
     * Tests that tokens contain only valid alphanumeric characters (lowercase letters and digits).
     */
    @Test
    void shouldGenerateTokenWithValidCharacters() {
        // Arrange: Token generator is ready

        // Act: Generate a token
        String token = tokenGenerator.generateToken();

        // Assert: Token contains only lowercase letters and digits
        assertThat(token).matches("^[a-z0-9]+$");
    }

    /**
     * Tests that consecutive token generations produce unique tokens.
     */
    @Test
    void shouldGenerateUniqueTokens() {
        // Arrange: Generate multiple tokens
        Set<String> tokens = new HashSet<>();
        int tokenCount = 1000;

        // Act: Generate 1000 tokens
        for (int i = 0; i < tokenCount; i++) {
            tokens.add(tokenGenerator.generateToken());
        }

        // Assert: All tokens are unique
        assertThat(tokens).hasSize(tokenCount);
    }

    /**
     * Tests that token is not null.
     */
    @Test
    void shouldNeverGenerateNullToken() {
        // Arrange: Token generator is ready

        // Act: Generate a token
        String token = tokenGenerator.generateToken();

        // Assert: Token is not null
        assertThat(token).isNotNull();
    }

    /**
     * Tests that token is not empty.
     */
    @Test
    void shouldNeverGenerateEmptyToken() {
        // Arrange: Token generator is ready

        // Act: Generate a token
        String token = tokenGenerator.generateToken();

        // Assert: Token is not empty
        assertThat(token).isNotEmpty();
    }
}
