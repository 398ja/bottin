package xyz.tcheeric.bottin.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.core.model.DomainData;
import xyz.tcheeric.bottin.core.model.VerificationMethod;
import xyz.tcheeric.bottin.service.DomainService;
import xyz.tcheeric.bottin.verification.DomainVerificationService;
import xyz.tcheeric.bottin.verification.VerificationChallenge;
import xyz.tcheeric.bottin.verification.VerificationResult;
import xyz.tcheeric.bottin.verification.VerificationStatus;
import xyz.tcheeric.bottin.api.dto.CreateDomainRequest;
import xyz.tcheeric.bottin.api.dto.DomainResponse;
import xyz.tcheeric.bottin.api.dto.InitiateVerificationRequest;
import xyz.tcheeric.bottin.api.dto.PageResponse;
import xyz.tcheeric.bottin.api.dto.VerificationChallengeResponse;
import xyz.tcheeric.bottin.api.dto.VerificationResultResponse;
import xyz.tcheeric.bottin.api.dto.VerificationStatusResponse;

import java.net.URI;
import java.util.List;

/**
 * REST controller for managing domains.
 */
@RestController
@RequestMapping("/api/v1/domains")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domains", description = "Manage registered domains")
public class DomainController {

    private final DomainService domainService;
    private final DomainVerificationService verificationService;

