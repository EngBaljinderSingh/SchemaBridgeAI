package com.schemabridge.service.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.MappingRule;
import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.repository.MappingRuleRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class HistoricalMatchStrategy implements MatchStrategy {

    private final MappingRuleRepository mappingRuleRepository;
    private final ObjectMapper objectMapper;

    public HistoricalMatchStrategy(MappingRuleRepository mappingRuleRepository, ObjectMapper objectMapper) {
        this.mappingRuleRepository = mappingRuleRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public MatchMethod getMethod() {
        return MatchMethod.HISTORICAL;
    }

    @Override
    public int getOrder() {
        return 4;
    }

    @Override
    public List<MappingRuleDto> match(
            List<FieldExtractionDto> availableSources,
            List<FieldExtractionDto> availableTargets,
            Set<String> alreadyMappedTargetPaths,
            String projectId) {

        List<MappingRuleDto> matches = new ArrayList<>();

        for (FieldExtractionDto source : availableSources) {
            for (FieldExtractionDto target : availableTargets) {
                if (alreadyMappedTargetPaths.contains(target.getFieldPath())) {
                    continue;
                }

                List<MappingRule> historicalRules = mappingRuleRepository.findApprovedHistoricalRules(
                        source.getFieldPath(), target.getFieldPath(), MappingStatus.APPROVED);

                if (historicalRules.isEmpty()) {
                    historicalRules = mappingRuleRepository.findApprovedHistoricalRules(
                            source.getFieldPath(), target.getFieldPath(), MappingStatus.PUBLISHED);
                }

                if (!historicalRules.isEmpty()) {
                    MappingRule best = historicalRules.get(0);
                    Map<String, Object> params = new HashMap<>();
                    if (best.getParametersJson() != null && !best.getParametersJson().isBlank()) {
                        try {
                            params = objectMapper.readValue(best.getParametersJson(), new TypeReference<Map<String, Object>>() {});
                        } catch (Exception ignored) {}
                    }

                    matches.add(MappingRuleDto.builder()
                            .sourcePaths(List.of(source.getFieldPath()))
                            .targetPath(target.getFieldPath())
                            .operation(best.getOperation())
                            .parameters(params)
                            .matchMethod(MatchMethod.HISTORICAL)
                            .confidence(BigDecimal.valueOf(0.910))
                            .confidenceLevel(ConfidenceLevel.HIGH)
                            .status(MappingStatus.SUGGESTED)
                            .explanation("Derived from historical approved rule: " + best.getExplanation())
                            .requiresReview(false)
                            .build());

                    alreadyMappedTargetPaths.add(target.getFieldPath());
                    break;
                }
            }
        }

        return matches;
    }
}
