package com.schemabridge.service.aviator;

import com.schemabridge.dto.*;

import java.util.List;

public interface AviatorMappingService {

    /**
     * Suggests mappings for unresolved/ambiguous fields using Aviator ADT semantic reasoning.
     */
    AviatorSuggestionResult suggestMappings(
            List<FieldExtractionDto> unmappedSourceFields,
            List<FieldExtractionDto> unmappedTargetFields,
            String domainContext
    );

    /**
     * Converts plain-English mapping instruction (e.g. "Map customer name to userName") into a structured MappingRuleDto.
     */
    MappingRuleDto convertNaturalLanguageRule(
            String instruction,
            List<FieldExtractionDto> sourceFields,
            List<FieldExtractionDto> targetFields
    );

    /**
     * Generates a clear, user-friendly explanation for an existing mapping rule.
     */
    String explainMapping(MappingRuleDto rule);

    /**
     * Analyzes schema diffs and recommends rule adjustments.
     */
    SchemaChangeAnalysisResponse.FieldChange analyzeFieldChange(String fieldName, String oldType, String newType);

    /**
     * Checks health and connection to Aviator ADT service.
     */
    AviatorHealthResponse healthCheck();

    record AviatorSuggestionResult(
            List<MappingRuleDto> suggestedRules,
            List<String> unmappedSourceFields,
            List<String> unmappedTargetFields,
            List<String> warnings
    ) {}
}
