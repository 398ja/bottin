package xyz.tcheeric.bottin.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.bottin.core.exception.DomainNotFoundException;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.persistence.entity.DomainEntity;
import xyz.tcheeric.bottin.persistence.repository.DomainRepository;
import xyz.tcheeric.bottin.persistence.repository.DomainVerificationLogRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DomainVerificationService.
 * Tests verification orchestration logic with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class DomainVerificationServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private DomainVerificationLogRepository verificationLogRepository;

    @Mock
    private VerificationTokenGenerator tokenGenerator;

    @Mock
    private DnsVerificationService dnsVerificationService;

    @Mock
    private WellKnownVerificationService wellKnownVerificationService;

    @InjectMocks
    private DomainVerificationService verificationService;

    private DomainEntity testDomain;

    @BeforeEach
    void setUp() {
        testDomain = DomainEntity.builder()
                .id(1L)
                .name("example.com")
                .verified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Tests that initiating verification for a non-existent domain throws exception.
     */
    @Test
    void shouldThrowExceptionWhenDomainNotFound() {
        // Arrange: Domain does not exist
        when(domainRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert: Exception is thrown
        assertThatThrownBy(() -> verificationService.initiateVerification(1L, VerificationMethod.DNS_TXT))
                .isInstanceOf(DomainNotFoundException.class);
    }

    /**
     * Tests that initiating verification for an already verified domain returns alreadyVerified challenge.
     */
    @Test
    void shouldReturnAlreadyVerifiedWhenDomainIsVerified() {
        // Arrange: Domain is already verified
        testDomain.setVerified(true);
        testDomain.setVerifiedAt(Instant.now());
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Initiate verification
        VerificationChallenge challenge = verificationService.initiateVerification(1L, VerificationMethod.DNS_TXT);

        // Assert: Returns already verified challenge
        assertThat(challenge.isAlreadyVerified()).isTrue();
        verify(tokenGenerator, never()).generateToken();
    }

    /**
     * Tests that initiating DNS verification generates token and returns instructions.
     */
    @Test
    void shouldInitiateDnsVerification() {
        // Arrange: Unverified domain
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(tokenGenerator.generateToken()).thenReturn("generated-token");
        when(dnsVerificationService.getSetupInstructions("example.com", "generated-token"))
                .thenReturn("DNS instructions");

        // Act: Initiate DNS verification
        VerificationChallenge challenge = verificationService.initiateVerification(1L, VerificationMethod.DNS_TXT);

        // Assert: Challenge contains correct data
        assertThat(challenge.getDomainId()).isEqualTo(1L);
        assertThat(challenge.getDomainName()).isEqualTo("example.com");
        assertThat(challenge.getMethod()).isEqualTo(VerificationMethod.DNS_TXT);
        assertThat(challenge.getToken()).isEqualTo("generated-token");
        assertThat(challenge.getInstructions()).isEqualTo("DNS instructions");
        assertThat(challenge.isAlreadyVerified()).isFalse();

        // Verify domain was updated
        verify(domainRepository).save(testDomain);
        assertThat(testDomain.getVerificationToken()).isEqualTo("generated-token");
        assertThat(testDomain.getVerificationMethod()).isEqualTo(VerificationMethod.DNS_TXT);
    }

    /**
     * Tests that initiating well-known file verification works correctly.
     */
    @Test
    void shouldInitiateWellKnownVerification() {
        // Arrange: Unverified domain
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(tokenGenerator.generateToken()).thenReturn("generated-token");
        when(wellKnownVerificationService.getSetupInstructions("example.com", "generated-token"))
                .thenReturn("Well-known instructions");

        // Act: Initiate well-known verification
        VerificationChallenge challenge = verificationService.initiateVerification(1L, VerificationMethod.WELL_KNOWN_FILE);

        // Assert: Method is correct
        assertThat(challenge.getMethod()).isEqualTo(VerificationMethod.WELL_KNOWN_FILE);
        assertThat(challenge.getInstructions()).isEqualTo("Well-known instructions");
    }

    /**
     * Tests attempting verification when no verification has been initiated.
     */
    @Test
    void shouldFailWhenNoVerificationInitiated() {
        // Arrange: Domain without verification token
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Attempt verification
        VerificationResult result = verificationService.attemptVerification(1L);

        // Assert: Fails with appropriate message
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("not been initiated");
    }

    /**
     * Tests successful DNS verification updates domain state.
     */
    @Test
    void shouldMarkDomainAsVerifiedOnDnsSuccess() {
        // Arrange: Domain with pending DNS verification
        testDomain.setVerificationToken("test-token");
        testDomain.setVerificationMethod(VerificationMethod.DNS_TXT);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(dnsVerificationService.verify("example.com", "test-token"))
                .thenReturn(VerificationResult.success(VerificationMethod.DNS_TXT, "Success"));

        // Act: Attempt verification
        VerificationResult result = verificationService.attemptVerification(1L);

        // Assert: Domain is marked as verified
        assertThat(result.isSuccess()).isTrue();
        assertThat(testDomain.isVerified()).isTrue();
        assertThat(testDomain.getVerifiedAt()).isNotNull();
        assertThat(testDomain.getVerificationToken()).isNull();
        verify(domainRepository).save(testDomain);
        verify(verificationLogRepository).save(any());
    }

    /**
     * Tests successful well-known verification updates domain state.
     */
    @Test
    void shouldMarkDomainAsVerifiedOnWellKnownSuccess() {
        // Arrange: Domain with pending well-known verification
        testDomain.setVerificationToken("test-token");
        testDomain.setVerificationMethod(VerificationMethod.WELL_KNOWN_FILE);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(wellKnownVerificationService.verify("example.com", "test-token"))
                .thenReturn(VerificationResult.success(VerificationMethod.WELL_KNOWN_FILE, "Success"));

        // Act: Attempt verification
        VerificationResult result = verificationService.attemptVerification(1L);

        // Assert: Domain is marked as verified
        assertThat(result.isSuccess()).isTrue();
        assertThat(testDomain.isVerified()).isTrue();
    }

    /**
     * Tests failed verification does not change domain verified status.
     */
    @Test
    void shouldNotUpdateDomainOnVerificationFailure() {
        // Arrange: Domain with pending verification
        testDomain.setVerificationToken("test-token");
        testDomain.setVerificationMethod(VerificationMethod.DNS_TXT);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));
        when(dnsVerificationService.verify("example.com", "test-token"))
                .thenReturn(VerificationResult.failure(VerificationMethod.DNS_TXT, "No record found"));

        // Act: Attempt verification
        VerificationResult result = verificationService.attemptVerification(1L);

        // Assert: Domain remains unverified
        assertThat(result.isSuccess()).isFalse();
        assertThat(testDomain.isVerified()).isFalse();
        assertThat(testDomain.getVerificationToken()).isEqualTo("test-token");
        verify(verificationLogRepository).save(any());
    }

    /**
     * Tests getting verification status for an unverified domain.
     */
    @Test
    void shouldReturnStatusForUnverifiedDomain() {
        // Arrange: Unverified domain
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Get status
        VerificationStatus status = verificationService.getVerificationStatus(1L);

        // Assert: Status reflects unverified state
        assertThat(status.getDomainId()).isEqualTo(1L);
        assertThat(status.getDomainName()).isEqualTo("example.com");
        assertThat(status.isVerified()).isFalse();
        assertThat(status.isHasPendingVerification()).isFalse();
    }

    /**
     * Tests getting verification status for a domain with pending verification.
     */
    @Test
    void shouldReturnStatusWithPendingVerification() {
        // Arrange: Domain with pending verification
        testDomain.setVerificationToken("test-token");
        testDomain.setVerificationMethod(VerificationMethod.DNS_TXT);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Get status
        VerificationStatus status = verificationService.getVerificationStatus(1L);

        // Assert: Status shows pending verification
        assertThat(status.isVerified()).isFalse();
        assertThat(status.isHasPendingVerification()).isTrue();
        assertThat(status.getMethod()).isEqualTo(VerificationMethod.DNS_TXT);
    }

    /**
     * Tests getting verification status for a verified domain.
     */
    @Test
    void shouldReturnStatusForVerifiedDomain() {
        // Arrange: Verified domain
        Instant verifiedAt = Instant.now();
        testDomain.setVerified(true);
        testDomain.setVerifiedAt(verifiedAt);
        testDomain.setVerificationMethod(VerificationMethod.WELL_KNOWN_FILE);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Get status
        VerificationStatus status = verificationService.getVerificationStatus(1L);

        // Assert: Status reflects verified state
        assertThat(status.isVerified()).isTrue();
        assertThat(status.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(status.getMethod()).isEqualTo(VerificationMethod.WELL_KNOWN_FILE);
    }

    /**
     * Tests that attempting verification for already verified domain returns success.
     */
    @Test
    void shouldReturnSuccessForAlreadyVerifiedDomain() {
        // Arrange: Already verified domain
        testDomain.setVerified(true);
        testDomain.setVerificationMethod(VerificationMethod.DNS_TXT);
        when(domainRepository.findById(1L)).thenReturn(Optional.of(testDomain));

        // Act: Attempt verification
        VerificationResult result = verificationService.attemptVerification(1L);

        // Assert: Returns success without checking
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("already verified");
        verify(dnsVerificationService, never()).verify(anyString(), anyString());
    }
}
