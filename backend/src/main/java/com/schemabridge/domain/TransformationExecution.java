package com.schemabridge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transformation_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransformationExecution {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private IntegrationProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_definition_id")
    private MappingDefinition mappingDefinition;

    @Column(name = "mapping_version", nullable = false)
    private Integer mappingVersion;

    @Column(nullable = false, length = 50)
    private String status; // SUCCESS, VALIDATION_ERROR, TRANSFORMATION_ERROR

    @Column(name = "source_payload_hash", length = 64)
    private String sourcePayloadHash;

    @Column(name = "validation_result", columnDefinition = "TEXT")
    private String validationResult;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
