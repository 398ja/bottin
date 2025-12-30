package xyz.tcheeric.bottin.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import xyz.tcheeric.bottin.admin.config.AdminSecurityConfig;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.verification.DomainVerificationService;
import xyz.tcheeric.bottin.verification.VerificationChallenge;
import xyz.tcheeric.bottin.verification.VerificationResult;
import xyz.tcheeric.bottin.verification.VerificationStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Unit tests for AdminDomainsController.
 * Tests domain management and verification operations.
 */
@WebMvcTest(AdminDomainsController.class)
@Import(AdminSecurityConfig.class)
class AdminDomainsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DomainService domainService;

    @MockBean
    private DomainVerificationService verificationService;

    /**
     * Tests that unauthenticated users are redirected to login.
     */
    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/domains"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/admin/login"));
    }

    /**
     * Tests that authenticated admin can list domains.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDisplayDomainsListWhenAuthenticated() throws Exception {
        // Arrange
        when(domainService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/admin/domains"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/domains"))
                .andExpect(model().attributeExists("domains"))
                .andExpect(model().attributeExists("createForm"))
                .andExpect(model().attributeExists("verificationMethods"));
    }

    /**
     * Tests viewing a single domain by ID.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDisplayDomainDetailWhenFound() throws Exception {
        // Arrange
        DomainData domain = createTestDomain(1L, "example.com", false);
        VerificationStatus status = createTestStatus(1L, "example.com", false);

        when(domainService.findById(1L)).thenReturn(Optional.of(domain));
        when(verificationService.getVerificationStatus(1L)).thenReturn(status);

        // Act & Assert
        mockMvc.perform(get("/admin/domains/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/domain-detail"))
                .andExpect(model().attributeExists("domain"))
                .andExpect(model().attributeExists("verificationStatus"))
                .andExpect(model().attributeExists("verificationMethods"));
    }

    /**
     * Tests redirect when domain not found.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectWhenDomainNotFound() throws Exception {
        // Arrange
        when(domainService.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/admin/domains/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains?error=notfound"));
    }

    /**
     * Tests creating a new domain successfully.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateDomainSuccessfully() throws Exception {
        // Arrange
        DomainData created = createTestDomain(1L, "newdomain.com", false);
        when(domainService.create(anyString(), any()))
                .thenReturn(created);

        // Act & Assert
        mockMvc.perform(post("/admin/domains")
                        .with(csrf())
                        .param("name", "newdomain.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests that validation errors redirect back with error message.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRedirectWithErrorOnValidationFailure() throws Exception {
        // Act & Assert: invalid domain name
        mockMvc.perform(post("/admin/domains")
                        .with(csrf())
                        .param("name", "-invalid-"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains"))
                .andExpect(flash().attributeExists("error"));
    }

    /**
     * Tests initiating domain verification.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldInitiateVerificationSuccessfully() throws Exception {
        // Arrange
        VerificationChallenge challenge = VerificationChallenge.builder()
                .domainId(1L)
                .domainName("example.com")
                .method(VerificationMethod.DNS_TXT)
                .token("test-token")
                .instructions("Add TXT record")
                .createdAt(Instant.now())
                .alreadyVerified(false)
                .build();

        when(verificationService.initiateVerification(eq(1L), eq(VerificationMethod.DNS_TXT)))
                .thenReturn(challenge);

        // Act & Assert
        mockMvc.perform(post("/admin/domains/1/verify")
                        .with(csrf())
                        .param("method", "DNS_TXT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains/1"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests verification when domain is already verified.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldShowInfoWhenDomainAlreadyVerified() throws Exception {
        // Arrange
        VerificationChallenge challenge = VerificationChallenge.alreadyVerified("example.com");
        when(verificationService.initiateVerification(eq(1L), eq(VerificationMethod.DNS_TXT)))
                .thenReturn(challenge);

        // Act & Assert
        mockMvc.perform(post("/admin/domains/1/verify")
                        .with(csrf())
                        .param("method", "DNS_TXT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains/1"))
                .andExpect(flash().attributeExists("info"));
    }

    /**
     * Tests attempting verification successfully.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAttemptVerificationSuccessfully() throws Exception {
        // Arrange
        VerificationResult result = VerificationResult.success(VerificationMethod.DNS_TXT, "Verified!");
        when(verificationService.attemptVerification(1L)).thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/admin/domains/1/verify/attempt")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains/1"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests verification failure.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldShowErrorWhenVerificationFails() throws Exception {
        // Arrange
        VerificationResult result = VerificationResult.failure(VerificationMethod.DNS_TXT, "Token not found");
        when(verificationService.attemptVerification(1L)).thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/admin/domains/1/verify/attempt")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains/1"))
                .andExpect(flash().attributeExists("error"));
    }

    /**
     * Tests deleting a domain.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldDeleteDomainSuccessfully() throws Exception {
        // Arrange
        doNothing().when(domainService).delete(1L);

        // Act & Assert
        mockMvc.perform(post("/admin/domains/1/delete")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/domains"))
                .andExpect(flash().attributeExists("success"));
    }

    /**
     * Tests that CSRF token is required for POST requests.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRequireCsrfTokenForPostRequests() throws Exception {
        // Act & Assert: POST without CSRF token
        mockMvc.perform(post("/admin/domains/1/delete"))
                .andExpect(status().isForbidden());
    }

    private DomainData createTestDomain(Long id, String name, boolean verified) {
        return DomainData.builder()
                .id(id)
                .name(name)
                .verified(verified)
                .verifiedAt(verified ? Instant.now() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .recordCount(0)
                .build();
    }

    private VerificationStatus createTestStatus(Long domainId, String domainName, boolean verified) {
        return VerificationStatus.builder()
                .domainId(domainId)
                .domainName(domainName)
                .verified(verified)
                .verifiedAt(verified ? Instant.now() : null)
                .hasPendingVerification(false)
                .build();
    }
}
