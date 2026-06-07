package com.crystal.msintake;

import com.crystal.msintake.model.dto.ScanRequest;
import com.crystal.msintake.model.dto.ScanResponse;
import com.crystal.msintake.service.ScanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.api-key=test-api-key",
        "spring.kafka.bootstrap-servers=localhost:9092"
})
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScanService scanService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String VALID_API_KEY = "test-api-key";
    private static final String API_URL = "/api/v1/scans";

    @Test
    void postScan_withValidBody_returns202() throws Exception {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        ScanResponse mockResponse = ScanResponse.builder()
                .jobId(UUID.randomUUID())
                .requestedAt(Instant.now())
                .status("accepted")
                .message("Scan job accepted and queued for processing")
                .build();

        when(scanService.initiateScan(any(ScanRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post(API_URL)
                        .header("X-API-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void postScan_withMissingTarget_returns400() throws Exception {
        String requestJson = """
                {
                    "targetType": "github_url",
                    "requestedBy": "user@example.com"
                }
                """;

        mockMvc.perform(post(API_URL)
                        .header("X-API-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void postScan_withMissingRequestedBy_returns400() throws Exception {
        String requestJson = """
                {
                    "targetType": "file",
                    "target": "/path/to/file.java"
                }
                """;

        mockMvc.perform(post(API_URL)
                        .header("X-API-Key", VALID_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void postScan_withoutApiKey_returns401() throws Exception {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        mockMvc.perform(post(API_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void postScan_withWrongApiKey_returns401() throws Exception {
        ScanRequest request = ScanRequest.builder()
                .targetType(ScanRequest.TargetType.GITHUB_URL)
                .target("https://github.com/org/repo")
                .requestedBy("user@example.com")
                .build();

        mockMvc.perform(post(API_URL)
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
