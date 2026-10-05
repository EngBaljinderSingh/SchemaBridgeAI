package com.schemabridge.service;

import com.schemabridge.domain.*;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.ProjectStatus;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.domain.enums.SystemType;
import com.schemabridge.dto.*;
import com.schemabridge.exception.ResourceNotFoundException;
import com.schemabridge.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final IntegrationProjectRepository projectRepository;
    private final SystemDefinitionRepository systemRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final SchemaFieldRepository fieldRepository;
    private final MappingDefinitionRepository mappingDefinitionRepository;
    private final SchemaExtractionService schemaExtractionService;
    private final AuditService auditService;

    public ProjectService(
            IntegrationProjectRepository projectRepository,
            SystemDefinitionRepository systemRepository,
            SchemaDefinitionRepository schemaRepository,
            SchemaFieldRepository fieldRepository,
            MappingDefinitionRepository mappingDefinitionRepository,
            SchemaExtractionService schemaExtractionService,
            AuditService auditService) {
        this.projectRepository = projectRepository;
        this.systemRepository = systemRepository;
        this.schemaRepository = schemaRepository;
        this.fieldRepository = fieldRepository;
        this.mappingDefinitionRepository = mappingDefinitionRepository;
        this.schemaExtractionService = schemaExtractionService;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest req) {
        String approver = (req.getApproverEmail() != null && !req.getApproverEmail().isBlank())
                ? req.getApproverEmail().trim()
                : "approver@enterprise.com";

        IntegrationProject project = IntegrationProject.builder()
                .name(req.getName())
                .description(req.getDescription())
                .status(ProjectStatus.ACTIVE)
                .approverEmail(approver)
                .autoApproveEnabled(req.isAutoApproveEnabled())
                .build();
        project = projectRepository.save(project);

        // Auto-create source and target systems if names supplied
        String srcName = req.getSourceSystemName() != null ? req.getSourceSystemName() : "Source System";
        SystemDefinition srcSystem = SystemDefinition.builder()
                .project(project)
                .systemName(srcName)
                .systemType(SystemType.REST_API)
                .direction(Direction.SOURCE_TO_TARGET)
                .schemaType(SchemaType.JSON_SCHEMA)
                .build();
        systemRepository.save(srcSystem);

        String tgtName = req.getTargetSystemName() != null ? req.getTargetSystemName() : "Target System";
        SystemDefinition tgtSystem = SystemDefinition.builder()
                .project(project)
                .systemName(tgtName)
                .systemType(SystemType.REST_API)
                .direction(Direction.TARGET_TO_SOURCE)
                .schemaType(SchemaType.JSON_SCHEMA)
                .build();
        systemRepository.save(tgtSystem);

        auditService.recordEvent("IntegrationProject", project.getId(), "CREATE", "system", null, project.getName());
        return mapToResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(String id) {
        IntegrationProject project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        return mapToResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(String id, ProjectCreateRequest req) {
        IntegrationProject project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        String oldName = project.getName();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project = projectRepository.save(project);

        auditService.recordEvent("IntegrationProject", id, "UPDATE", "system", oldName, req.getName());
        return mapToResponse(project);
    }

    @Transactional
    public void deleteProject(String id) {
        IntegrationProject project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
        String name = project.getName();
        projectRepository.delete(project);
        auditService.recordEvent("IntegrationProject", id, "DELETE", "system", name, null);
    }

    @Transactional
    public SchemaResponse importSchema(String projectId, SchemaImportRequest req) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        // Find or create SystemDefinition for the specified direction
        SystemDefinition system = systemRepository.findByProjectIdAndDirection(projectId, req.getDirection())
                .orElseGet(() -> {
                    String defaultName = req.getDirection() == Direction.SOURCE_TO_TARGET ? "Source System" : "Target System";
                    SystemDefinition newSys = SystemDefinition.builder()
                            .project(project)
                            .systemName(req.getSystemName() != null ? req.getSystemName() : defaultName)
                            .systemType(SystemType.REST_API)
                            .direction(req.getDirection())
                            .schemaType(req.getSchemaType())
                            .build();
                    return systemRepository.save(newSys);
                });

        if (req.getSystemName() != null && !req.getSystemName().isBlank()) {
            system.setSystemName(req.getSystemName());
            system.setSchemaType(req.getSchemaType());
            systemRepository.save(system);
        }

        String hash = schemaExtractionService.computeSha256(req.getSchemaContent());
        String schemaName = req.getSchemaName() != null ? req.getSchemaName() :
                (req.getDirection() == Direction.SOURCE_TO_TARGET ? "SourceSchema" : "TargetSchema");
        String version = req.getSchemaVersion() != null ? req.getSchemaVersion() : "1.0";

        SchemaDefinition schemaDef = SchemaDefinition.builder()
                .systemDefinition(system)
                .schemaName(schemaName)
                .schemaVersion(version)
                .schemaType(req.getSchemaType())
                .originalSchema(req.getSchemaContent())
                .schemaHash(hash)
                .status("ACTIVE")
                .build();
        schemaDef = schemaRepository.save(schemaDef);

        // Extract fields
        List<FieldExtractionDto> fieldDtos = schemaExtractionService.extractFields(req.getSchemaContent(), req.getSchemaType());
        List<SchemaField> fieldEntities = schemaExtractionService.convertToEntities(fieldDtos, schemaDef);
        schemaDef.getFields().addAll(fieldEntities);
        schemaDef = schemaRepository.save(schemaDef);

        auditService.recordEvent("SchemaDefinition", schemaDef.getId(), "IMPORT", "system", null,
                "Imported " + req.getDirection() + " schema (" + fieldEntities.size() + " fields)");

        return mapToSchemaResponse(schemaDef, system, fieldDtos);
    }

    @Transactional(readOnly = true)
    public List<SchemaResponse> getSchemasByProject(String projectId) {
        List<SystemDefinition> systems = systemRepository.findByProjectId(projectId);
        List<SchemaResponse> responses = new ArrayList<>();

        for (SystemDefinition sys : systems) {
            Optional<SchemaDefinition> schemaOpt = schemaRepository.findFirstBySystemDefinitionIdOrderByCreatedAtDesc(sys.getId());
            if (schemaOpt.isPresent()) {
                SchemaDefinition sd = schemaOpt.get();
                List<FieldExtractionDto> fieldDtos = sd.getFields().stream()
                        .map(f -> FieldExtractionDto.builder()
                                .id(f.getId())
                                .fieldPath(f.getFieldPath())
                                .fieldName(f.getFieldName())
                                .description(f.getDescription())
                                .dataType(f.getDataType())
                                .format(f.getFormat())
                                .required(f.isRequired())
                                .array(f.isArray())
                                .parentPath(f.getParentPath())
                                .build())
                        .collect(Collectors.toList());
                responses.add(mapToSchemaResponse(sd, sys, fieldDtos));
            }
        }
        return responses;
    }

    @Transactional
    public SchemaResponse importSchemaWithEndpoints(String projectId, EndpointSelectionImportRequest req) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        Direction dir = req.getDirection() != null ? req.getDirection() : Direction.TARGET_TO_SOURCE;
        SystemDefinition system = systemRepository.findByProjectIdAndDirection(projectId, dir)
                .orElseGet(() -> {
                    String defaultName = dir == Direction.SOURCE_TO_TARGET ? "Source System" : "Target System";
                    SystemDefinition newSys = SystemDefinition.builder()
                            .project(project)
                            .systemName(req.getSystemName() != null ? req.getSystemName() : defaultName)
                            .systemType(SystemType.REST_API)
                            .direction(dir)
                            .schemaType(SchemaType.OPENAPI)
                            .build();
                    return systemRepository.save(newSys);
                });

        if (req.getSystemName() != null && !req.getSystemName().isBlank()) {
            system.setSystemName(req.getSystemName());
            systemRepository.save(system);
        }

        String hash = schemaExtractionService.computeSha256(req.getRawSpecContent());
        int count = req.getSelectedEndpointPaths() != null ? req.getSelectedEndpointPaths().size() : 0;
        String schemaName = (req.getSystemName() != null ? req.getSystemName() : "Spec") +
                (count > 0 ? " (" + count + " APIs Selected)" : " (All APIs)");

        SchemaDefinition schemaDef = SchemaDefinition.builder()
                .systemDefinition(system)
                .schemaName(schemaName)
                .schemaVersion("1.0")
                .schemaType(SchemaType.OPENAPI)
                .originalSchema(req.getRawSpecContent())
                .schemaHash(hash)
                .status("ACTIVE")
                .build();
        schemaDef = schemaRepository.save(schemaDef);

        List<FieldExtractionDto> fieldDtos = schemaExtractionService.extractFieldsForEndpoints(
                req.getRawSpecContent(), req.getSelectedEndpointPaths());
        List<SchemaField> fieldEntities = schemaExtractionService.convertToEntities(fieldDtos, schemaDef);
        schemaDef.getFields().addAll(fieldEntities);
        schemaDef = schemaRepository.save(schemaDef);

        auditService.recordEvent("SchemaDefinition", schemaDef.getId(), "IMPORT_SELECTED_APIS", "system", null,
                "Imported " + count + " selected APIs (" + fieldEntities.size() + " fields)");

        return mapToSchemaResponse(schemaDef, system, fieldDtos);
    }

    private ProjectResponse mapToResponse(IntegrationProject p) {
        List<SystemDefinition> systems = systemRepository.findByProjectId(p.getId());
        List<MappingDefinition> mappings = mappingDefinitionRepository.findByProjectId(p.getId());
        Integer latestPublished = mappings.stream()
                .filter(m -> m.getStatus() == com.schemabridge.domain.enums.MappingStatus.PUBLISHED)
                .map(MappingDefinition::getVersion)
                .max(Integer::compareTo)
                .orElse(null);

        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .approverEmail(p.getApproverEmail())
                .autoApproveEnabled(p.isAutoApproveEnabled())
                .systemCount(systems.size())
                .mappingVersionCount(mappings.size())
                .latestPublishedVersion(latestPublished)
                .build();
    }

    private SchemaResponse mapToSchemaResponse(SchemaDefinition sd, SystemDefinition sys, List<FieldExtractionDto> fields) {
        return SchemaResponse.builder()
                .id(sd.getId())
                .systemDefinitionId(sys.getId())
                .systemName(sys.getSystemName())
                .direction(sys.getDirection())
                .schemaName(sd.getSchemaName())
                .schemaVersion(sd.getSchemaVersion())
                .schemaType(sd.getSchemaType())
                .schemaHash(sd.getSchemaHash())
                .originalSchema(sd.getOriginalSchema())
                .createdAt(sd.getCreatedAt())
                .fields(fields)
                .build();
    }
}
