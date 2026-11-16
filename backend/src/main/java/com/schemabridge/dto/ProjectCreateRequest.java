package com.schemabridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateRequest {
    @NotBlank(message = "Project name is required")
    private String name;
    private String description;
    private String sourceSystemName;
    private String sourceSystemUrl;
    private String targetSystemName;
    private String targetSystemUrl;
    private String unmappedFieldPolicy;
    private String missingMappingPolicy;
    private String approverEmail;
    @Builder.Default
    private boolean autoApproveEnabled = true;
}
