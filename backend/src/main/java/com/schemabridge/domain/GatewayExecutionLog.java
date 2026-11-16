package com.schemabridge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gateway_execution_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayExecutionLog {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "api_id", length = 64)
    private String apiId;

    @Column(name = "api_name")
    private String apiName;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "scenario_name")
    private String scenarioName;

    @Column(nullable = false, length = 50)
    private String direction;

    @Column(name = "http_method", nullable = false, length = 20)
    private String httpMethod;

    @Column(name = "endpoint_path", nullable = false, length = 500)
    private String endpointPath;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "gateway_url", length = 500)
    private String gatewayUrl;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "mapping_version")
    private Integer mappingVersion;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(name = "http_status_code", nullable = false)
    private int httpStatusCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "transformed_payload", columnDefinition = "TEXT")
    private String transformedPayload;

    @Column(name = "downstream_response", columnDefinition = "TEXT")
    private String downstreamResponse;

    @Column(name = "applied_rules_json", columnDefinition = "TEXT")
    private String appliedRulesJson;

    @Column(name = "unmapped_fields_json", columnDefinition = "TEXT")
    private String unmappedFieldsJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "resolution_hint", columnDefinition = "TEXT")
    private String resolutionHint;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
