package com.schemabridge.service.matching;

import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;

import java.util.List;
import java.util.Set;

public interface MatchStrategy {
    MatchMethod getMethod();
    int getOrder();

    List<MappingRuleDto> match(
            List<FieldExtractionDto> availableSources,
            List<FieldExtractionDto> availableTargets,
            Set<String> alreadyMappedTargetPaths,
            String projectId
    );
}
