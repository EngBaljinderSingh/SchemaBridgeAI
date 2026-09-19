package com.schemabridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.*;
import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.dto.*;
import com.schemabridge.exception.ResourceNotFoundException;
import com.schemabridge.exception.ValidationException;
import com.schemabridge.repository.*;
import com.schemabridge.service.matching.MatchingPipeline;
import com.schemabridge.service.validation.SchemaValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MappingService {

    private static final Logger log = LoggerFactory.getLogger(MappingService.class);

    private final IntegrationProjectRepository projectRepository;
    private final SystemDefinitionRepository systemRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final MappingDefinitionRepository mappingDefinitionRepository;
    private final MappingRuleRepository mappingRuleRepository;
    private final TransformationExecutionRepository executionRepository;
    private final MatchingPipeline matchingPipeline;
    private final TransformationEngine transformationEngine;
    private final SchemaValidationService validationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public MappingService(
            IntegrationProjectRepository projectRepository,
            SystemDefinitionRepository systemRepository,
            SchemaDefinitionRepository schemaRepository,
            MappingDefinitionRepository mappingDefinitionRepository,
            MappingRuleRepository mappingRuleRepository,
            TransformationExecutionRepository executionRepository,
            MatchingPipeline matchingPipeline,
            TransformationEngine transformationEngine,
            SchemaValidationService validationService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.systemRepository = systemRepository;
        this.schemaRepository = schemaRepository;
        this.mappingDefinitionRepository = mappingDefinitionRepository;
        this.mappingRuleRepository = mappingRuleRepository;
        this.executionRepository = executionRepository;
        this.matchingPipeline = matchingPipeline;
        this.transformationEngine = transformationEngine;
        this.validationService = validationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MappingDefinitionDto generateMappingSuggestions(String projectId, Direction direction) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        Direction dir = direction != null ? direction : Direction.SOURCE_TO_TARGET;

        // Fetch source and target system schemas
        SystemDefinition srcSystem = systemRepository.findByProjectIdAndDirection(projectId, Direction.SOURCE_TO_TARGET)
                .orElseThrow(() -> new ValidationException("Source system is not configured for project " + projectId));
        SystemDefinition tgtSystem = systemRepository.findByProjectIdAndDirection(projectId, Direction.TARGET_TO_SOURCE)
                .orElseThrow(() -> new ValidationException("Target system is not configured for project " + projectId));

        SchemaDefinition srcSchema = schemaRepository.findFirstBySystemDefinitionIdOrderByCreatedAtDesc(srcSystem.getId())
                .orElseThrow(() -> new ValidationException("Source schema has not been imported."));
        SchemaDefinition tgtSchema = schemaRepository.findFirstBySystemDefinitionIdOrderByCreatedAtDesc(tgtSystem.getId())
                .orElseThrow(() -> new ValidationException("Target schema has not been imported."));

        List<FieldExtractionDto> srcFields = srcSchema.getFields().stream().map(this::toFieldDto).toList();
        List<FieldExtractionDto> tgtFields = tgtSchema.getFields().stream().map(this::toFieldDto).toList();

        // Run 5-Level Matching Pipeline
        MatchingPipeline.PipelineResult pipelineResult = matchingPipeline.execute(srcFields, tgtFields, projectId, project.getName());

        // Determine next version number
        List<MappingDefinition> existing = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(projectId);
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;

        MappingDefinition mappingDef = MappingDefinition.builder()
                .project(project)
                .direction(dir)
                .version(nextVersion)
                .sourceSchemaId(srcSchema.getId())
                .targetSchemaId(tgtSchema.getId())
                .status(MappingStatus.SUGGESTED)
                .createdBy("system")
                .build();
        mappingDef = mappingDefinitionRepository.save(mappingDef);

        List<MappingRule> ruleEntities = new ArrayList<>();
        for (MappingRuleDto dto : pipelineResult.rules()) {
            MappingRule rule = MappingRule.builder()
                    .mappingDefinition(mappingDef)
                    .sourcePaths(writeJson(dto.getSourcePaths()))
                    .targetPath(dto.getTargetPath())
                    .operation(dto.getOperation())
                    .parametersJson(writeJson(dto.getParameters()))
                    .matchMethod(dto.getMatchMethod())
                    .confidence(dto.getConfidence())
                    .status(dto.getStatus() != null ? dto.getStatus() : MappingStatus.SUGGESTED)
                    .explanation(dto.getExplanation())
                    .build();
            ruleEntities.add(rule);
        }
        mappingDef.getRules().addAll(ruleEntities);
        mappingDef = mappingDefinitionRepository.save(mappingDef);

        auditService.recordEvent("MappingDefinition", mappingDef.getId(), "GENERATE_SUGGESTIONS", "system", null,
                "Generated " + ruleEntities.size() + " mapping rules for v" + nextVersion);

        return mapToMappingDto(mappingDef, pipelineResult.unmappedSourceFields(), pipelineResult.unmappedTargetFields(), pipelineResult.warnings());
    }

    public List<MappingDefinitionDto> getMappingsByProject(String projectId) {
        List<MappingDefinition> list = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(projectId);
        return list.stream().map(m -> mapToMappingDto(m, List.of(), List.of(), List.of())).collect(Collectors.toList());
    }

    public MappingDefinitionDto getMappingById(String mappingId) {
        MappingDefinition mapping = mappingDefinitionRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", mappingId));
        return mapToMappingDto(mapping, List.of(), List.of(), List.of());
    }

    @Transactional
    public MappingDefinitionDto updateRules(String mappingId, List<RuleUpdateRequest> ruleUpdates) {
        MappingDefinition mapping = mappingDefinitionRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", mappingId));

        if (mapping.getStatus() == MappingStatus.PUBLISHED) {
            throw new ValidationException("Cannot modify a PUBLISHED mapping version directly. Clone or create a new version.");
        }

        mapping.getRules().clear();

        List<MappingRule> newRules = new ArrayList<>();
        for (RuleUpdateRequest req : ruleUpdates) {
            MappingRule r = MappingRule.builder()
                    .mappingDefinition(mapping)
                    .sourcePaths(writeJson(req.getSourcePaths()))
                    .targetPath(req.getTargetPath())
                    .operation(req.getOperation())
                    .parametersJson(writeJson(req.getParameters()))
                    .matchMethod(com.schemabridge.domain.enums.MatchMethod.MANUAL)
                    .confidence(BigDecimal.valueOf(1.000))
                    .status(req.getStatus() != null ? req.getStatus() : MappingStatus.APPROVED)
                    .explanation(req.getExplanation() != null ? req.getExplanation() : "User approved rule")
                    .build();
            newRules.add(r);
        }
        mapping.getRules().addAll(newRules);
        mapping.setStatus(MappingStatus.DRAFT);
        mapping = mappingDefinitionRepository.save(mapping);

        auditService.recordEvent("MappingDefinition", mappingId, "UPDATE_RULES", "user", null,
                "Updated " + newRules.size() + " rules for mapping v" + mapping.getVersion());

        return mapToMappingDto(mapping, List.of(), List.of(), List.of());
    }

    @Transactional
    public MappingDefinitionDto addRuleToProject(String projectId, MappingRuleDto ruleDto) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<MappingDefinition> mappings = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(projectId);
        MappingDefinition mapping;

        if (mappings.isEmpty()) {
            mapping = MappingDefinition.builder()
                    .project(project)
                    .direction(Direction.SOURCE_TO_TARGET)
                    .version(1)
                    .status(MappingStatus.DRAFT)
                    .createdBy("system")
                    .build();
            mapping = mappingDefinitionRepository.save(mapping);
        } else {
            mapping = mappings.get(0);
            if (mapping.getStatus() == MappingStatus.PUBLISHED) {
                MappingDefinition newVersion = MappingDefinition.builder()
                        .project(project)
                        .direction(mapping.getDirection())
                        .version(mapping.getVersion() + 1)
                        .sourceSchemaId(mapping.getSourceSchemaId())
                        .targetSchemaId(mapping.getTargetSchemaId())
                        .status(MappingStatus.DRAFT)
                        .createdBy("system")
                        .build();
                newVersion = mappingDefinitionRepository.save(newVersion);
                for (MappingRule oldR : mapping.getRules()) {
                    MappingRule cloned = MappingRule.builder()
                            .mappingDefinition(newVersion)
                            .sourcePaths(oldR.getSourcePaths())
                            .targetPath(oldR.getTargetPath())
                            .operation(oldR.getOperation())
                            .parametersJson(oldR.getParametersJson())
                            .matchMethod(oldR.getMatchMethod())
                            .confidence(oldR.getConfidence())
                            .status(oldR.getStatus())
                            .explanation(oldR.getExplanation())
                            .build();
                    newVersion.getRules().add(cloned);
                }
                mapping = newVersion;
            }
        }

        mapping.getRules().removeIf(r -> r.getTargetPath().equals(ruleDto.getTargetPath()));

        MappingRule newRule = MappingRule.builder()
                .mappingDefinition(mapping)
                .sourcePaths(writeJson(ruleDto.getSourcePaths()))
                .targetPath(ruleDto.getTargetPath())
                .operation(ruleDto.getOperation())
                .parametersJson(writeJson(ruleDto.getParameters()))
                .matchMethod(ruleDto.getMatchMethod() != null ? ruleDto.getMatchMethod() : com.schemabridge.domain.enums.MatchMethod.MANUAL)
                .confidence(ruleDto.getConfidence() != null ? ruleDto.getConfidence() : BigDecimal.valueOf(1.000))
                .status(MappingStatus.APPROVED)
                .explanation(ruleDto.getExplanation() != null ? ruleDto.getExplanation() : "Custom rule")
                .build();

        mapping.getRules().add(newRule);
        mapping = mappingDefinitionRepository.save(mapping);

        auditService.recordEvent("MappingDefinition", mapping.getId(), "ADD_CUSTOM_RULE", "user", null,
                "Added rule for target " + ruleDto.getTargetPath() + " with " + ruleDto.getOperation());

        return mapToMappingDto(mapping, List.of(), List.of(), List.of());
    }

    @Transactional
    public MappingDefinitionDto approveMapping(String mappingId, String approvedBy) {
        MappingDefinition mapping = mappingDefinitionRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", mappingId));

        mapping.setStatus(MappingStatus.APPROVED);
        mapping.setApprovedBy(approvedBy != null ? approvedBy : "admin");
        mapping.setApprovedAt(LocalDateTime.now());
        mappingDefinitionRepository.save(mapping);

        // Mark all rules as APPROVED
        for (MappingRule r : mapping.getRules()) {
            r.setStatus(MappingStatus.APPROVED);
        }
        mappingRuleRepository.saveAll(mapping.getRules());

        auditService.recordEvent("MappingDefinition", mappingId, "APPROVE", approvedBy, null,
                "Approved mapping version v" + mapping.getVersion());

        return mapToMappingDto(mapping, List.of(), List.of(), List.of());
    }

    @Transactional
    public MappingDefinitionDto publishMapping(String mappingId, String publishedBy) {
        MappingDefinition mapping = mappingDefinitionRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", mappingId));

        // Validation before publishing
        List<MappingRule> rules = mapping.getRules();
        if (rules.isEmpty()) {
            throw new ValidationException("Cannot publish an empty mapping definition.");
        }

        // Verify required target fields are mapped
        if (mapping.getTargetSchemaId() != null) {
            schemaRepository.findById(mapping.getTargetSchemaId()).ifPresent(tgtSchema -> {
                Set<String> mappedTargets = rules.stream().map(MappingRule::getTargetPath).collect(Collectors.toSet());
                List<String> missingRequired = tgtSchema.getFields().stream()
                        .filter(SchemaField::isRequired)
                        .map(SchemaField::getFieldPath)
                        .filter(p -> !mappedTargets.contains(p))
                        .toList();
                if (!missingRequired.isEmpty()) {
                    throw new ValidationException("Cannot publish mapping: Required target fields are not mapped: " + missingRequired);
                }
            });
        }

        // Check for duplicate target mappings
        Set<String> seenTargets = new HashSet<>();
        for (MappingRule r : rules) {
            if (!seenTargets.add(r.getTargetPath())) {
                throw new ValidationException("Duplicate target mapping detected for: " + r.getTargetPath());
            }
        }

        mapping.setStatus(MappingStatus.PUBLISHED);
        mapping.setApprovedBy(publishedBy != null ? publishedBy : "admin");
        mapping.setApprovedAt(LocalDateTime.now());
        mappingDefinitionRepository.save(mapping);

        auditService.recordEvent("MappingDefinition", mappingId, "PUBLISH", publishedBy, null,
                "Published mapping version v" + mapping.getVersion());

        return mapToMappingDto(mapping, List.of(), List.of(), List.of());
    }

    @Transactional
    public MappingDefinitionDto cloneMapping(String mappingId) {
        MappingDefinition original = mappingDefinitionRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", mappingId));

        List<MappingDefinition> existing = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(original.getProject().getId());
        int nextVersion = existing.get(0).getVersion() + 1;

        MappingDefinition clone = MappingDefinition.builder()
                .project(original.getProject())
                .direction(original.getDirection())
                .version(nextVersion)
                .sourceSchemaId(original.getSourceSchemaId())
                .targetSchemaId(original.getTargetSchemaId())
                .status(MappingStatus.DRAFT)
                .createdBy("cloned_from_v" + original.getVersion())
                .build();
        clone = mappingDefinitionRepository.save(clone);

        List<MappingRule> clonedRules = new ArrayList<>();
        for (MappingRule origRule : original.getRules()) {
            MappingRule r = MappingRule.builder()
                    .mappingDefinition(clone)
                    .sourcePaths(origRule.getSourcePaths())
                    .targetPath(origRule.getTargetPath())
                    .operation(origRule.getOperation())
                    .parametersJson(origRule.getParametersJson())
                    .matchMethod(origRule.getMatchMethod())
                    .confidence(origRule.getConfidence())
                    .status(MappingStatus.SUGGESTED)
                    .explanation("Cloned from v" + original.getVersion() + ": " + origRule.getExplanation())
                    .build();
            clonedRules.add(r);
        }
        clone.getRules().addAll(clonedRules);
        clone = mappingDefinitionRepository.save(clone);

        auditService.recordEvent("MappingDefinition", clone.getId(), "CLONE", "system", original.getId(),
                "Cloned v" + original.getVersion() + " to new version v" + nextVersion);

        return mapToMappingDto(clone, List.of(), List.of(), List.of());
    }

    /**
     * Preview transformation without persisting execution records.
     */
    public TransformationPreviewResponse previewTransformation(String projectId, TransformationPreviewRequest req) {
        List<MappingRuleDto> rulesToExecute = new ArrayList<>();

        if (req.getAdhocRules() != null && !req.getAdhocRules().isEmpty()) {
            rulesToExecute = req.getAdhocRules();
        } else if (req.getMappingDefinitionId() != null) {
            MappingDefinition mapping = mappingDefinitionRepository.findById(req.getMappingDefinitionId())
                    .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition", req.getMappingDefinitionId()));
            rulesToExecute = mapping.getRules().stream().map(this::toRuleDto).toList();
        } else {
            // Use latest mapping version
            List<MappingDefinition> mappings = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(projectId);
            if (!mappings.isEmpty()) {
                rulesToExecute = mappings.get(0).getRules().stream().map(this::toRuleDto).toList();
            }
        }

        TransformationEngine.ExecutionResult execResult =
                transformationEngine.executeWithRuleDtos(req.getSourcePayload(), rulesToExecute);

        // Validate target output against target schema
        ValidationResultDto validationResult = ValidationResultDto.builder().valid(true).build();
        Optional<SystemDefinition> tgtSystem = systemRepository.findByProjectIdAndDirection(projectId, Direction.TARGET_TO_SOURCE);
        if (tgtSystem.isPresent()) {
            Optional<SchemaDefinition> tgtSchema = schemaRepository.findFirstBySystemDefinitionIdOrderByCreatedAtDesc(tgtSystem.get().getId());
            if (tgtSchema.isPresent()) {
                validationResult = validationService.validate(tgtSchema.get(), execResult.transformedPayload());
            }
        }

        return TransformationPreviewResponse.builder()
                .transformedPayload(execResult.transformedPayload())
                .validation(validationResult)
                .appliedRules(execResult.appliedRules())
                .executionTimeMs(execResult.durationMs())
                .build();
    }

    /**
     * Executes deterministic transformation against an approved/published mapping version.
     */
    @Transactional
    public TransformationExecuteResponse executeTransformation(String projectId, TransformationExecuteRequest req) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        MappingDefinition mapping;
        if (req.getMappingVersion() != null) {
            mapping = mappingDefinitionRepository.findByProjectIdAndVersion(projectId, req.getMappingVersion())
                    .orElseThrow(() -> new ResourceNotFoundException("MappingDefinition version " + req.getMappingVersion(), projectId));
        } else {
            mapping = mappingDefinitionRepository.findFirstByProjectIdAndDirectionAndStatusOrderByVersionDesc(
                    projectId, Direction.SOURCE_TO_TARGET, MappingStatus.PUBLISHED)
                    .orElseGet(() -> {
                        List<MappingDefinition> any = mappingDefinitionRepository.findByProjectIdOrderByVersionDesc(projectId);
                        if (any.isEmpty()) throw new ValidationException("No mappings exist for project " + projectId);
                        return any.get(0);
                    });
        }

        // Execute deterministic transformation
        TransformationEngine.ExecutionResult execResult =
                transformationEngine.executeWithEntities(req.getSourcePayload(), mapping.getRules());

        // Validate output against target schema
        ValidationResultDto valResult = ValidationResultDto.builder().valid(true).build();
        if (mapping.getTargetSchemaId() != null) {
            Optional<SchemaDefinition> tgtSchema = schemaRepository.findById(mapping.getTargetSchemaId());
            if (tgtSchema.isPresent()) {
                valResult = validationService.validate(tgtSchema.get(), execResult.transformedPayload());
            }
        }

        String status = valResult.isValid() ? "SUCCESS" : "VALIDATION_WARNING";

        // Persist transformation execution record
        TransformationExecution execution = TransformationExecution.builder()
                .project(project)
                .mappingDefinition(mapping)
                .mappingVersion(mapping.getVersion())
                .status(status)
                .validationResult(writeJson(valResult))
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        execution = executionRepository.save(execution);

        auditService.recordEvent("TransformationExecution", execution.getId(), "EXECUTE", "system", null,
                "Executed transformation v" + mapping.getVersion() + " with status=" + status);

        return TransformationExecuteResponse.builder()
                .executionId(execution.getId())
                .status(status)
                .mappingVersion(mapping.getVersion())
                .transformedPayload(execResult.transformedPayload())
                .validationResult(valResult)
                .appliedRules(execResult.appliedRules())
                .executionDurationMs(execResult.durationMs())
                .build();
    }

    private FieldExtractionDto toFieldDto(SchemaField f) {
        return FieldExtractionDto.builder()
                .id(f.getId())
                .fieldPath(f.getFieldPath())
                .fieldName(f.getFieldName())
                .description(f.getDescription())
                .dataType(f.getDataType())
                .format(f.getFormat())
                .required(f.isRequired())
                .array(f.isArray())
                .parentPath(f.getParentPath())
                .build();
    }

    private MappingRuleDto toRuleDto(MappingRule r) {
        List<String> sources = Collections.emptyList();
        if (r.getSourcePaths() != null && !r.getSourcePaths().isBlank()) {
            try {
                sources = objectMapper.readValue(r.getSourcePaths(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                sources = List.of(r.getSourcePaths());
            }
        }
        Map<String, Object> params = Collections.emptyMap();
        if (r.getParametersJson() != null && !r.getParametersJson().isBlank()) {
            try {
                params = objectMapper.readValue(r.getParametersJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        double confVal = r.getConfidence() != null ? r.getConfidence().doubleValue() : 0.8;
        ConfidenceLevel confLevel = confVal >= 0.90 ? ConfidenceLevel.HIGH :
                confVal >= 0.70 ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;

        return MappingRuleDto.builder()
                .id(r.getId())
                .sourcePaths(sources)
                .targetPath(r.getTargetPath())
                .operation(r.getOperation())
                .parameters(params)
                .matchMethod(r.getMatchMethod())
                .confidence(r.getConfidence())
                .confidenceLevel(confLevel)
                .status(r.getStatus())
                .explanation(r.getExplanation())
                .requiresReview(confLevel != ConfidenceLevel.HIGH)
                .build();
    }

    private MappingDefinitionDto mapToMappingDto(
            MappingDefinition m, List<String> unmappedSources, List<String> unmappedTargets, List<String> warnings) {
        List<MappingRuleDto> ruleDtos = m.getRules().stream().map(this::toRuleDto).toList();
        return MappingDefinitionDto.builder()
                .id(m.getId())
                .projectId(m.getProject().getId())
                .direction(m.getDirection())
                .version(m.getVersion())
                .sourceSchemaId(m.getSourceSchemaId())
                .targetSchemaId(m.getTargetSchemaId())
                .status(m.getStatus())
                .createdBy(m.getCreatedBy())
                .approvedBy(m.getApprovedBy())
                .createdAt(m.getCreatedAt())
                .approvedAt(m.getApprovedAt())
                .rules(ruleDtos)
                .unmappedSourceFields(unmappedSources)
                .unmappedTargetFields(unmappedTargets)
                .warnings(warnings)
                .build();
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
