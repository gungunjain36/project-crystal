package com.crystal.msalert.service;

import com.crystal.msalert.model.dto.IssueDto;
import com.crystal.msalert.model.dto.ScanResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final SlackNotificationService slackNotificationService;

    @Value("${app.alert.severities:high,critical}")
    private String alertSeveritiesConfig;

    @Override
    public int processResult(ScanResultMessage message) {
        if (message == null) {
            log.warn("Received null ScanResultMessage, skipping processing");
            return 0;
        }

        log.info("Processing scan result for jobId={} status={} completedAt={}",
                message.getJobId(), message.getStatus(), message.getCompletedAt());

        List<IssueDto> issues = message.getIssues();
        if (issues == null || issues.isEmpty()) {
            log.debug("No issues found in scan result for jobId={}", message.getJobId());
            return 0;
        }

        Set<String> alertSeverities = Arrays.stream(alertSeveritiesConfig.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<IssueDto> criticalIssues = issues.stream()
                .filter(issue -> issue.getSeverity() != null
                        && alertSeverities.contains(issue.getSeverity().toLowerCase()))
                .collect(Collectors.toList());

        int count = criticalIssues.size();

        if (count > 0) {
            log.warn("Found {} high/critical issue(s) in jobId={}, sending alert", count, message.getJobId());
            slackNotificationService.sendAlert(message.getJobId(), criticalIssues);
        } else {
            log.info("No high/critical issues found in jobId={}, no alert needed", message.getJobId());
        }

        return count;
    }
}
