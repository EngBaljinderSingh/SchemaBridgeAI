package com.schemabridge.controller;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.dto.*;
import com.schemabridge.service.MappingService;
import com.schemabridge.service.ProjectService;
import com.schemabridge.service.aviator.AviatorMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/mappings")
@Tag(name = "Mapping Definitions", description = "AI-assisted mapping generation, review, approval, and publication")
public class MappingController {

    private final MappingService mappingService;
    private final ProjectService projectService;
    private final AviatorMappingService aviatorMappingService;

    public MappingController(
            MappingService mappingService,
            ProjectService projectService,
            AviatorMappingService aviatorMappingService) {
        this.mappingService = mappingService;
        this.projectService = projectService;
        this.aviatorMappingService = aviatorMappingService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate 5-level mapping suggestions (including Aviator semantic matching)")
    public ResponseEntity<MappingDefinitionDto> generateMappings(
            @PathVariable String projectId,
            @RequestParam(required = false, defaultValue = "SOURCE_TO_TARGET") Direction direction) {
        MappingDefinitionDto response = mappingService.generateMappingSuggestions(projectId, direction);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all mapping definitions and versions for a project")
    public ResponseEntity<List<MappingDefinitionDto>> getMappings(@PathVariable String projectId) {
        return ResponseEntity.ok(mappingService.getMappingsByProject(projectId));
    }

    @GetMapping("/{mappingId}")
    @Operation(summary = "Get mapping details by ID")
    public ResponseEntity<MappingDefinitionDto> getMappingById(
            @PathVariable String projectId,
            @PathVariable String mappingId) {
        return ResponseEntity.ok(mappingService.getMappingById(mappingId));
    }

    @PutMapping("/{mappingId}/rules")
    @Operation(summary = "Update rules in a draft mapping")
    public ResponseEntity<MappingDefinitionDto> updateRules(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @Valid @RequestBody List<RuleUpdateRequest> rules) {
        return ResponseEntity.ok(mappingService.updateRules(mappingId, rules));
    }

    @PostMapping("/{mappingId}/approve")
    @Operation(summary = "Approve a mapping version")
    public ResponseEntity<MappingDefinitionDto> approveMapping(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @RequestParam(required = false, defaultValue = "admin") String approvedBy) {
        return ResponseEntity.ok(mappingService.approveMapping(mappingId, approvedBy));
    }

    @PostMapping("/{mappingId}/approve-all")
    @Operation(summary = "Approve all rules and mark mapping approved")
    public ResponseEntity<MappingDefinitionDto> approveAll(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @RequestParam(required = false, defaultValue = "admin") String approvedBy) {
        return ResponseEntity.ok(mappingService.approveMapping(mappingId, approvedBy));
    }

    @PostMapping("/{mappingId}/approve-all-high-confidence")
    @Operation(summary = "Approve only high-confidence rules (confidence >= 90%)")
    public ResponseEntity<MappingDefinitionDto> approveAllHighConfidence(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @RequestParam(required = false, defaultValue = "admin") String approvedBy) {
        return ResponseEntity.ok(mappingService.approveAllHighConfidence(mappingId, approvedBy));
    }

    @PostMapping("/{mappingId}/invert")
    @Operation(summary = "Synthesize reverse mapping (target -> source) from this mapping version")
    public ResponseEntity<MappingDefinitionDto> invertMapping(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @RequestParam(required = false, defaultValue = "system (inverted)") String createdBy) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingService.invertMapping(mappingId, createdBy));
    }

    @PostMapping("/{mappingId}/publish")
    @Operation(summary = "Publish an approved mapping version for deterministic execution")
    public ResponseEntity<MappingDefinitionDto> publishMapping(
            @PathVariable String projectId,
            @PathVariable String mappingId,
            @RequestParam(required = false, defaultValue = "admin") String publishedBy) {
        return ResponseEntity.ok(mappingService.publishMapping(mappingId, publishedBy));
    }

    @PostMapping("/{mappingId}/clone")
    @Operation(summary = "Clone an existing mapping into a new editable draft version")
    public ResponseEntity<MappingDefinitionDto> cloneMapping(
            @PathVariable String projectId,
            @PathVariable String mappingId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingService.cloneMapping(mappingId));
    }

    @PostMapping("/custom-rule")
    @Operation(summary = "Add or update a custom rule in the project's active mapping")
    public ResponseEntity<MappingDefinitionDto> addCustomRule(
            @PathVariable String projectId,
            @Valid @RequestBody MappingRuleDto ruleDto) {
        return ResponseEntity.ok(mappingService.addRuleToProject(projectId, ruleDto));
    }

    @PostMapping("/natural-language")
    @Operation(summary = "Translate a plain-English instruction into a structured mapping rule using Aviator")
    public ResponseEntity<MappingRuleDto> convertNaturalLanguageRule(
            @PathVariable String projectId,
            @RequestParam(required = false, defaultValue = "false") boolean autoSave,
            @Valid @RequestBody NaturalLanguageRuleRequest request) {
        List<SchemaResponse> schemas = projectService.getSchemasByProject(projectId);
        List<FieldExtractionDto> srcFields = List.of();
        List<FieldExtractionDto> tgtFields = List.of();

        for (SchemaResponse s : schemas) {
            if (s.getDirection() == Direction.SOURCE_TO_TARGET) {
                srcFields = s.getFields();
            } else if (s.getDirection() == Direction.TARGET_TO_SOURCE) {
                tgtFields = s.getFields();
            }
        }

        MappingRuleDto rule = aviatorMappingService.convertNaturalLanguageRule(
                request.getInstruction(), srcFields, tgtFields);

        if (autoSave) {
            mappingService.addRuleToProject(projectId, rule);
        }

        return ResponseEntity.ok(rule);
    }
}
