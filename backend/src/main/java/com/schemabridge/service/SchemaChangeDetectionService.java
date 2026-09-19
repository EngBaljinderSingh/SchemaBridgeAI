package com.schemabridge.service;

import com.schemabridge.domain.MappingRule;
import com.schemabridge.domain.SchemaDefinition;
import com.schemabridge.domain.SchemaField;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.SchemaChangeAnalysisResponse;
import com.schemabridge.repository.MappingRuleRepository;
import com.schemabridge.repository.SchemaDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaChangeDetectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeDetectionService.class);

    private final SchemaExtractionService schemaExtractionService;
    private final SchemaDefinitionRepository schemaDefinitionRepository;
    private final MappingRuleRepository mappingRuleRepository;

    public SchemaChangeDetectionService(
            SchemaExtractionService schemaExtractionService,
            SchemaDefinitionRepository schemaDefinitionRepository,
            MappingRuleRepository mappingRuleRepository) {
        this.schemaExtractionService = schemaExtractionService;
        this.schemaDefinitionRepository = schemaDefinitionRepository;
        this.mappingRuleRepository = mappingRuleRepository;
    }

    public SchemaChangeAnalysisResponse analyzeChange(String systemDefinitionId, String newSchemaContent, SchemaType schemaType) {
        String newHash = schemaExtractionService.computeSha256(newSchemaContent);
        List<FieldExtractionDto> newFields = schemaExtractionService.extractFields(newSchemaContent, schemaType);

        Optional<SchemaDefinition> currentSchemaOpt =
                schemaDefinitionRepository.findFirstBySystemDefinitionIdOrderByCreatedAtDesc(systemDefinitionId);

        if (currentSchemaOpt.isEmpty()) {
            return SchemaChangeAnalysisResponse.builder()
                    .newSchemaHash(newHash)
                    .hasChanges(true)
                    .recommendations(List.of("Initial schema version registered."))
                    .build();
        }

        SchemaDefinition currentSchema = currentSchemaOpt.get();
        String oldHash = currentSchema.getSchemaHash();
        List<SchemaField> oldFields = currentSchema.getFields();

        if (oldHash.equals(newHash)) {
            return SchemaChangeAnalysisResponse.builder()
                    .previousSchemaHash(oldHash)
                    .newSchemaHash(newHash)
                    .hasChanges(false)
                    .recommendations(List.of("Schema content is identical; no changes detected."))
                    .build();
        }

        Map<String, SchemaField> oldMap = new HashMap<>();
        for (SchemaField of : oldFields) {
            oldMap.put(of.getFieldPath(), of);
        }

        Map<String, FieldExtractionDto> newMap = new HashMap<>();
        for (FieldExtractionDto nf : newFields) {
            newMap.put(nf.getFieldPath(), nf);
        }

        List<SchemaChangeAnalysisResponse.FieldChange> added = new ArrayList<>();
        List<SchemaChangeAnalysisResponse.FieldChange> removed = new ArrayList<>();
        List<SchemaChangeAnalysisResponse.FieldChange> modified = new ArrayList<>();

        // Detect Added and Modified fields
        for (FieldExtractionDto nf : newFields) {
            if (!oldMap.containsKey(nf.getFieldPath())) {
                added.add(SchemaChangeAnalysisResponse.FieldChange.builder()
                        .fieldPath(nf.getFieldPath())
                        .changeType("ADDED")
                        .description("Field '" + nf.getFieldPath() + "' was added")
                        .newValue(nf.getDataType())
                        .build());
            } else {
                SchemaField of = oldMap.get(nf.getFieldPath());
                if (!Objects.equals(of.getDataType(), nf.getDataType())) {
                    modified.add(SchemaChangeAnalysisResponse.FieldChange.builder()
                            .fieldPath(nf.getFieldPath())
                            .changeType("TYPE_CHANGED")
                            .description("Data type changed from " + of.getDataType() + " to " + nf.getDataType())
                            .previousValue(of.getDataType())
                            .newValue(nf.getDataType())
                            .build());
                }
                if (of.isRequired() != nf.isRequired()) {
                    modified.add(SchemaChangeAnalysisResponse.FieldChange.builder()
                            .fieldPath(nf.getFieldPath())
                            .changeType("REQUIRED_CHANGED")
                            .description("Required status changed from " + of.isRequired() + " to " + nf.isRequired())
                            .previousValue(String.valueOf(of.isRequired()))
                            .newValue(String.valueOf(nf.isRequired()))
                            .build());
                }
            }
        }

        // Detect Removed fields
        for (SchemaField of : oldFields) {
            if (!newMap.containsKey(of.getFieldPath())) {
                removed.add(SchemaChangeAnalysisResponse.FieldChange.builder()
                        .fieldPath(of.getFieldPath())
                        .changeType("REMOVED")
                        .description("Field '" + of.getFieldPath() + "' was removed")
                        .previousValue(of.getDataType())
                        .build());
            }
        }

        // Identify Impacted Rules
        List<SchemaChangeAnalysisResponse.ImpactedRule> impactedRules = new ArrayList<>();
        Set<String> removedPaths = new HashSet<>();
        for (SchemaChangeAnalysisResponse.FieldChange fc : removed) {
            removedPaths.add(fc.getFieldPath());
        }

        // Find rules referencing removed or modified paths
        for (String rem : removedPaths) {
            impactedRules.add(SchemaChangeAnalysisResponse.ImpactedRule.builder()
                    .ruleId("rule-affected-" + rem)
                    .targetPath(rem)
                    .impactType("BROKEN_PATH")
                    .suggestedRemedy("Field '" + rem + "' has been removed. Remap to a newly added field or assign default value.")
                    .build());
        }

        List<String> recommendations = new ArrayList<>();
        if (!added.isEmpty()) {
            recommendations.add(added.size() + " new fields added. Run AI-assisted mapping to find matching target fields.");
        }
        if (!removed.isEmpty()) {
            recommendations.add(removed.size() + " fields removed. Review affected mapping rules before publishing next version.");
        }
        if (!modified.isEmpty()) {
            recommendations.add(modified.size() + " fields modified. Verify date/number/boolean conversion operations.");
        }

        return SchemaChangeAnalysisResponse.builder()
                .previousSchemaHash(oldHash)
                .newSchemaHash(newHash)
                .hasChanges(true)
                .addedFields(added)
                .removedFields(removed)
                .modifiedFields(modified)
                .impactedRules(impactedRules)
                .recommendations(recommendations)
                .build();
    }
}
