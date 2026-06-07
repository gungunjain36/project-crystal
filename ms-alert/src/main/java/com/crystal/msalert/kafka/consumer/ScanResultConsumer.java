package com.crystal.msalert.kafka.consumer;

import com.crystal.msalert.model.dto.ScanResultMessage;
import com.crystal.msalert.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScanResultConsumer {

    private final AlertService alertService;

    @KafkaListener(
            topics = "${kafka.topics.scan-results}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload ScanResultMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received message from topic={} partition={} offset={} jobId={}",
                topic, partition, offset,
                message != null ? message.getJobId() : "null");

        try {
            int alertCount = alertService.processResult(message);
            log.info("Finished processing message from topic={} offset={} alertCount={}",
                    topic, offset, alertCount);
        } catch (Exception ex) {
            log.error("Error processing Kafka message from topic={} offset={}: {}",
                    topic, offset, ex.getMessage(), ex);
        }
    }
}
