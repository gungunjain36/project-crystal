package com.crystal.msalert.service;

import com.crystal.msalert.model.dto.IssueDto;
import com.crystal.msalert.model.dto.SlackMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlackNotificationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SlackNotificationService slackNotificationService;

    private static final String TEST_WEBHOOK_URL = "https://hooks.slack.com/services/test/webhook";

    @BeforeEach
    void setUp() {
        slackNotificationService = new SlackNotificationService(restTemplate);
        ReflectionTestUtils.setField(slackNotificationService, "webhookUrl", TEST_WEBHOOK_URL);
        ReflectionTestUtils.setField(slackNotificationService, "slackEnabled", true);
    }

    @Test
    void sendAlert_withCriticalIssue_usesColorDanger() {
        // Arrange
        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical")
                .type("RCE")
                .location("exec.java:5")
                .description("Remote code execution vulnerability")
                .build();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        // Act
        slackNotificationService.sendAlert("job-001", Collections.singletonList(criticalIssue));

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<SlackMessage>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));

        SlackMessage sentMessage = requestCaptor.getValue().getBody();
        assertThat(sentMessage).isNotNull();
        assertThat(sentMessage.getAttachments()).hasSize(1);
        assertThat(sentMessage.getAttachments().get(0).getColor()).isEqualTo("danger");
        assertThat(sentMessage.getAttachments().get(0).getTitle()).contains("CRITICAL");
    }

    @Test
    void sendAlert_withHighIssue_usesColorWarning() {
        // Arrange
        IssueDto highIssue = IssueDto.builder()
                .severity("high")
                .type("SQLi")
                .location("repo.java:50")
                .description("SQL injection vulnerability")
                .build();

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        // Act
        slackNotificationService.sendAlert("job-002", Collections.singletonList(highIssue));

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<SlackMessage>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));

        SlackMessage sentMessage = requestCaptor.getValue().getBody();
        assertThat(sentMessage).isNotNull();
        assertThat(sentMessage.getAttachments()).hasSize(1);
        assertThat(sentMessage.getAttachments().get(0).getColor()).isEqualTo("warning");
        assertThat(sentMessage.getAttachments().get(0).getTitle()).contains("HIGH");
    }

    @Test
    void sendAlert_withBlankWebhookUrl_logsWarningAndDoesNotThrow() {
        // Arrange
        ReflectionTestUtils.setField(slackNotificationService, "webhookUrl", "");

        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical")
                .type("RCE")
                .location("exec.java:5")
                .description("Critical issue")
                .build();

        // Act — should not throw
        slackNotificationService.sendAlert("job-003", Collections.singletonList(criticalIssue));

        // Assert
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void sendAlert_withSlackDisabled_doesNotPost() {
        // Arrange
        ReflectionTestUtils.setField(slackNotificationService, "slackEnabled", false);

        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical")
                .type("RCE")
                .location("exec.java:5")
                .description("Critical issue")
                .build();

        // Act
        slackNotificationService.sendAlert("job-004", Collections.singletonList(criticalIssue));

        // Assert
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void sendAlert_withMultipleIssues_buildsOneAttachmentPerIssue() {
        // Arrange
        List<IssueDto> issues = List.of(
                IssueDto.builder().severity("critical").type("RCE").location("a.java:1").description("desc1").build(),
                IssueDto.builder().severity("high").type("SQLi").location("b.java:2").description("desc2").build()
        );

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        // Act
        slackNotificationService.sendAlert("job-005", issues);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<SlackMessage>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));

        SlackMessage sentMessage = requestCaptor.getValue().getBody();
        assertThat(sentMessage).isNotNull();
        assertThat(sentMessage.getAttachments()).hasSize(2);
    }
}
