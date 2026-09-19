package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaImportRequest {
    private Direction direction; // Optional in request body, set by /source or /target endpoints

    private String systemName;

    @NotNull(message = "Schema type is required")
    private SchemaType schemaType; // JSON_SCHEMA, OPENAPI, SAMPLE_JSON

    @NotBlank(message = "Schema content is required")
    private String schemaContent;

    private String schemaName;
    private String schemaVersion;
}
