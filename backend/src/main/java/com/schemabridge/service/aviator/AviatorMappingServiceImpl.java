package com.schemabridge.service.aviator;

import com.schemabridge.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AviatorMappingServiceImpl implements AviatorMappingService {

    private static final Logger log = LoggerFactory.getLogger(AviatorMappingServiceImpl.class);
    private final AviatorClient aviatorClient;

    public AviatorMappingServiceImpl(AviatorClient aviatorClient) {
        this.aviatorClient = aviatorClient;
    }

    @Override
    public AviatorSuggestionResult suggestMappings(
            List<FieldExtractionDto> unmappedSourceFields,
            List<FieldExtractionDto> unmappedTargetFields,
            String domainContext) {
        if (unmappedSourceFields == null || unmappedSourceFields.isEmpty() ||
            unmappedTargetFields == null || unmappedTargetFields.isEmpty()) {
            return new AviatorSuggestionResult(List.of(), List.of(), List.of(), List.of());
        }

        log.info("Invoking Aviator ADT to evaluate {} source fields against {} target fields",
                unmappedSourceFields.size(), unmappedTargetFields.size());
        return aviatorClient.requestMappingSuggestions(unmappedSourceFields, unmappedTargetFields, domainContext);
    }

    @Override
    public MappingRuleDto convertNaturalLanguageRule(
            String instruction,
            List<FieldExtractionDto> sourceFields,
            List<FieldExtractionDto> targetFields) {
        log.info("Translating plain-English rule using Aviator: '{}'", instruction);
        return aviatorClient.convertInstructionToRule(instruction, sourceFields, targetFields);
    }

    @Override
    public String explainMapping(MappingRuleDto rule) {
        return rule.getExplanation() != null ? rule.getExplanation() :
                "Deterministic " + rule.getOperation() + " mapping from " + rule.getSourcePaths() + " to " + rule.getTargetPath();
    }

    @Override
    public SchemaChangeAnalysisResponse.FieldChange analyzeFieldChange(String fieldName, String oldType, String newType) {
        return SchemaChangeAnalysisResponse.FieldChange.builder()
                .fieldPath(fieldName)
                .changeType("TYPE_CHANGED")
                .description("Data type changed from " + oldType + " to " + newType)
                .previousValue(oldType)
                .newValue(newType)
                .build();
    }

    @Override
    public AviatorHealthResponse healthCheck() {
        return aviatorClient.checkHealth();
    }
}
