package com.schemabridge.service.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.SchemaField;
import com.schemabridge.dto.ValidationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidationServiceTest {

    private SchemaValidationService validationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validationService = new SchemaValidationService(objectMapper);
    }

    @Test
    @DisplayName("Validation: Valid transformed payload satisfies target schema")
    void testValidPayload() {
        String payload = """
                {
                  "userName": "Baljinder Singh",
                  "dateOfBirth": "1992-05-10",
                  "accountEnabled": true,
                  "projectId": "1001",
                  "emailAddress": "user@example.com"
                }
                """;

        List<SchemaField> fields = List.of(
                SchemaField.builder().fieldPath("userName").dataType("string").required(true).build(),
                SchemaField.builder().fieldPath("dateOfBirth").dataType("date").format("yyyy-MM-dd").required(true).build(),
                SchemaField.builder().fieldPath("accountEnabled").dataType("boolean").required(true).build(),
                SchemaField.builder().fieldPath("projectId").dataType("string").required(true).build(),
                SchemaField.builder().fieldPath("emailAddress").dataType("string").format("email").required(true).build()
        );

        ValidationResultDto result = validationService.validate(null, fields, payload);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Validation: Detects missing required field and invalid date format")
    void testInvalidPayload() {
        String invalidPayload = """
                {
                  "dateOfBirth": "10/05/1992",
                  "accountEnabled": true,
                  "emailAddress": "invalid-email"
                }
                """;

        List<SchemaField> fields = List.of(
                SchemaField.builder().fieldPath("userName").dataType("string").required(true).build(),
                SchemaField.builder().fieldPath("dateOfBirth").dataType("date").format("yyyy-MM-dd").required(true).build(),
                SchemaField.builder().fieldPath("emailAddress").dataType("string").format("email").required(true).build()
        );

        ValidationResultDto result = validationService.validate(null, fields, invalidPayload);
        assertFalse(result.isValid());
        assertEquals(3, result.getErrors().size()); // missing userName, invalid date, invalid email
    }
}
