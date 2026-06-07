package com.crystal.msalert.kafka;

import com.crystal.msalert.model.dto.IssueDto;
import com.crystal.msalert.model.dto.ScanResultMessage;
import com.crystal.msalert.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"scan-results"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:${embeddedKafka.port:9093}",
                "port=${embeddedKafka.port:9093}"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topics.scan-results=scan-results",
        "spring.kafka.consumer.group-id=test-group",
        "app.slack.webhook-url=",
        "app.slack.enabled=false"
})
class ScanResultConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, ScanResultMessage> kafkaTemplate;

    @MockBean
    private AlertService alertService;

    @Test
    void consumer_receivesMessageWithCriticalIssue_callsAlertService() throws InterruptedException {
        // Arrange
        IssueDto criticalIssue = IssueDto.builder()
                .severity("critical")
                .type("RCE")
                .location("exec.java:99")
                .description("Remote code execution found")
                .build();

        ScanResultMessage message = ScanResultMessage.builder()
                .jobId("integration-job-001")
                .completedAt("2025-01-01T10:00:00Z")
                .status("success")
                .issues(Collections.singletonList(criticalIssue))
                .build();

        // Act
        kafkaTemplate.send("scan-results", "integration-job-001", message);

        // Assert — wait up to 10 seconds for the consumer to process
        verify(alertService, timeout(10_000).times(1)).processResult(any(ScanResultMessage.class));
    }
}
