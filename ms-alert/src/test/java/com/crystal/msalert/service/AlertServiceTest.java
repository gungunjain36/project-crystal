package com.crystal.msalert.service;

import com.crystal.msalert.model.dto.IssueDto;
import com.crystal.msalert.model.dto.ScanResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private SlackNotificationService slackNotificationService;

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertServiceImpl(slackNotificationService);
        ReflectionTestUtils.setField(alertService, "alertSeveritiesConfig", "high,critical");
    }

    @Test
    void processResult_withMixedSeverities_onlyAlertsOnHighAndCritical() {
        // Arrange
        IssueDto lowIssue = IssueDto.builder()
                .severity("low").type("XSS").location("file.js:10").description("Low XSS").build();
        IssueDto mediumIssue = IssueDto.builder()
                .severity("medium").type("CSRF").location("file.js:20").description("Medium CSRF").build();
        IssueDto highIssue = IssueDto.builder()
                .severity("high").type("SQLi").location("repo.java:50").description("High SQL injection").build();
        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical").type("RCE").location("cmd.java:99").description("Critical RCE").build();

        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("job-001")
                .completedAt("2025-01-01T00:00:00Z")
                .status("success")
                .issues(Arrays.asList(lowIssue, mediumIssue, highIssue, criticalIssue))
                .build();

        // Act
        int count = alertService.processResult(message);

        // Assert
        assertThat(count).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IssueDto>> issuesCaptor = ArgumentCaptor.forClass(List.class);
        verify(slackNotificationService).sendAlert(eq("job-001"), issuesCaptor.capture());

        List<IssueDto> alertedIssues = issuesCaptor.getValue();
        assertThat(alertedIssues).hasSize(2);
        assertThat(alertedIssues).extracting(IssueDto::getSeverity)
                .containsExactlyInAnyOrder("high", "critical");
    }

    @Test
    void processResult_withAllLowMedium_noAlertSent() {
        // Arrange
        IssueDto lowIssue = IssueDto.builder()
                .severity("low").type("XSS").location("file.js:10").description("Low XSS").build();
        IssueDto mediumIssue = IssueDto.builder()
                .severity("medium").type("CSRF").location("file.js:20").description("Medium CSRF").build();

        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("job-002")
                .completedAt("2025-01-01T00:00:00Z")
                .status("success")
                .issues(Arrays.asList(lowIssue, mediumIssue))
                .build();

        // Act
        int count = alertService.processResult(message);

        // Assert
        assertThat(count).isEqualTo(0);
        verify(slackNotificationService, never()).sendAlert(any(), any());
    }

    @Test
    void processResult_withEmptyIssues_noAlertSent() {
        // Arrange
        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("job-003")
                .completedAt("2025-01-01T00:00:00Z")
                .status("success")
                .issues(Collections.emptyList())
                .build();

        // Act
        int count = alertService.processResult(message);

        // Assert
        assertThat(count).isEqualTo(0);
        verify(slackNotificationService, never()).sendAlert(any(), any());
    }

    @Test
    void processResult_withNullIssues_noAlertSent() {
        // Arrange
        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("job-004")
                .completedAt("2025-01-01T00:00:00Z")
                .status("success")
                .issues(null)
                .build();

        // Act
        int count = alertService.processResult(message);

        // Assert
        assertThat(count).isEqualTo(0);
        verify(slackNotificationService, never()).sendAlert(any(), any());
    }

    @Test
    void processResult_withOnlyCriticalIssues_alertSentWithCriticalOnly() {
        // Arrange
        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical").type("RCE").location("exec.java:5").description("Remote code execution").build();

        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("job-005")
                .completedAt("2025-01-01T00:00:00Z")
                .status("success")
                .issues(Collections.singletonList(criticalIssue))
                .build();

        // Act
        int count = alertService.processResult(message);

        // Assert
        assertThat(count).isEqualTo(1);
        verify(slackNotificationService).sendAlert(eq("job-005"), any());
    }
}
