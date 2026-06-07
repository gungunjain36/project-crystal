package com.crystal.msalert.service;

import com.crystal.msalert.model.dto.IssueDto;
import com.crystal.msalert.model.dto.SlackAttachment;
import com.crystal.msalert.model.dto.SlackField;
import com.crystal.msalert.model.dto.SlackMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final RestTemplate restTemplate;

    @Value("${app.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${app.slack.enabled:true}")
    private boolean slackEnabled;

    /**
     * Send a Slack alert for high/critical issues found in a scan result.
     *
     * @param jobId          the job ID that produced the issues
     * @param criticalIssues the list of high/critical severity issues
     */
    public void sendAlert(String jobId, List<IssueDto> criticalIssues) {
        if (!slackEnabled) {
            log.info("Slack alerts are disabled, skipping alert for jobId={}", jobId);
            return;
        }

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("SLACK_WEBHOOK_URL is not configured. Skipping alert for jobId={}. "
                    + "Set the SLACK_WEBHOOK_URL environment variable to enable notifications.", jobId);
            return;
        }

        try {
            SlackMessage slackMessage = buildSlackMessage(jobId, criticalIssues);
            postToSlack(slackMessage, jobId);
        } catch (Exception ex) {
            log.error("Failed to send Slack alert for jobId={}: {}", jobId, ex.getMessage(), ex);
            // Intentionally not re-throwing — we don't want to crash the Kafka consumer
        }
    }

    private SlackMessage buildSlackMessage(String jobId, List<IssueDto> criticalIssues) {
        long criticalCount = criticalIssues.stream()
                .filter(i -> "critical".equalsIgnoreCase(i.getSeverity()))
                .count();
        long highCount = criticalIssues.stream()
                .filter(i -> "high".equalsIgnoreCase(i.getSeverity()))
                .count();

        String headerText = String.format(
                ":rotating_light: *Security Alert* — Scan job `%s` found *%d critical* and *%d high* severity issue(s).",
                jobId, criticalCount, highCount);

        List<SlackAttachment> attachments = new ArrayList<>();
        for (IssueDto issue : criticalIssues) {
            attachments.add(buildAttachment(issue));
        }

        return SlackMessage.builder()
                .text(headerText)
                .attachments(attachments)
                .build();
    }

    private SlackAttachment buildAttachment(IssueDto issue) {
        String color = "critical".equalsIgnoreCase(issue.getSeverity()) ? "danger" : "warning";
        String title = String.format("[%s] %s",
                issue.getSeverity() != null ? issue.getSeverity().toUpperCase() : "UNKNOWN",
                issue.getType() != null ? issue.getType() : "Unknown Issue Type");

        List<SlackField> fields = new ArrayList<>();
        fields.add(SlackField.builder()
                .title("Severity")
                .value(issue.getSeverity() != null ? issue.getSeverity().toUpperCase() : "UNKNOWN")
                .shortValue(true)
                .build());
        fields.add(SlackField.builder()
                .title("Type")
                .value(issue.getType() != null ? issue.getType() : "N/A")
                .shortValue(true)
                .build());
        fields.add(SlackField.builder()
                .title("Location")
                .value(issue.getLocation() != null ? issue.getLocation() : "N/A")
                .shortValue(false)
                .build());

        return SlackAttachment.builder()
                .color(color)
                .title(title)
                .text(issue.getDescription() != null ? issue.getDescription() : "No description provided")
                .fields(fields)
                .footer("Project Crystal — MS-Alert")
                .ts(Instant.now().getEpochSecond())
                .build();
    }

    private void postToSlack(SlackMessage slackMessage, String jobId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SlackMessage> request = new HttpEntity<>(slackMessage, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Slack alert sent successfully for jobId={}", jobId);
        } else {
            log.error("Slack webhook returned non-2xx status={} for jobId={}",
                    response.getStatusCode(), jobId);
        }
    }
}
