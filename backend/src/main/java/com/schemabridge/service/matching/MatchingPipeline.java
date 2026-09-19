package com.schemabridge.service.matching;

import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.service.aviator.AviatorMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingPipeline {

    private static final Logger log = LoggerFactory.getLogger(MatchingPipeline.class);

    private final List<MatchStrategy> strategies;
    private final AviatorMappingService aviatorMappingService;
    private final double highConfidenceThreshold;
    private final double mediumConfidenceThreshold;

    public MatchingPipeline(
            List<MatchStrategy> strategyList,
            AviatorMappingService aviatorMappingService,
            @Value("${schemabridge.matching.thresholds.high-confidence:0.90}") double highConfidenceThreshold,
            @Value("${schemabridge.matching.thresholds.medium-confidence:0.70}") double mediumConfidenceThreshold) {
        this.strategies = strategyList.stream()
                .sorted(Comparator.comparingInt(MatchStrategy::getOrder))
                .collect(Collectors.toList());
        this.aviatorMappingService = aviatorMappingService;
        this.highConfidenceThreshold = highConfidenceThreshold;
        this.mediumConfidenceThreshold = mediumConfidenceThreshold;
    }

    public record PipelineResult(
            List<MappingRuleDto> rules,
            List<String> unmappedSourceFields,
            List<String> unmappedTargetFields,
            List<String> warnings
    ) {}

    public PipelineResult execute(
            List<FieldExtractionDto> sourceFields,
            List<FieldExtractionDto> targetFields,
            String projectId,
            String domainContext) {

        List<MappingRuleDto> accumulatedRules = new ArrayList<>();
        Set<String> mappedSourcePaths = new HashSet<>();
        Set<String> mappedTargetPaths = new HashSet<>();
        List<String> warnings = new ArrayList<>();

        log.info("Starting 5-level matching pipeline: {} source fields, {} target fields",
                sourceFields.size(), targetFields.size());

        // Run Levels 1 to 4 (Deterministic Match Strategies)
        for (MatchStrategy strategy : strategies) {
            List<FieldExtractionDto> remainingSources = sourceFields.stream()
                    .filter(s -> !mappedSourcePaths.contains(s.getFieldPath()))
                    .collect(Collectors.toList());

            List<FieldExtractionDto> remainingTargets = targetFields.stream()
                    .filter(t -> !mappedTargetPaths.contains(t.getFieldPath()))
                    .collect(Collectors.toList());

            if (remainingSources.isEmpty() || remainingTargets.isEmpty()) {
                break;
            }

            List<MappingRuleDto> matches = strategy.match(remainingSources, remainingTargets, mappedTargetPaths, projectId);
            for (MappingRuleDto rule : matches) {
                accumulatedRules.add(rule);
                if (rule.getSourcePaths() != null) {
                    mappedSourcePaths.addAll(rule.getSourcePaths());
                }
                mappedTargetPaths.add(rule.getTargetPath());
                log.debug("Level {} ({}) matched: {} -> {}",
                        strategy.getOrder(), strategy.getMethod(), rule.getSourcePaths(), rule.getTargetPath());
            }
        }

        // Run Level 5: AI Semantic Matching via OpenText Aviator ADT for unresolved fields
        List<FieldExtractionDto> unmappedSources = sourceFields.stream()
                .filter(s -> !mappedSourcePaths.contains(s.getFieldPath()))
                .collect(Collectors.toList());

        List<FieldExtractionDto> unmappedTargets = targetFields.stream()
                .filter(t -> !mappedTargetPaths.contains(t.getFieldPath()))
                .collect(Collectors.toList());

        if (!unmappedSources.isEmpty() && !unmappedTargets.isEmpty()) {
            log.info("Delegating {} unresolved source fields and {} target fields to Aviator Level 5",
                    unmappedSources.size(), unmappedTargets.size());

            try {
                AviatorMappingService.AviatorSuggestionResult aiResult =
                        aviatorMappingService.suggestMappings(unmappedSources, unmappedTargets, domainContext);

                if (aiResult != null && aiResult.suggestedRules() != null) {
                    for (MappingRuleDto aiRule : aiResult.suggestedRules()) {
                        if (!mappedTargetPaths.contains(aiRule.getTargetPath())) {
                            accumulatedRules.add(aiRule);
                            if (aiRule.getSourcePaths() != null) {
                                mappedSourcePaths.addAll(aiRule.getSourcePaths());
                            }
                            mappedTargetPaths.add(aiRule.getTargetPath());
                        } else {
                            warnings.add("Conflict: multiple sources proposed for target " + aiRule.getTargetPath());
                        }
                    }
                    if (aiResult.warnings() != null) {
                        warnings.addAll(aiResult.warnings());
                    }
                }
            } catch (Exception e) {
                log.warn("Aviator AI semantic matching encountered an issue: {}. Deterministic mappings preserved.", e.getMessage());
                warnings.add("Aviator ADT was unable to resolve remaining fields: " + e.getMessage());
            }
        }

        // Compute final unmapped sets
        List<String> finalUnmappedSources = sourceFields.stream()
                .map(FieldExtractionDto::getFieldPath)
                .filter(p -> !mappedSourcePaths.contains(p))
                .collect(Collectors.toList());

        List<String> finalUnmappedTargets = targetFields.stream()
                .map(FieldExtractionDto::getFieldPath)
                .filter(p -> !mappedTargetPaths.contains(p))
                .collect(Collectors.toList());

        // Assign confidence level and review requirements to all rules
        for (MappingRuleDto rule : accumulatedRules) {
            double score = rule.getConfidence() != null ? rule.getConfidence().doubleValue() : 0.8;
            ConfidenceLevel level = ConfidenceLevel.fromScore(score, highConfidenceThreshold, mediumConfidenceThreshold);
            rule.setConfidenceLevel(level);

            if (level == ConfidenceLevel.MEDIUM || level == ConfidenceLevel.LOW) {
                rule.setRequiresReview(true);
            }
        }

        log.info("Matching pipeline completed with {} rules. Unmapped sources: {}, Unmapped targets: {}",
                accumulatedRules.size(), finalUnmappedSources.size(), finalUnmappedTargets.size());

        return new PipelineResult(accumulatedRules, finalUnmappedSources, finalUnmappedTargets, warnings);
    }
}
