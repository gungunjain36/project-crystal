package com.crystal.msintake;

import com.crystal.msintake.kafka.producer.ScanJobProducer;
import com.crystal.msintake.model.dto.ScanJobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"scan-jobs"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "kafka.topics.scan-jobs=scan-jobs",
        "app.api-key=test-api-key"
})
class ScanJobProducerIntegrationTest {

    @Autowired
    private ScanJobProducer scanJobProducer;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaMessageListenerContainer<String, String> container;
    private BlockingQueue<ConsumerRecord<String, String>> records;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProperties = new ContainerProperties("scan-jobs");
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, String>) records::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        container.stop();
    }

    @Test
    void send_shouldDeliverMessageToScanJobsTopic() throws Exception {
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.now();

        ScanJobMessage message = ScanJobMessage.builder()
                .jobId(jobId)
                .requestedAt(requestedAt)
                .targetType("github_url")
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        scanJobProducer.send(message);

        ConsumerRecord<String, String> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo(jobId.toString());

        ScanJobMessage receivedMessage = objectMapper.readValue(received.value(), ScanJobMessage.class);
        assertThat(receivedMessage.getJobId()).isEqualTo(jobId);
        assertThat(receivedMessage.getTargetType()).isEqualTo("github_url");
        assertThat(receivedMessage.getTarget()).isEqualTo("https://github.com/org/repo");
        assertThat(receivedMessage.getRequestedBy()).isEqualTo("user@example.com");
    }

    @Test
    void send_shouldUseJobIdAsMessageKey() throws Exception {
        UUID jobId = UUID.randomUUID();

        ScanJobMessage message = ScanJobMessage.builder()
                .jobId(jobId)
                .requestedAt(Instant.now())
                .targetType("file")
                .target("/path/to/file.java")
                .requestedBy("ci-system")
                .build();

        scanJobProducer.send(message);

        ConsumerRecord<String, String> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo(jobId.toString());
    }

    @Test
    void send_shouldIncludeAllRequiredSchemaFields() throws Exception {
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.now();

        ScanJobMessage message = ScanJobMessage.builder()
                .jobId(jobId)
                .requestedAt(requestedAt)
                .targetType("github_url")
                .target("https://github.com/test/project")
                .requestedBy("integration-test")
                .build();

        scanJobProducer.send(message);

        ConsumerRecord<String, String> received = records.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();

        String json = received.value();
        // Verify all required schema fields are present
        assertThat(json).contains("jobId");
        assertThat(json).contains("requestedAt");
        assertThat(json).contains("targetType");
        assertThat(json).contains("target");
        assertThat(json).contains("requestedBy");
    }
}
