package com.crystal.msintake.model.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for initiating a new security scan")
public class ScanRequest {

    @NotNull(message = "targetType is required")
    @Schema(description = "Type of target to scan", example = "GITHUB_URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private TargetType targetType;

    @NotBlank(message = "target must not be blank")
    @Schema(description = "The target to scan — a file path or a GitHub URL", example = "https://github.com/org/repo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String target;

    @NotBlank(message = "requestedBy must not be blank")
    @Schema(description = "Identifier of the user or service requesting the scan", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestedBy;

    public enum TargetType {
        FILE,
        GITHUB_URL;

        @JsonValue
        public String toValue() {
            return this.name().toLowerCase();
        }

        @JsonCreator
        public static TargetType fromValue(String value) {
            if (value == null) {
                return null;
            }
            for (TargetType type : TargetType.values()) {
                if (type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown TargetType: " + value);
        }
    }
}
