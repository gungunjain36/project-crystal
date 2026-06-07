package com.crystal.msalert.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackField {

    private String title;
    private String value;

    @JsonProperty("short")
    private boolean shortValue;
}
