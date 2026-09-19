package com.schemabridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schemabridge.domain.MappingRule;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.exception.TransformationException;
import com.schemabridge.service.operations.OperationRegistry;
import com.schemabridge.service.operations.StructuralAndConditionalOperations;
import com.schemabridge.service.operations.TransformationOperation;
import com.schemabridge.util.JsonPathTreeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TransformationEngine {

    private static final Logger log = LoggerFactory.getLogger(TransformationEngine.class);
    private final OperationRegistry operationRegistry;
    private final ObjectMapper objectMapper;

    public TransformationEngine(OperationRegistry operationRegistry, ObjectMapper objectMapper) {
        this.operationRegistry = operationRegistry;
        this.objectMapper = objectMapper;
    }

    public record ExecutionResult(ObjectNode transformedPayload, List<String> appliedRules, long durationMs) {}

    /**
     * Executes deterministic transformation against an input source payload using rule DTOs.
     */
    public ExecutionResult executeWithRuleDtos(Object sourcePayload, List<MappingRuleDto> rules) {
        long start = System.currentTimeMillis();
        JsonNode sourceRoot = convertToJsonNode(sourcePayload);
        ObjectNode targetRoot = objectMapper.createObjectNode();
        List<String> appliedRules = new ArrayList<>();

        if (rules == null || rules.isEmpty()) {
            return new ExecutionResult(targetRoot, appliedRules, System.currentTimeMillis() - start);
        }

        for (MappingRuleDto rule : rules) {
            applySingleRule(sourceRoot, targetRoot, rule.getSourcePaths(), rule.getTargetPath(),
                    rule.getOperation(), rule.getParameters(), appliedRules);
        }

        long duration = System.currentTimeMillis() - start;
        log.debug("Executed {} transformation rules in {}ms", appliedRules.size(), duration);
        return new ExecutionResult(targetRoot, appliedRules, duration);
    }

    /**
     * Executes deterministic transformation against an input source payload using JPA MappingRules.
     */
    public ExecutionResult executeWithEntities(Object sourcePayload, List<MappingRule> rules) {
        long start = System.currentTimeMillis();
        JsonNode sourceRoot = convertToJsonNode(sourcePayload);
        ObjectNode targetRoot = objectMapper.createObjectNode();
        List<String> appliedRules = new ArrayList<>();

        if (rules == null || rules.isEmpty()) {
            return new ExecutionResult(targetRoot, appliedRules, System.currentTimeMillis() - start);
        }

        for (MappingRule rule : rules) {
            List<String> sourcePaths = parseSourcePaths(rule.getSourcePaths());
            Map<String, Object> parameters = parseParameters(rule.getParametersJson());

            applySingleRule(sourceRoot, targetRoot, sourcePaths, rule.getTargetPath(),
                    rule.getOperation(), parameters, appliedRules);
        }

        long duration = System.currentTimeMillis() - start;
        return new ExecutionResult(targetRoot, appliedRules, duration);
    }

    private void applySingleRule(JsonNode sourceRoot, ObjectNode targetRoot, List<String> sourcePaths,
                                 String targetPath, TransformationOpType opType, Map<String, Object> parameters,
                                 List<String> appliedRules) {
        try {
            List<Object> sourceValues = new ArrayList<>();
            if (sourcePaths != null) {
                for (String path : sourcePaths) {
                    Object val = JsonPathTreeUtils.extractValue(sourceRoot, path);
                    sourceValues.add(val);
                }
            }

            TransformationOperation op = operationRegistry.getOperation(opType);
            Object transformedValue = op.apply(sourceValues, parameters != null ? parameters : Collections.emptyMap());

            if (Objects.equals(transformedValue, StructuralAndConditionalOperations.RemoveOperation.REMOVE_MARKER)) {
                log.trace("Rule skipped target field {} due to REMOVE operation", targetPath);
                return;
            }

            JsonPathTreeUtils.setValue(targetRoot, targetPath, transformedValue);
            appliedRules.add(opType.name() + ": " + (sourcePaths != null ? String.join(", ", sourcePaths) : "") + " -> " + targetPath);

        } catch (Exception e) {
            log.error("Failed executing rule {} -> {}: {}", sourcePaths, targetPath, e.getMessage(), e);
            throw new TransformationException("Failed executing rule " + sourcePaths + " -> " + targetPath + ": " + e.getMessage(), e);
        }
    }

    private JsonNode convertToJsonNode(Object sourcePayload) {
        if (sourcePayload instanceof JsonNode jn) {
            return jn;
        }
        if (sourcePayload instanceof String str) {
            try {
                return objectMapper.readTree(str);
            } catch (Exception e) {
                throw new TransformationException("Invalid JSON source payload string: " + e.getMessage(), e);
            }
        }
        return objectMapper.valueToTree(sourcePayload);
    }

    private List<String> parseSourcePaths(String sourcePathsJson) {
        if (sourcePathsJson == null || sourcePathsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(sourcePathsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(sourcePathsJson);
        }
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
