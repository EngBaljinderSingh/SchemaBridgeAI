package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaResponse {
    private String id;
    private String systemDefinitionId;
    private String systemName;
    private Direction direction;
    private String schemaName;
    private String schemaVersion;
    private SchemaType schemaType;
    private String schemaHash;
    private String originalSchema;
    private LocalDateTime createdAt;
    private List<FieldExtractionDto> fields;
}
