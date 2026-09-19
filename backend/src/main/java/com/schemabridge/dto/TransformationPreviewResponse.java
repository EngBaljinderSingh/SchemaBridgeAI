package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationPreviewResponse {
    private Object transformedPayload;
    private ValidationResultDto validation;
    private List<String> appliedRules;
    private Integer mappingVersion;
    private long executionTimeMs;
}
