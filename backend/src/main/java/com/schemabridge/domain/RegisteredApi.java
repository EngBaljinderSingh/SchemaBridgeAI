package com.schemabridge.domain;

import com.schemabridge.domain.enums.Direction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "registered_apis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisteredApi {

    @Id
    @Column(length = 64)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private IntegrationProject project;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "http_method", nullable = false, length = 20)
    @Builder.Default
    private String httpMethod = "POST";

    @Column(name = "endpoint_path", nullable = false, length = 500)
    private String endpointPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Direction direction = Direction.SOURCE_TO_TARGET;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "source_schema", columnDefinition = "TEXT")
    private String sourceSchema;

    @Column(name = "target_schema", columnDefinition = "TEXT")
    private String targetSchema;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
