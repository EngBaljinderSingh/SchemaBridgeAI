package com.schemabridge.domain;

import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.domain.enums.TransformationOpType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "mapping_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MappingRule {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_definition_id", nullable = false)
    private MappingDefinition mappingDefinition;

    @Column(name = "source_paths", nullable = false, columnDefinition = "TEXT")
    private String sourcePaths; // JSON array of string paths e.g. ["contact.email"] or ["firstName", "lastName"]

    @Column(name = "target_path", nullable = false)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransformationOpType operation;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_method", nullable = false, length = 50)
    private MatchMethod matchMethod;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private MappingStatus status = MappingStatus.SUGGESTED;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}