    @GetMapping
    @Operation(summary = "List all domains", description = "Retrieves a paginated list of all registered domains")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domains retrieved successfully")
    })
    public ResponseEntity<PageResponse<DomainResponse>> listDomains(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @Parameter(description = "Filter by verification status")
            @RequestParam(required = false) Boolean verified) {

        log.debug("api_domains_list page={} size={} verified={}",
                pageable.getPageNumber(), pageable.getPageSize(), verified);

        Page<DomainData> page = domainService.findAll(pageable);

        return ResponseEntity.ok(PageResponse.from(page, DomainResponse::from));
    }

    @GetMapping("/all")
    @Operation(summary = "List all domains (unpaginated)", description = "Retrieves all domains as a list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domains retrieved successfully")
    })
    public ResponseEntity<List<DomainResponse>> listAllDomains() {
        log.debug("api_domains_list_all");

        List<DomainResponse> domains = domainService.findAll().stream()
                .map(DomainResponse::from)
                .toList();

        return ResponseEntity.ok(domains);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get domain by ID", description = "Retrieves a specific domain by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain found"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<DomainResponse> getDomain(
            @Parameter(description = "Domain ID") @PathVariable Long id) {

        log.debug("api_domain_get id={}", id);

        return domainService.findById(id)
                .map(data -> ResponseEntity.ok(DomainResponse.from(data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get domain by name", description = "Retrieves a domain by its name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain found"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<DomainResponse> getDomainByName(
            @Parameter(description = "Domain name (e.g., example.com)")
            @PathVariable String name) {

        log.debug("api_domain_get_by_name name={}", name);

        return domainService.findByName(name)
                .map(data -> ResponseEntity.ok(DomainResponse.from(data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Register domain", description = "Registers a new domain")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Domain registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Domain already exists")
    })
    public ResponseEntity<DomainResponse> createDomain(
            @Valid @RequestBody CreateDomainRequest request) {

        log.debug("api_domain_create name={}", request.getName());

        DomainData created = domainService.create(
                request.getName(),
                request.getOwnerPubkey()
        );

        DomainResponse response = DomainResponse.from(created);
        return ResponseEntity
                .created(URI.create("/api/v1/domains/" + created.getId()))
                .body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete domain", description = "Deletes a domain and all its associated records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Domain deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<Void> deleteDomain(
            @Parameter(description = "Domain ID") @PathVariable Long id) {

        log.debug("api_domain_delete id={}", id);

        domainService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verified")
    @Operation(summary = "List verified domains", description = "Retrieves all verified domains")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verified domains retrieved successfully")
    })
    public ResponseEntity<List<DomainResponse>> listVerifiedDomains() {
        log.debug("api_domains_list_verified");

        List<DomainResponse> domains = domainService.findVerified().stream()
                .map(DomainResponse::from)
                .toList();

        return ResponseEntity.ok(domains);
    }

    @GetMapping("/unverified")
    @Operation(summary = "List unverified domains", description = "Retrieves all domains pending verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unverified domains retrieved successfully")
    })
    public ResponseEntity<List<DomainResponse>> listUnverifiedDomains() {
        log.debug("api_domains_list_unverified");

        List<DomainResponse> domains = domainService.findUnverified().stream()
                .map(DomainResponse::from)
                .toList();

        return ResponseEntity.ok(domains);
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Initiate domain verification",
            description = "Starts the verification process for a domain using the specified method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification initiated, challenge returned"),
            @ApiResponse(responseCode = "400", description = "Invalid verification method"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<VerificationChallengeResponse> initiateVerification(
            @Parameter(description = "Domain ID") @PathVariable Long id,
            @Valid @RequestBody InitiateVerificationRequest request) {

        log.info("verification_initiate_via_body domain_id={} method={}", id, request.getMethod());

        VerificationChallenge challenge = verificationService.initiateVerification(id, request.getMethod());

        log.info("verification_challenge_created_via_body domain_id={} method={} already_verified={}",
                id, request.getMethod(), challenge.isAlreadyVerified());

        return ResponseEntity.ok(VerificationChallengeResponse.from(challenge));
    }

    @GetMapping("/{id}/verification")
    @Operation(summary = "Get verification status",
            description = "Retrieves the current verification status for a domain")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification status retrieved"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<VerificationStatusResponse> getVerificationStatus(
            @Parameter(description = "Domain ID") @PathVariable Long id) {

        log.debug("verification_status_get domain_id={}", id);

        VerificationStatus status = verificationService.getVerificationStatus(id);

        return ResponseEntity.ok(VerificationStatusResponse.from(status));
    }

    @PostMapping("/{id}/verify/attempt")
    @Operation(summary = "Attempt domain verification",
            description = "Attempts to verify the domain by checking if the challenge has been completed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification attempt completed"),
            @ApiResponse(responseCode = "400", description = "No pending verification or invalid state"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<VerificationResultResponse> attemptVerification(
            @Parameter(description = "Domain ID") @PathVariable Long id) {

        log.info("verification_attempt domain_id={}", id);

        VerificationResult result = verificationService.attemptVerification(id);

        log.info("verification_result domain_id={} success={} method={} message={}",
                id, result.isSuccess(), result.getMethod(), result.getMessage());

        return ResponseEntity.ok(VerificationResultResponse.from(result));
    }

    @PostMapping("/{id}/verify/{method}")
    @Operation(summary = "Initiate verification with specific method",
            description = "Starts the verification process using the method specified in the path")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification initiated, challenge returned"),
            @ApiResponse(responseCode = "400", description = "Invalid verification method"),
            @ApiResponse(responseCode = "404", description = "Domain not found")
    })
    public ResponseEntity<VerificationChallengeResponse> initiateVerificationWithMethod(
            @Parameter(description = "Domain ID") @PathVariable Long id,
            @Parameter(description = "Verification method (DNS_TXT or WELL_KNOWN_FILE)")
            @PathVariable("method") String methodStr) {

        log.info("verification_initiate_via_path domain_id={} method={}", id, methodStr);

        VerificationMethod method;
        try {
            method = VerificationMethod.valueOf(methodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("verification_invalid_method_in_path domain_id={} method={}", id, methodStr);
            return ResponseEntity.badRequest().build();
        }

        VerificationChallenge challenge = verificationService.initiateVerification(id, method);

        log.info("verification_challenge_created_via_path domain_id={} method={} already_verified={}",
                id, method, challenge.isAlreadyVerified());

        return ResponseEntity.ok(VerificationChallengeResponse.from(challenge));
    }
}
