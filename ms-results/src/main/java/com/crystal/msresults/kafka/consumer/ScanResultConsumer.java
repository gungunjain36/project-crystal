package com.crystal.msresults.kafka.consumer;

import com.crystal.msresults.model.dto.ScanResultMessage;
import com.crystal.msresults.service.ScanResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ScanResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScanResultConsumer.class);

    private final ScanResultService scanResultService;

    public ScanResultConsumer(ScanResultService scanResultService) {
        this.scanResultService = scanResultService;
    }

    @KafkaListener(
            topics = "${kafka.topics.scan-results}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ScanResultMessage message) {
        log.info("Received scan result message for jobId={} status={}", message.getJobId(), message.getStatus());
        try {
            scanResultService.persistResult(message);
            log.info("Successfully processed scan result for jobId={}", message.getJobId());
        } catch (Exception e) {
            log.error("Failed to process scan result for jobId={}: {}", message.getJobId(), e.getMessage(), e);
            throw e;
        }
    }
}
