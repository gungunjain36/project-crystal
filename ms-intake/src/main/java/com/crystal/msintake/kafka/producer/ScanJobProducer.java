package com.crystal.msintake.kafka.producer;

import com.crystal.msintake.model.dto.ScanJobMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ScanJobProducer {

    private final KafkaTemplate<String, ScanJobMessage> kafkaTemplate;
    private final String scanJobsTopic;

    public ScanJobProducer(
            KafkaTemplate<String, ScanJobMessage> kafkaTemplate,
            @Value("${kafka.topics.scan-jobs}") String scanJobsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.scanJobsTopic = scanJobsTopic;
    }

    public void send(ScanJobMessage message) {
        String key = message.getJobId().toString();

        log.info("Sending scan job to topic={} key={} targetType={} requestedBy={}",
                scanJobsTopic, key, message.getTargetType(), message.getRequestedBy());

        CompletableFuture<SendResult<String, ScanJobMessage>> future =
                kafkaTemplate.send(scanJobsTopic, key, message);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send scan job key={} to topic={}: {}",
                        key, scanJobsTopic, ex.getMessage(), ex);
            } else {
                log.info("Successfully sent scan job key={} to topic={} partition={} offset={}",
                        key,
                        scanJobsTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
