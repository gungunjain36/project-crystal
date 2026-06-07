package com.crystal.msresults.kafka;

import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultMessage;
import com.crystal.msresults.repository.ScanResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"scan-results"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9093",
                "port=9093"
        }
)
@DirtiesContext
class ScanResultConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, ScanResultMessage> kafkaTemplate;

    @Autowired
    private ScanResultRepository scanResultRepository;

    @Test
    void consumeScanResultMessage_persistsToDatabase() throws Exception {
        String jobId = "integration-test-job-" + System.currentTimeMillis();

        ScanResultMessage message = ScanResultMessage.builder()
                .jobId(jobId)
                .completedAt(Instant.now())
                .status("success")
                .issues(List.of(
                        IssueDto.builder()
                                .severity("critical")
                                .type("HARDCODED_SECRET")
                                .location("config/secrets.java:10")
                                .description("Hardcoded API key found")
                                .build()
                ))
                .build();

        kafkaTemplate.send("scan-results", jobId, message);

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> scanResultRepository.existsByJobId(jobId));

        assertThat(scanResultRepository.findByJobId(jobId)).isPresent();
        assertThat(scanResultRepository.findByJobId(jobId).get().getIssues()).hasSize(1);
        assertThat(scanResultRepository.findByJobId(jobId).get().getStatus()).isEqualTo("success");
    }
}
