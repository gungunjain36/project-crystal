package com.crystal.msresults.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResultResponse {
    private UUID id;
    private String jobId;
    private Instant completedAt;
    private String status;
    private Instant createdAt;
    private List<IssueDto> issues;
}
