package xyz.tcheeric.bottin.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.tcheeric.bottin.api.dto.SettingsResponse;
import xyz.tcheeric.bottin.service.SettingsService;

/**
 * REST controller serving the admin-maintained deployment settings.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Settings", description = "Admin-maintained deployment settings")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @Operation(
            summary = "Get the deployment settings",
            description = "Returns the media server and relay topology an administrator maintains. "
                    + "Requires the API role: the values are not secret, but they are not public "
                    + "either, and the client server that consumes them already holds credentials. "
                    + "The rate limit is deliberately not included."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings returned"),
            @ApiResponse(responseCode = "401", description = "Missing or insufficient credentials")
    })
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(SettingsResponse.from(settingsService.find()));
    }
}
