package com.schemabridge.dto;

import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.domain.enums.TransformationOpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRuleDto {
    private String id;
    private List<String> sourcePaths;
    private String targetPath;
    private TransformationOpType operation;
    private Map<String, Object> parameters;
    private MatchMethod matchMethod;
    private BigDecimal confidence;
    private ConfidenceLevel confidenceLevel;
    private MappingStatus status;
    private String explanation;
    private boolean requiresReview;
}
