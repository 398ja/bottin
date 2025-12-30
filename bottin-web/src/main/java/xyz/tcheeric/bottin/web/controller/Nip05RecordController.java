package xyz.tcheeric.bottin.web.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.core.model.Nip05RecordData;
import xyz.tcheeric.bottin.service.Nip05RecordService;
import xyz.tcheeric.bottin.web.dto.CreateNip05RecordRequest;
import xyz.tcheeric.bottin.web.dto.Nip05RecordResponse;
import xyz.tcheeric.bottin.web.dto.PageResponse;
import xyz.tcheeric.bottin.web.dto.UpdateNip05RecordRequest;

import java.net.URI;

/**
 * REST controller for managing NIP-05 records.
 */
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "NIP-05 Records", description = "Manage NIP-05 identity records")
public class Nip05RecordController {

    private final Nip05RecordService nip05RecordService;

    @GetMapping
    @Operation(summary = "List all records", description = "Retrieves a paginated list of all NIP-05 records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Records retrieved successfully")
    })
    public ResponseEntity<PageResponse<Nip05RecordResponse>> listRecords(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @Parameter(description = "Filter by username (partial match)")
            @RequestParam(required = false) String username,
            @Parameter(description = "Filter by domain")
            @RequestParam(required = false) String domain) {

        log.debug("api_records_list page={} size={} username={} domain={}",
                pageable.getPageNumber(), pageable.getPageSize(), username, domain);

        Page<Nip05RecordData> page;
        if (username != null && !username.isBlank()) {
            page = nip05RecordService.searchByUsername(username, pageable);
        } else if (domain != null && !domain.isBlank()) {
            page = nip05RecordService.findByDomain(domain, pageable);
        } else {
            page = nip05RecordService.findAll(pageable);
        }

        return ResponseEntity.ok(PageResponse.from(page, Nip05RecordResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get record by ID", description = "Retrieves a specific NIP-05 record by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Record found"),
            @ApiResponse(responseCode = "404", description = "Record not found")
    })
    public ResponseEntity<Nip05RecordResponse> getRecord(
            @Parameter(description = "Record ID") @PathVariable Long id) {

        log.debug("api_record_get id={}", id);

        return nip05RecordService.findById(id)
                .map(data -> ResponseEntity.ok(Nip05RecordResponse.from(data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-nip05/{nip05}")
    @Operation(summary = "Get record by NIP-05", description = "Retrieves a record by its NIP-05 identifier (username@domain)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Record found"),
            @ApiResponse(responseCode = "404", description = "Record not found")
    })
    public ResponseEntity<Nip05RecordResponse> getRecordByNip05(
            @Parameter(description = "NIP-05 identifier (e.g., alice@example.com)")
            @PathVariable String nip05) {

        log.debug("api_record_get_by_nip05 nip05={}", nip05);

        return nip05RecordService.findByNip05(nip05)
                .map(data -> ResponseEntity.ok(Nip05RecordResponse.from(data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create record", description = "Creates a new NIP-05 record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Record created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Record already exists")
    })
    public ResponseEntity<Nip05RecordResponse> createRecord(
            @Valid @RequestBody CreateNip05RecordRequest request) {

        log.debug("api_record_create username={} domain={}", request.getUsername(), request.getDomain());

        Nip05RecordData created = nip05RecordService.create(
                request.getUsername(),
                request.getDomain(),
                request.getPubkey(),
                request.getRelays()
        );

        Nip05RecordResponse response = Nip05RecordResponse.from(created);
        return ResponseEntity
                .created(URI.create("/api/v1/records/" + created.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update record", description = "Updates an existing NIP-05 record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Record updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Record not found")
    })
    public ResponseEntity<Nip05RecordResponse> updateRecord(
            @Parameter(description = "Record ID") @PathVariable Long id,
            @Valid @RequestBody UpdateNip05RecordRequest request) {

        log.debug("api_record_update id={}", id);

        Nip05RecordData updated = nip05RecordService.update(
                id,
                request.getPubkey(),
                request.getRelays(),
                request.getEnabled()
        );

        return ResponseEntity.ok(Nip05RecordResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete record", description = "Deletes an NIP-05 record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Record deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Record not found")
    })
    public ResponseEntity<Void> deleteRecord(
            @Parameter(description = "Record ID") @PathVariable Long id) {

        log.debug("api_record_delete id={}", id);

        nip05RecordService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle record status", description = "Toggles the enabled/disabled status of a record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Record status toggled"),
            @ApiResponse(responseCode = "404", description = "Record not found")
    })
    public ResponseEntity<Nip05RecordResponse> toggleRecord(
            @Parameter(description = "Record ID") @PathVariable Long id) {

        log.debug("api_record_toggle id={}", id);

        Nip05RecordData toggled = nip05RecordService.toggleEnabled(id);
        return ResponseEntity.ok(Nip05RecordResponse.from(toggled));
    }
}
