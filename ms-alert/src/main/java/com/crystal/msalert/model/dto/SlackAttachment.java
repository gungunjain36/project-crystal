package com.crystal.msalert.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackAttachment {

    private String color;
    private String title;
    private String text;
    private List<SlackField> fields;
    private String footer;
    private long ts;
}
