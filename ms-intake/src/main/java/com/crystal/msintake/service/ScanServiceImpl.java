package com.crystal.msintake.service;

import com.crystal.msintake.kafka.producer.ScanJobProducer;
import com.crystal.msintake.model.dto.ScanJobMessage;
import com.crystal.msintake.model.dto.ScanRequest;
import com.crystal.msintake.model.dto.ScanResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class ScanServiceImpl implements ScanService {

    private final ScanJobProducer scanJobProducer;

    public ScanServiceImpl(ScanJobProducer scanJobProducer) {
        this.scanJobProducer = scanJobProducer;
    }

    @Override
    public ScanResponse initiateScan(ScanRequest request) {
        UUID jobId = UUID.randomUUID();
        Instant requestedAt = Instant.now();

        ScanJobMessage message = ScanJobMessage.builder()
                .jobId(jobId)
                .requestedAt(requestedAt)
                .targetType(request.getTargetType().name().toLowerCase())
                .target(request.getTarget())
                .requestedBy(request.getRequestedBy())
                .build();

        log.info("Initiating scan jobId={} targetType={} requestedBy={}",
                jobId, message.getTargetType(), message.getRequestedBy());

        scanJobProducer.send(message);

        return ScanResponse.builder()
                .jobId(jobId)
                .requestedAt(requestedAt)
                .status("accepted")
                .message("Scan job accepted and queued for processing")
                .build();
    }

    @Override
    public ScanResponse getScanStatus(String jobId) {
        log.info("Fetching status for jobId={}", jobId);

        // ms-intake is stateless — it only accepts and forwards jobs.
        // Status tracking is handled by downstream services.
        return ScanResponse.builder()
                .jobId(UUID.fromString(jobId))
                .status("accepted")
                .message("Job has been accepted and forwarded to the scan pipeline")
                .build();
    }
}
