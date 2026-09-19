package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
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
public class MappingDefinitionDto {
    private String id;
    private String projectId;
    private Direction direction;
    private Integer version;
    private String sourceSchemaId;
    private String targetSchemaId;
    private MappingStatus status;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private List<MappingRuleDto> rules;
    private List<String> unmappedSourceFields;
    private List<String> unmappedTargetFields;
    private List<String> warnings;
}
