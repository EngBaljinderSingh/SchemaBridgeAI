package com.schemabridge.domain;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.domain.enums.SystemType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "system_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemDefinition {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private IntegrationProject project;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", nullable = false, length = 50)
    @Builder.Default
    private SystemType systemType = SystemType.REST_API;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Direction direction;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "authentication_type", length = 50)
    @Builder.Default
    private String authenticationType = "NONE";

    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false, length = 50)
    @Builder.Default
    private SchemaType schemaType = SchemaType.JSON_SCHEMA;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "systemDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SchemaDefinition> schemas = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
