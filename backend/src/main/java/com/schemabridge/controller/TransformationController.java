package com.schemabridge.controller;

import com.schemabridge.domain.SchemaDefinition;
import com.schemabridge.dto.*;
import com.schemabridge.repository.SchemaDefinitionRepository;
import com.schemabridge.service.MappingService;
import com.schemabridge.service.validation.SchemaValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Transformation & Validation", description = "Deterministic payload translation and schema validation")
public class TransformationController {

    private final MappingService mappingService;
    private final SchemaValidationService validationService;
    private final SchemaDefinitionRepository schemaRepository;

    public TransformationController(
            MappingService mappingService,
            SchemaValidationService validationService,
            SchemaDefinitionRepository schemaRepository) {
        this.mappingService = mappingService;
        this.validationService = validationService;
        this.schemaRepository = schemaRepository;
    }

    @PostMapping("/transform/preview")
    @Operation(summary = "Preview transformation of sample source payload using draft or saved rules")
    public ResponseEntity<TransformationPreviewResponse> previewTransformation(
            @PathVariable String projectId,
            @Valid @RequestBody TransformationPreviewRequest request) {
        return ResponseEntity.ok(mappingService.previewTransformation(projectId, request));
    }

    @PostMapping("/transform/execute")
    @Operation(summary = "Execute deterministic transformation against approved mapping rules and validate output")
    public ResponseEntity<TransformationExecuteResponse> executeTransformation(
            @PathVariable String projectId,
            @Valid @RequestBody TransformationExecuteRequest request) {
        return ResponseEntity.ok(mappingService.executeTransformation(projectId, request));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a payload directly against a schema definition")
    public ResponseEntity<ValidationResultDto> validatePayload(
            @PathVariable String projectId,
            @Valid @RequestBody ValidationRequest request) {
        SchemaDefinition schema = null;
        if (request.getSchemaDefinitionId() != null) {
            schema = schemaRepository.findById(request.getSchemaDefinitionId()).orElse(null);
        }
        return ResponseEntity.ok(validationService.validate(schema, request.getPayload()));
    }
}
