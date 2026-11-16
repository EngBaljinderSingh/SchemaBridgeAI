package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoMappedApiPairDto {
    private String sourceMethod;
    private String sourcePath;
    private String sourceSummary;
    private String sourceModelName;

    private String targetMethod;
    private String targetPath;
    private String targetSummary;
    private String targetModelName;

    private String suggestedName;
    private double confidence;
    private String matchReason;
    private boolean requiresReview;
    private boolean selected;

    private String sourceSampleJson;
    private String targetSampleJson;
}
