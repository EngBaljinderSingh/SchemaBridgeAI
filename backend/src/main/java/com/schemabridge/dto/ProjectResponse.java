package com.schemabridge.dto;

import com.schemabridge.domain.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private String description;
    private ProjectStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String approverEmail;
    private boolean autoApproveEnabled;
    private String sourceSystemName;
    private String sourceSystemUrl;
    private String gatewayUrl;
    private String targetSystemName;
    private String targetSystemUrl;
    private String unmappedFieldPolicy;
    private String missingMappingPolicy;
    private int systemCount;
    private int mappingVersionCount;
    private Integer latestPublishedVersion;
}
