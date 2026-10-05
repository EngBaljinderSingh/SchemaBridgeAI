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
    private int systemCount;
    private int mappingVersionCount;
    private Integer latestPublishedVersion;
}
