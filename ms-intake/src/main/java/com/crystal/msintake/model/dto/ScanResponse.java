package com.crystal.msintake.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned after a scan request is accepted")
public class ScanResponse {

    @Schema(description = "Unique identifier for the scan job", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID jobId;

    @Schema(description = "Timestamp when the scan was requested", example = "2024-01-15T10:30:00Z")
    private Instant requestedAt;

    @Schema(description = "Status of the scan job", example = "accepted")
    private String status;

    @Schema(description = "Human-readable message", example = "Scan job accepted and queued for processing")
    private String message;
}
