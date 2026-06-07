package com.crystal.msresults.service;

import com.crystal.msresults.exception.ResourceNotFoundException;
import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultMessage;
import com.crystal.msresults.model.dto.ScanResultResponse;
import com.crystal.msresults.model.entity.ScanIssue;
import com.crystal.msresults.model.entity.ScanResult;
import com.crystal.msresults.repository.ScanIssueRepository;
import com.crystal.msresults.repository.ScanResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScanResultServiceImpl implements ScanResultService {

    private static final Logger log = LoggerFactory.getLogger(ScanResultServiceImpl.class);

    private final ScanResultRepository scanResultRepository;
    private final ScanIssueRepository scanIssueRepository;

    public ScanResultServiceImpl(ScanResultRepository scanResultRepository,
                                  ScanIssueRepository scanIssueRepository) {
        this.scanResultRepository = scanResultRepository;
        this.scanIssueRepository = scanIssueRepository;
    }

    @Override
    @Transactional
    public void persistResult(ScanResultMessage message) {
        if (scanResultRepository.existsByJobId(message.getJobId())) {
            log.info("Skipping duplicate scan result for jobId={}", message.getJobId());
            return;
        }

        ScanResult scanResult = new ScanResult();
        scanResult.setJobId(message.getJobId());
        scanResult.setCompletedAt(message.getCompletedAt());
        scanResult.setStatus(message.getStatus());

        if (message.getIssues() != null) {
            for (IssueDto issueDto : message.getIssues()) {
                ScanIssue issue = new ScanIssue();
                issue.setSeverity(issueDto.getSeverity());
                issue.setType(issueDto.getType());
                issue.setLocation(issueDto.getLocation());
                issue.setDescription(issueDto.getDescription());
                scanResult.addIssue(issue);
            }
        }

        scanResultRepository.save(scanResult);
        log.info("Persisted scan result for jobId={} with {} issues",
                message.getJobId(),
                message.getIssues() != null ? message.getIssues().size() : 0);
    }

    @Override
    @Transactional(readOnly = true)
    public ScanResultResponse getResultByJobId(String jobId) {
        ScanResult scanResult = scanResultRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ScanResult", "jobId", jobId));
        return mapToResponse(scanResult, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ScanResultResponse> getAllResults(Pageable pageable) {
        return scanResultRepository.findAll(pageable)
                .map(result -> mapToResponse(result, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueDto> getIssuesByJobId(String jobId) {
        if (!scanResultRepository.existsByJobId(jobId)) {
            throw new ResourceNotFoundException("ScanResult", "jobId", jobId);
        }
        return scanIssueRepository.findByScanResultJobId(jobId).stream()
                .map(this::mapIssueToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getSeverityStats() {
        List<Object[]> results = scanIssueRepository.countBySeverity();
        Map<String, Long> stats = new HashMap<>();
        for (Object[] row : results) {
            stats.put((String) row[0], (Long) row[1]);
        }
        return stats;
    }

    private ScanResultResponse mapToResponse(ScanResult scanResult, boolean includeIssues) {
        ScanResultResponse.ScanResultResponseBuilder builder = ScanResultResponse.builder()
                .id(scanResult.getId())
                .jobId(scanResult.getJobId())
                .completedAt(scanResult.getCompletedAt())
                .status(scanResult.getStatus())
                .createdAt(scanResult.getCreatedAt());

        if (includeIssues) {
            builder.issues(scanResult.getIssues().stream()
                    .map(this::mapIssueToDto)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private IssueDto mapIssueToDto(ScanIssue issue) {
        return IssueDto.builder()
                .id(issue.getId())
                .severity(issue.getSeverity())
                .type(issue.getType())
                .location(issue.getLocation())
                .description(issue.getDescription())
                .build();
    }
}
