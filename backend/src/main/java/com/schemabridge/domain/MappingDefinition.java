package com.schemabridge.domain;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mapping_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MappingDefinition {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private IntegrationProject project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Direction direction = Direction.SOURCE_TO_TARGET;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "source_schema_id", length = 64)
    private String sourceSchemaId;

    @Column(name = "target_schema_id", length = 64)
    private String targetSchemaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MappingStatus status = MappingStatus.DRAFT;

    @Column(name = "created_by", length = 100)
    @Builder.Default
    private String createdBy = "system";

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "mappingDefinition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MappingRule> rules = new ArrayList<>();
}
