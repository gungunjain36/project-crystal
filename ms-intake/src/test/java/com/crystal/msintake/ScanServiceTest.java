package com.crystal.msintake;

import com.crystal.msintake.kafka.producer.ScanJobProducer;
import com.crystal.msintake.model.dto.ScanJobMessage;
import com.crystal.msintake.model.dto.ScanRequest;
import com.crystal.msintake.model.dto.ScanResponse;
import com.crystal.msintake.service.ScanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ScanJobProducer scanJobProducer;

    private ScanServiceImpl scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanServiceImpl(scanJobProducer);
    }

    @Test
    void initiateScan_shouldProduceCorrectScanJobMessage() {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        scanService.initiateScan(request);

        ArgumentCaptor<ScanJobMessage> messageCaptor = ArgumentCaptor.forClass(ScanJobMessage.class);
        verify(scanJobProducer, times(1)).send(messageCaptor.capture());

        ScanJobMessage capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getTargetType()).isEqualTo("github_url");
        assertThat(capturedMessage.getTarget()).isEqualTo("https://github.com/org/repo");
        assertThat(capturedMessage.getRequestedBy()).isEqualTo("user@example.com");
    }

    @Test
    void initiateScan_shouldGenerateValidUUID() {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.FILE)
                .target("/path/to/file.java")
                .requestedBy("ci-system")
                .build();

        ScanResponse response = scanService.initiateScan(request);

        assertThat(response.getJobId()).isNotNull();
        // UUID.fromString will throw if not a valid UUID
        assertThat(response.getJobId().toString()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
    }

    @Test
    void initiateScan_shouldSetRequestedAtToNow() {
        Instant before = Instant.now();

        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.FILE)
                .target("/path/to/file.java")
                .requestedBy("ci-system")
                .build();

        ScanResponse response = scanService.initiateScan(request);
        Instant after = Instant.now();

        assertThat(response.getRequestedAt()).isNotNull();
        assertThat(response.getRequestedAt()).isAfterOrEqualTo(before);
        assertThat(response.getRequestedAt()).isBeforeOrEqualTo(after);
    }

    @Test
    void initiateScan_shouldReturnAcceptedStatus() {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        ScanResponse response = scanService.initiateScan(request);

        assertThat(response.getStatus()).isEqualTo("accepted");
        assertThat(response.getMessage()).isNotBlank();
    }

    @Test
    void initiateScan_shouldPassJobIdToKafkaMessage() {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.FILE)
                .target("/src/main/App.java")
                .requestedBy("dev@company.com")
                .build();

        ScanResponse response = scanService.initiateScan(request);

        ArgumentCaptor<ScanJobMessage> captor = ArgumentCaptor.forClass(ScanJobMessage.class);
        verify(scanJobProducer).send(captor.capture());

        ScanJobMessage message = captor.getValue();
        assertThat(message.getJobId()).isEqualTo(response.getJobId());
        assertThat(message.getRequestedAt()).isEqualTo(response.getRequestedAt());
    }

    @Test
    void initiateScan_shouldProduceExactlyOneKafkaMessage() {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        scanService.initiateScan(request);

        verify(scanJobProducer, times(1)).send(any(ScanJobMessage.class));
        verifyNoMoreInteractions(scanJobProducer);
    }
}
