package com.crystal.msresults.controller;

import com.crystal.msresults.model.dto.ErrorResponse;
import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultResponse;
import com.crystal.msresults.service.ScanResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/results")
@Tag(name = "Scan Results", description = "API for querying static analysis scan results")
public class ScanResultController {

    private final ScanResultService scanResultService;

    public ScanResultController(ScanResultService scanResultService) {
        this.scanResultService = scanResultService;
    }

    @GetMapping
    @Operation(summary = "List all scan results", description = "Returns a paginated list of all scan results")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid API key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<ScanResultResponse>> getAllResults(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(scanResultService.getAllResults(pageable));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get scan result by job ID", description = "Returns full scan result including issues for the given job ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan result found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan result not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ScanResultResponse> getResultByJobId(
            @Parameter(description = "Job ID of the scan", required = true) @PathVariable String jobId) {
        return ResponseEntity.ok(scanResultService.getResultByJobId(jobId));
    }

    @GetMapping("/{jobId}/issues")
    @Operation(summary = "Get issues for a job", description = "Returns the list of issues found for the given job ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Issues retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Scan result not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<IssueDto>> getIssuesByJobId(
            @Parameter(description = "Job ID of the scan", required = true) @PathVariable String jobId) {
        return ResponseEntity.ok(scanResultService.getIssuesByJobId(jobId));
    }

    @GetMapping("/stats/severity")
    @Operation(summary = "Get severity statistics", description = "Returns a count of issues grouped by severity level")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Long>> getSeverityStats() {
        return ResponseEntity.ok(scanResultService.getSeverityStats());
    }
}
