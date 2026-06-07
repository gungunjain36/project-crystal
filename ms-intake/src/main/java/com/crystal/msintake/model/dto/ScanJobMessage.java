package com.crystal.msintake.model.dto;

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
public class ScanJobMessage {

    private UUID jobId;
    private Instant requestedAt;
    private String targetType;
    private String target;
    private String requestedBy;
}
