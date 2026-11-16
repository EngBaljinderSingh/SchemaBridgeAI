package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayExecutionLogDto {
    private String id;
    private String projectId;
    private String projectName;
    private String apiId;
    private String apiName;
    private String correlationId;
    private String scenarioName;
    private String direction;
    private String httpMethod;
    private String endpointPath;
    private String sourceUrl;
    private String gatewayUrl;
    private String targetUrl;
    private Integer mappingVersion;
    private String status;
    private int httpStatusCode;
    private long durationMs;
    private Map<String, Object> requestPayload;
    private Map<String, Object> transformedPayload;
    private Map<String, Object> downstreamResponse;
    private List<String> appliedRules;
    private List<String> unmappedFields;
    private String errorMessage;
    private String resolutionHint;
    private LocalDateTime createdAt;
}
