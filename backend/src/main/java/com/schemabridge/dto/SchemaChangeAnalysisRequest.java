package com.schemabridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaChangeAnalysisRequest {
    @NotBlank(message = "New schema content is required")
    private String newSchemaContent;
    private String systemDefinitionId;
}
