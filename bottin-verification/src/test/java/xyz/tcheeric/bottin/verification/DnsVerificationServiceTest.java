package xyz.tcheeric.bottin.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DnsVerificationService.
 * Tests instruction generation and basic verification logic.
 * Note: DNS lookup functionality requires integration tests with actual DNS servers.
 */
class DnsVerificationServiceTest {

    private DnsVerificationService dnsVerificationService;

    @BeforeEach
    void setUp() {
        dnsVerificationService = new DnsVerificationService();
    }

    /**
     * Tests that setup instructions contain the domain name.
     */
    @Test
    void shouldGenerateInstructionsWithDomainName() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "testtoken123";

        // Act: Generate instructions
        String instructions = dnsVerificationService.getSetupInstructions(domain, token);

        // Assert: Instructions contain the domain
        assertThat(instructions).contains("example.com");
    }

    /**
     * Tests that setup instructions contain the verification token.
     */
    @Test
    void shouldGenerateInstructionsWithToken() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "testtoken123";

        // Act: Generate instructions
        String instructions = dnsVerificationService.getSetupInstructions(domain, token);

        // Assert: Instructions contain the token
        assertThat(instructions).contains("testtoken123");
    }

    /**
     * Tests that setup instructions specify TXT record type.
     */
    @Test
    void shouldGenerateInstructionsWithTxtRecordType() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "testtoken123";

        // Act: Generate instructions
        String instructions = dnsVerificationService.getSetupInstructions(domain, token);

        // Assert: Instructions specify TXT record
        assertThat(instructions).contains("Type: TXT");
    }

    /**
     * Tests that setup instructions include the bottin-verification prefix.
     */
    @Test
    void shouldGenerateInstructionsWithCorrectPrefix() {
        // Arrange: Domain and token
        String domain = "example.com";
        String token = "testtoken123";

        // Act: Generate instructions
        String instructions = dnsVerificationService.getSetupInstructions(domain, token);

        // Assert: Instructions include the correct prefix for the record name
        assertThat(instructions).contains("_bottin-verification.example.com");
        assertThat(instructions).contains("bottin-verification=testtoken123");
    }

    /**
     * Tests verification returns failure when domain does not have required TXT record.
     * This tests against a known-invalid domain to verify error handling.
     */
    @Test
    void shouldReturnFailureForNonExistentDomain() {
        // Arrange: Non-existent domain
        String domain = "this-domain-does-not-exist-12345.invalid";
        String token = "testtoken123";

        // Act: Attempt verification
        VerificationResult result = dnsVerificationService.verify(domain, token);

        // Assert: Verification fails
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMethod()).isEqualTo(xyz.tcheeric.bottin.core.model.VerificationMethod.DNS_TXT);
    }
}
