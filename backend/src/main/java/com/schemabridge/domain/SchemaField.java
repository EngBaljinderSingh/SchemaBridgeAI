package com.schemabridge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "schema_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaField {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_definition_id", nullable = false)
    private SchemaDefinition schemaDefinition;

    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType;

    @Column(length = 50)
    private String format;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "is_array", nullable = false)
    @Builder.Default
    private boolean array = false;

    @Column(name = "parent_path")
    private String parentPath;

    @Column(name = "constraints_json", columnDefinition = "TEXT")
    private String constraintsJson;
}
