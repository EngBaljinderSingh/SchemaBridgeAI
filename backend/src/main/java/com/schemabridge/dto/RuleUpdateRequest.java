package com.schemabridge.dto;

import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.TransformationOpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleUpdateRequest {
    private String id;
    private List<String> sourcePaths;
    private String targetPath;
    private TransformationOpType operation;
    private Map<String, Object> parameters;
    private MappingStatus status;
    private String explanation;
}
