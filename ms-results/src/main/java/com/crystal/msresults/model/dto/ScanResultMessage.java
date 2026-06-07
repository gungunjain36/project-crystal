package com.crystal.msresults.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResultMessage {
    private String jobId;
    private Instant completedAt;
    private String status;
    private List<IssueDto> issues;
}
