package com.schemabridge.domain;

import com.schemabridge.domain.enums.SchemaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "schema_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaDefinition {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_definition_id", nullable = false)
    private SystemDefinition systemDefinition;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "schema_version", nullable = false, length = 50)
    @Builder.Default
    private String schemaVersion = "1.0";

    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false, length = 50)
    @Builder.Default
    private SchemaType schemaType = SchemaType.JSON_SCHEMA;

    @Column(name = "original_schema", nullable = false, columnDefinition = "TEXT")
    private String originalSchema;

    @Column(name = "schema_hash", nullable = false, length = 64)
    private String schemaHash;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "schemaDefinition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<SchemaField> fields = new ArrayList<>();
}
