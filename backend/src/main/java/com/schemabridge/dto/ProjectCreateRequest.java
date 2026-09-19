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
    private String targetSystemName;
}
