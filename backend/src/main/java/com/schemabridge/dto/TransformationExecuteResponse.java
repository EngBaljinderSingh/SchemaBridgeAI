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
public class TransformationExecuteResponse {
    private String executionId;
    private String status;
    private Integer mappingVersion;
    private Object transformedPayload;
    private ValidationResultDto validationResult;
    private List<String> appliedRules;
    private long executionDurationMs;
}
