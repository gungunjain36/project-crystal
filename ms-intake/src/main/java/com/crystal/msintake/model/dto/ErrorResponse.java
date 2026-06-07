package com.crystal.msintake.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response structure")
public class ErrorResponse {

    @Schema(description = "Timestamp when the error occurred")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error message", example = "Validation failed")
    private String message;

    @Schema(description = "Request path that caused the error", example = "/api/v1/scans")
    private String path;

    @Schema(description = "List of field-level validation errors")
    private List<FieldError> fieldErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual field validation error")
    public static class FieldError {

        @Schema(description = "Field name", example = "target")
        private String field;

        @Schema(description = "Rejected value", example = "")
        private Object rejectedValue;

        @Schema(description = "Error message", example = "target must not be blank")
        private String message;
    }
}
