package com.crystal.msresults.service;

import com.crystal.msresults.exception.ResourceNotFoundException;
import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultMessage;
import com.crystal.msresults.model.dto.ScanResultResponse;
import com.crystal.msresults.model.entity.ScanIssue;
import com.crystal.msresults.model.entity.ScanResult;
import com.crystal.msresults.repository.ScanIssueRepository;
import com.crystal.msresults.repository.ScanResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanResultServiceTest {

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private ScanIssueRepository scanIssueRepository;

    @InjectMocks
    private ScanResultServiceImpl scanResultService;

    private ScanResultMessage testMessage;
    private ScanResult testScanResult;

    @BeforeEach
    void setUp() {
        IssueDto issue1 = IssueDto.builder()
                .severity("high")
                .type("SQL_INJECTION")
                .location("src/main/UserDao.java:42")
                .description("SQL injection vulnerability detected")
                .build();

        IssueDto issue2 = IssueDto.builder()
                .severity("low")
                .type("UNUSED_IMPORT")
                .location("src/main/App.java:1")
                .description("Unused import statement")
                .build();

        testMessage = ScanResultMessage.builder()
                .jobId("test-job-123")
                .completedAt(Instant.now())
                .status("success")
                .issues(Arrays.asList(issue1, issue2))
                .build();

        testScanResult = new ScanResult();
        testScanResult.setId(UUID.randomUUID());
        testScanResult.setJobId("test-job-123");
        testScanResult.setCompletedAt(Instant.now());
        testScanResult.setStatus("success");

        ScanIssue scanIssue1 = new ScanIssue();
        scanIssue1.setId(1L);
        scanIssue1.setSeverity("high");
        scanIssue1.setType("SQL_INJECTION");
        scanIssue1.setLocation("src/main/UserDao.java:42");
        scanIssue1.setDescription("SQL injection vulnerability detected");
        scanIssue1.setScanResult(testScanResult);

        testScanResult.getIssues().add(scanIssue1);
    }

    @Test
    void persistResult_withValidMessage_savesEntities() {
        when(scanResultRepository.existsByJobId("test-job-123")).thenReturn(false);
        when(scanResultRepository.save(any(ScanResult.class))).thenReturn(testScanResult);

        scanResultService.persistResult(testMessage);

        verify(scanResultRepository, times(1)).save(any(ScanResult.class));
    }

    @Test
    void persistResult_withDuplicateJobId_skipsInsert() {
        when(scanResultRepository.existsByJobId("test-job-123")).thenReturn(true);

        scanResultService.persistResult(testMessage);

        verify(scanResultRepository, never()).save(any(ScanResult.class));
    }

    @Test
    void getResultByJobId_notFound_throwsResourceNotFoundException() {
        when(scanResultRepository.findByJobId("nonexistent-job")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanResultService.getResultByJobId("nonexistent-job"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nonexistent-job");
    }

    @Test
    void getSeverityStats_returnsCorrectCounts() {
        List<Object[]> rawStats = Arrays.asList(
                new Object[]{"high", 3L},
                new Object[]{"low", 5L},
                new Object[]{"critical", 1L}
        );
        when(scanIssueRepository.countBySeverity()).thenReturn(rawStats);

        Map<String, Long> stats = scanResultService.getSeverityStats();

        assertThat(stats).hasSize(3);
        assertThat(stats.get("high")).isEqualTo(3L);
        assertThat(stats.get("low")).isEqualTo(5L);
        assertThat(stats.get("critical")).isEqualTo(1L);
    }

    @Test
    void getResultByJobId_found_returnsResponse() {
        when(scanResultRepository.findByJobId("test-job-123")).thenReturn(Optional.of(testScanResult));

        ScanResultResponse response = scanResultService.getResultByJobId("test-job-123");

        assertThat(response).isNotNull();
        assertThat(response.getJobId()).isEqualTo("test-job-123");
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getIssues()).hasSize(1);
    }

    @Test
    void getIssuesByJobId_notFound_throwsResourceNotFoundException() {
        when(scanResultRepository.existsByJobId(anyString())).thenReturn(false);

        assertThatThrownBy(() -> scanResultService.getIssuesByJobId("nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
