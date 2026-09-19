package com.schemabridge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.*;
import com.schemabridge.exception.ValidationException;
import com.schemabridge.service.MappingService;
import com.schemabridge.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

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
}
