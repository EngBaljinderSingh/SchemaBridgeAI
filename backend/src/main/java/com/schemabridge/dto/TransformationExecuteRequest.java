package com.schemabridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationExecuteRequest {
    private Integer mappingVersion; // Optional specific version, otherwise latest published
    @NotNull(message = "Source payload is required")
    private Object sourcePayload;
    private boolean validateTarget;
}
