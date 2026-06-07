package com.crystal.msintake.controller;

import com.crystal.msintake.model.dto.ErrorResponse;
import com.crystal.msintake.model.dto.ScanRequest;
import com.crystal.msintake.model.dto.ScanResponse;
import com.crystal.msintake.service.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/scans")
@Tag(name = "Scans", description = "Operations for initiating and tracking security scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    @Operation(
            summary = "Initiate a new security scan",
            description = "Accepts a scan request, validates it, and enqueues it on the scan-jobs Kafka topic",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Scan job accepted",
                    content = @Content(schema = @Schema(implementation = ScanResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ScanResponse> initiateScan(@Valid @RequestBody ScanRequest request) {
        log.info("Received scan request targetType={} requestedBy={}",
                request.getTargetType(), request.getRequestedBy());

        ScanResponse response = scanService.initiateScan(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}/status")
    @Operation(
            summary = "Get scan job status",
            description = "Returns the current status of a scan job. Note: ms-intake is stateless; status reflects acceptance only.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Job status retrieved",
                    content = @Content(schema = @Schema(implementation = ScanResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid job ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ScanResponse> getScanStatus(
            @Parameter(description = "UUID of the scan job", required = true)
            @PathVariable String jobId) {

        log.info("Status request for jobId={}", jobId);
        ScanResponse response = scanService.getScanStatus(jobId);
        return ResponseEntity.ok(response);
    }
}
