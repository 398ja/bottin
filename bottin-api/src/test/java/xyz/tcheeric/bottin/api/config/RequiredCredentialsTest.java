package xyz.tcheeric.bottin.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the production startup check for configured credentials. Unset passwords
 * fall back to a random per-restart UUID, which locks out every configured caller;
 * production should say so at startup rather than serve requests nobody can use.
 */
class RequiredCredentialsTest {

    /**
     * Tests that both passwords being configured lets startup proceed.
     */
    @Test
    void shouldStartWhenBothPasswordsAreConfigured() {
        // Given: an environment with both credentials set
        MockEnvironment environment = new MockEnvironment()
                .withProperty("bottin.admin.password", "admin-secret")
                .withProperty("bottin.api.password", "api-secret");

        // When: the check runs
        // Then: startup is not blocked
        assertThatCode(() -> new RequiredCredentials(environment).verifyConfigured())
                .doesNotThrowAnyException();
    }

    /**
     * Tests that a missing API password blocks startup and names the property, so
     * the operator does not have to guess which one was forgotten.
     */
    @Test
    void shouldBlockStartupWhenTheApiPasswordIsMissing() {
        // Given: an environment with only the admin credential set
        MockEnvironment environment = new MockEnvironment()
                .withProperty("bottin.admin.password", "admin-secret");

        // When: the check runs
        // Then: startup fails naming the missing property
        assertThatThrownBy(() -> new RequiredCredentials(environment).verifyConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bottin.api.password")
                .hasMessageNotContaining("bottin.admin.password,");
    }

    /**
     * Tests that a blank password counts as unconfigured; an empty environment
     * variable is a misconfiguration, not a credential.
     */
    @Test
    void shouldTreatABlankPasswordAsUnconfigured() {
        // Given: an environment where both credentials are blank
        MockEnvironment environment = new MockEnvironment()
                .withProperty("bottin.admin.password", "  ")
                .withProperty("bottin.api.password", "");

        // When: the check runs
        // Then: startup fails naming both properties
        assertThatThrownBy(() -> new RequiredCredentials(environment).verifyConfigured())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bottin.admin.password")
                .hasMessageContaining("bottin.api.password");
    }
}
