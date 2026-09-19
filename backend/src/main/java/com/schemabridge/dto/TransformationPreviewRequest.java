package com.schemabridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationPreviewRequest {
    private String mappingDefinitionId;
    private List<MappingRuleDto> adhocRules; // Optional temporary rules to test in preview mode

    @NotNull(message = "Source payload is required")
    private Object sourcePayload; // JSON string or Map/List node
}
