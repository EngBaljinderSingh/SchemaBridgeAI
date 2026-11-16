package com.schemabridge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.dto.*;
import com.schemabridge.exception.ValidationException;
import com.schemabridge.repository.MappingDefinitionRepository;
import com.schemabridge.service.MappingService;
import com.schemabridge.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class SchemaBridgeEndToEndIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private MappingService mappingService;

    @Autowired
    private ObjectMapper objectMapper;

        @Autowired
        private MappingDefinitionRepository mappingRepository;

    @Test
    @DisplayName("End-to-End Flow: Complete integration from project creation to deterministic transformation and publishing")
    void testCompleteIntegrationWorkflow() throws Exception {
        // 1. Create Integration Project
        ProjectCreateRequest projectReq = ProjectCreateRequest.builder()
                .name("HR to Enterprise Directory Integration")
                .description("Synchronizes employees between System A and System B")
                .sourceSystemName("HR_System_A")
                .targetSystemName("Enterprise_Directory_B")
                .build();
        ProjectResponse project = projectService.createProject(projectReq);
        assertNotNull(project.getId());

        // 2. Import Source Schema (Sample JSON from Prompt)
        String sourcePayload = """
                {
                  "name": "Baljinder Singh",
                  "dob": "10/05/1992",
                  "active": "Yes",
                  "project_id": 1001,
                  "contact": {
                    "email": "user@example.com"
                  }
                }
                """;

        SchemaImportRequest srcImport = SchemaImportRequest.builder()
                .direction(Direction.SOURCE_TO_TARGET)
                .schemaName("SourcePayload")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(sourcePayload)
                .build();
        SchemaResponse srcSchema = projectService.importSchema(project.getId(), srcImport);
        assertNotNull(srcSchema.getId());
        assertEquals(5, srcSchema.getFields().size());

        // 3. Import Target Schema (Simplified Schema from Prompt)
        String targetSchema = """
                {
                  "userName": "string",
                  "dateOfBirth": "yyyy-MM-dd",
                  "accountEnabled": "boolean",
                  "projectId": "string",
                  "emailAddress": "string"
                }
                """;

        SchemaImportRequest tgtImport = SchemaImportRequest.builder()
                .direction(Direction.TARGET_TO_SOURCE)
                .schemaName("TargetSchema")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(targetSchema)
                .build();
        SchemaResponse tgtSchema = projectService.importSchema(project.getId(), tgtImport);
        assertNotNull(tgtSchema.getId());
        assertEquals(5, tgtSchema.getFields().size());

        // 4. Generate Mapping Suggestions
        MappingDefinitionDto mapping = mappingService.generateMappingSuggestions(project.getId(), Direction.SOURCE_TO_TARGET);
        assertNotNull(mapping.getId());
        assertFalse(mapping.getRules().isEmpty());
        assertEquals(5, mapping.getRules().size());

        // 5. Approve Mapping
        MappingDefinitionDto approved = mappingService.approveMapping(mapping.getId(), "test-lead");
        assertEquals(MappingStatus.APPROVED, approved.getStatus());

        // 6. Publish Mapping
        MappingDefinitionDto published = mappingService.publishMapping(mapping.getId(), "test-lead");
        assertEquals(MappingStatus.PUBLISHED, published.getStatus());

        // 7. Execute Deterministic Transformation
        TransformationExecuteRequest execReq = TransformationExecuteRequest.builder()
                .mappingVersion(published.getVersion())
                .sourcePayload(objectMapper.readTree(sourcePayload))
                .validateTarget(true)
                .build();

        TransformationExecuteResponse execRes = mappingService.executeTransformation(project.getId(), execReq);
        assertNotNull(execRes.getExecutionId());
        assertEquals("SUCCESS", execRes.getStatus());
        assertTrue(execRes.getValidationResult().isValid());

        JsonNode targetNode = (JsonNode) execRes.getTransformedPayload();
        assertEquals("Baljinder Singh", targetNode.get("userName").asText());
        assertEquals("1992-05-10", targetNode.get("dateOfBirth").asText());
        assertTrue(targetNode.get("accountEnabled").asBoolean());
        assertEquals("1001", targetNode.get("projectId").asText());
        assertEquals("user@example.com", targetNode.get("emailAddress").asText());

        // 8. Test Empty / Duplicate Target Mapping Rejection
        assertThrows(ValidationException.class, () -> {
            mappingService.updateRules(mapping.getId(), List.of(
                    RuleUpdateRequest.builder().sourcePaths(List.of("name")).targetPath("userName").operation(com.schemabridge.domain.enums.TransformationOpType.RENAME).build()
            ));
        }, "Cannot modify a PUBLISHED mapping version directly.");
    }

    @Test
    void ruleEditsPreserveUntouchedIdentityAndProvenance() {
        MappingDefinitionDto mapping = createReviewMapping();
        MappingRuleDto editedRule = mapping.getRules().get(0);
        MappingRuleDto untouchedRule = mapping.getRules().get(1);
        List<RuleUpdateRequest> updates = mapping.getRules().stream().map(this::toUpdate).toList();
        updates.get(0).setExplanation("Reviewed field mapping");

        MappingDefinitionDto saved = mappingService.updateRules(mapping.getId(), updates);
        MappingRuleDto savedEdited = saved.getRules().stream()
                .filter(rule -> rule.getId().equals(editedRule.getId())).findFirst().orElseThrow();
        MappingRuleDto savedUntouched = saved.getRules().stream()
                .filter(rule -> rule.getId().equals(untouchedRule.getId())).findFirst().orElseThrow();

        assertEquals("Reviewed field mapping", savedEdited.getExplanation());
        assertEquals(MappingStatus.SUGGESTED, savedEdited.getStatus());
        assertEquals(untouchedRule.getMatchMethod(), savedUntouched.getMatchMethod());
        assertEquals(0, untouchedRule.getConfidence().compareTo(savedUntouched.getConfidence()));
        assertEquals(untouchedRule.getStatus(), savedUntouched.getStatus());
        assertEquals(MatchMethod.EXACT, savedUntouched.getMatchMethod());
        assertEquals(2, mappingService.getMappingById(mapping.getId()).getRules().size());
        assertEquals(MappingStatus.DRAFT, saved.getStatus());
        assertNull(saved.getApprovedBy());
    }

    @Test
    void ruleUpdatesRejectUnknownAndDuplicateIdentitiesWithoutChangingSavedRules() {
        MappingDefinitionDto mapping = createReviewMapping();
        RuleUpdateRequest unknownRule = toUpdate(mapping.getRules().get(0));
        unknownRule.setId("another-mappings-rule");
        assertThrows(ValidationException.class,
                () -> mappingService.updateRules(mapping.getId(), List.of(unknownRule)));

        RuleUpdateRequest duplicateRule = toUpdate(mapping.getRules().get(0));
        assertThrows(ValidationException.class,
                () -> mappingService.updateRules(mapping.getId(), List.of(duplicateRule, duplicateRule)));
        assertEquals(2, mappingService.getMappingById(mapping.getId()).getRules().size());
    }

    @Test
    void unapprovedMappingsCannotBePublished() {
        MappingDefinitionDto mapping = createReviewMapping();
        assertThrows(ValidationException.class,
                () -> mappingService.publishMapping(mapping.getId(), "reviewer"));
        assertEquals(MappingStatus.REVIEW_REQUIRED, mappingService.getMappingById(mapping.getId()).getStatus());
    }

    @Test
    void publicationRequiresEveryRuleToBeApproved() {
        MappingDefinitionDto mapping = createReviewMapping();
        var entity = mappingRepository.findById(mapping.getId()).orElseThrow();
        entity.setStatus(MappingStatus.APPROVED);
        mappingRepository.saveAndFlush(entity);

        assertThrows(ValidationException.class,
                () -> mappingService.publishMapping(mapping.getId(), "reviewer"));
    }

    @Test
    void runtimeRejectsUnpublishedMappingsWithOrWithoutAnExplicitVersion() {
        MappingDefinitionDto mapping = createReviewMapping();
        TransformationExecuteRequest request = TransformationExecuteRequest.builder()
                .sourcePayload(Map.of("name", "Alice", "email", "alice@example.test"))
                .build();
        assertThrows(ValidationException.class,
                () -> mappingService.executeTransformation(mapping.getProjectId(), request));

        request.setMappingVersion(mapping.getVersion());
        assertThrows(ValidationException.class,
                () -> mappingService.executeTransformation(mapping.getProjectId(), request));
    }

    @Test
    void publishedMappingsCannotBeReopenedThroughApprovalEndpoints() {
        MappingDefinitionDto mapping = createReviewMapping();
        mappingService.approveMapping(mapping.getId(), "reviewer");
                MappingDefinitionDto published = mappingService.publishMapping(mapping.getId(), "publisher");

                assertEquals("reviewer", published.getApprovedBy());
        assertThrows(ValidationException.class,
                () -> mappingService.approveMapping(mapping.getId(), "editor"));
        assertThrows(ValidationException.class,
                () -> mappingService.approveAllHighConfidence(mapping.getId(), "editor"));
        assertEquals(MappingStatus.PUBLISHED, mappingService.getMappingById(mapping.getId()).getStatus());
    }

        @Test
        void removingARulePersistsOnlyTheRetainedIdentityAndClearsApproval() {
                MappingDefinitionDto mapping = createReviewMapping();
                MappingDefinitionDto approved = mappingService.approveMapping(mapping.getId(), "reviewer");
                MappingRuleDto retainedRule = approved.getRules().get(1);

                mappingService.updateRules(mapping.getId(), List.of(toUpdate(retainedRule)));
                MappingDefinitionDto reloaded = mappingService.getMappingById(mapping.getId());

                assertEquals(1, reloaded.getRules().size());
                assertEquals(retainedRule.getId(), reloaded.getRules().get(0).getId());
                assertEquals(MatchMethod.EXACT, reloaded.getRules().get(0).getMatchMethod());
                assertEquals(MappingStatus.DRAFT, reloaded.getStatus());
                assertNull(reloaded.getApprovedBy());
                assertNull(reloaded.getApprovedAt());
        }

        @Test
        void draftMappingsRemainAvailableForPreview() {
                MappingDefinitionDto mapping = createReviewMapping();
                TransformationPreviewResponse preview = mappingService.previewTransformation(mapping.getProjectId(),
                                TransformationPreviewRequest.builder().mappingDefinitionId(mapping.getId())
                                                .sourcePayload(Map.of("name", "Alice", "email", "alice@example.test")).build());

                JsonNode payload = objectMapper.valueToTree(preview.getTransformedPayload());
                assertEquals("Alice", payload.get("name").asText());
                assertTrue(preview.getValidation().isValid());
        }

    private MappingDefinitionDto createReviewMapping() {
        ProjectResponse project = projectService.createProject(ProjectCreateRequest.builder()
                .name("Mapping safety regression").autoApproveEnabled(false).build());
        projectService.importSchema(project.getId(), SchemaImportRequest.builder()
                .direction(Direction.SOURCE_TO_TARGET).schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent("{\"name\":\"Alice\",\"email\":\"alice@example.test\"}").build());
        projectService.importSchema(project.getId(), SchemaImportRequest.builder()
                .direction(Direction.TARGET_TO_SOURCE).schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent("{\"name\":\"string\",\"email\":\"string\"}").build());
        return mappingService.generateMappingSuggestions(project.getId(), Direction.SOURCE_TO_TARGET);
    }

    private RuleUpdateRequest toUpdate(MappingRuleDto rule) {
        return RuleUpdateRequest.builder()
                .id(rule.getId()).sourcePaths(rule.getSourcePaths()).targetPath(rule.getTargetPath())
                .operation(rule.getOperation()).parameters(rule.getParameters())
                .status(rule.getStatus()).explanation(rule.getExplanation()).build();
    }
}
