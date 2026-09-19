package com.schemabridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.service.operations.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransformationEngineTest {

    private TransformationEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        List<TransformationOperation> opList = List.of(
                new StandardStringOperations.RenameOperation(),
                new StandardStringOperations.CopyOperation(),
                new StandardStringOperations.ConcatOperation(),
                new TypeConversionOperations.StringToBooleanOperation(),
                new TypeConversionOperations.NumberToStringOperation(),
                new TypeConversionOperations.StringToNumberOperation(),
                new DateFormatOperation(),
                new EnumMapOperation(),
                new StructuralAndConditionalOperations.DefaultValueOperation(),
                new StructuralAndConditionalOperations.ConditionalOperation(),
                new StructuralAndConditionalOperations.RemoveOperation()
        );
        OperationRegistry registry = new OperationRegistry(opList);
        engine = new TransformationEngine(registry, objectMapper);
    }

    @Test
    @DisplayName("Sample Transformation: Translates complete user profile sample payload from prompt")
    void testSampleTransformationFromPrompt() throws Exception {
        String sourceJson = """
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

        List<MappingRuleDto> rules = List.of(
                MappingRuleDto.builder()
                        .sourcePaths(List.of("name"))
                        .targetPath("userName")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("dob"))
                        .targetPath("dateOfBirth")
                        .operation(TransformationOpType.DATE_FORMAT)
                        .parameters(Map.of("sourceFormat", "dd/MM/yyyy", "targetFormat", "yyyy-MM-dd"))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("active"))
                        .targetPath("accountEnabled")
                        .operation(TransformationOpType.STRING_TO_BOOLEAN)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("project_id"))
                        .targetPath("projectId")
                        .operation(TransformationOpType.NUMBER_TO_STRING)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("contact.email"))
                        .targetPath("emailAddress")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build()
        );

        TransformationEngine.ExecutionResult result = engine.executeWithRuleDtos(sourceJson, rules);
        JsonNode output = result.transformedPayload();

        assertNotNull(output);
        assertEquals("Baljinder Singh", output.get("userName").asText());
        assertEquals("1992-05-10", output.get("dateOfBirth").asText());
        assertTrue(output.get("accountEnabled").asBoolean());
        assertEquals("1001", output.get("projectId").asText());
        assertEquals("user@example.com", output.get("emailAddress").asText());
        assertEquals(5, result.appliedRules().size());
    }
}
