package com.crystal.msresults.controller;

import com.crystal.msresults.exception.ResourceNotFoundException;
import com.crystal.msresults.model.dto.IssueDto;
import com.crystal.msresults.model.dto.ScanResultResponse;
import com.crystal.msresults.service.ScanResultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScanResultController.class)
@ActiveProfiles("test")
class ScanResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanResultService scanResultService;

    @Autowired
    private ObjectMapper objectMapper;

    private ScanResultResponse sampleResponse;
    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        IssueDto issue = IssueDto.builder()
                .id(1L)
                .severity("high")
                .type("SQL_INJECTION")
                .location("UserDao.java:42")
                .description("SQL injection vulnerability")
                .build();

        sampleResponse = ScanResultResponse.builder()
                .id(UUID.randomUUID())
                .jobId("job-abc-123")
                .completedAt(Instant.now())
                .status("success")
                .createdAt(Instant.now())
                .issues(List.of(issue))
                .build();
    }

    @Test
    void getAllResults_withApiKey_returns200() throws Exception {
        when(scanResultService.getAllResults(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/results")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].jobId").value("job-abc-123"));
    }

    @Test
    void getAllResults_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/results"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getResultByJobId_found_returns200() throws Exception {
        when(scanResultService.getResultByJobId("job-abc-123")).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/results/job-abc-123")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-abc-123"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void getResultByJobId_notFound_returns404() throws Exception {
        when(scanResultService.getResultByJobId("nonexistent"))
                .thenThrow(new ResourceNotFoundException("ScanResult", "jobId", "nonexistent"));

        mockMvc.perform(get("/api/v1/results/nonexistent")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getSeverityStats_withApiKey_returns200() throws Exception {
        when(scanResultService.getSeverityStats()).thenReturn(Map.of("high", 3L, "low", 5L));

        mockMvc.perform(get("/api/v1/results/stats/severity")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.high").value(3))
                .andExpect(jsonPath("$.low").value(5));
    }
}
