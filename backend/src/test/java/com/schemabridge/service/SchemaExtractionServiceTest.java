package com.schemabridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.FieldExtractionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaExtractionServiceTest {

    private SchemaExtractionService service;

    @BeforeEach
    void setUp() {
        service = new SchemaExtractionService(new ObjectMapper());
    }

    private String readResource(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("Resource not found: " + path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Extract fields from Host System A Swagger JSON")
    void testExtractFieldsFromHostSwaggerJson() throws Exception {
        String hostJson = readResource("samples/host-system-a-swagger.json");
        List<FieldExtractionDto> fields = service.extractFields(hostJson, SchemaType.OPENAPI);

        assertThat(fields).isNotEmpty();
        Set<String> fieldPaths = fields.stream().map(FieldExtractionDto::getFieldPath).collect(Collectors.toSet());

        assertThat(fieldPaths).contains(
                "order_num",
                "cust_id",
                "name",
                "dob",
                "active",
                "tier",
                "contact.email",
                "contact.phone",
                "total_amount"
        );

        FieldExtractionDto nameField = fields.stream().filter(f -> f.getFieldPath().equals("name")).findFirst().orElseThrow();
        assertThat(nameField.getDescription()).isEqualTo("Customer full legal name");
        assertThat(nameField.getSampleValue()).isEqualTo("Dr. Sarah Connor");

        FieldExtractionDto emailField = fields.stream().filter(f -> f.getFieldPath().equals("contact.email")).findFirst().orElseThrow();
        assertThat(emailField.getSampleValue()).isEqualTo("sarah.c@cyberdyne.org");
    }

    @Test
    @DisplayName("Extract fields from Destination System B Swagger JSON")
    void testExtractFieldsFromDestinationSwaggerJson() throws Exception {
        String destJson = readResource("samples/destination-system-b-swagger.json");
        List<FieldExtractionDto> fields = service.extractFields(destJson, SchemaType.OPENAPI);

        assertThat(fields).isNotEmpty();
        Set<String> fieldPaths = fields.stream().map(FieldExtractionDto::getFieldPath).collect(Collectors.toSet());

        assertThat(fieldPaths).contains(
                "orderId",
                "customerId",
                "userName",
                "dateOfBirth",
                "accountEnabled",
                "membershipLevel",
                "emailAddress",
                "telephone",
                "totalAmount"
        );

        FieldExtractionDto userField = fields.stream().filter(f -> f.getFieldPath().equals("userName")).findFirst().orElseThrow();
        assertThat(userField.getSampleValue()).isEqualTo("Dr. Sarah Connor");
        assertThat(userField.getDescription()).isEqualTo("Customer display username / full name");
    }

    @Test
    @DisplayName("Parse endpoints catalog from Swagger Spec (100 APIs Picker support)")
    void testExtractEndpointsFromSpec() throws Exception {
        String destJson = readResource("samples/destination-system-b-swagger.json");
        List<com.schemabridge.dto.ApiEndpointSummaryDto> endpoints = service.extractEndpointsFromSpec(destJson);

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints.get(0).getHttpMethod()).isEqualTo("POST");
        assertThat(endpoints.get(0).getEndpointPath()).isEqualTo("/erp/orders");
        assertThat(endpoints.get(0).getSchemaModelName()).isEqualTo("CloudErpOrderRecord");
    }

    @Test
    @DisplayName("Trace live payload and auto-infer schema for system with NO Swagger")
    void testBuildImportRequestFromTrace() {
        String sampleJson = "{\"bookTitle\": \"The Hobbit\", \"authorName\": \"J.R.R. Tolkien\", \"year\": 1937}";
        var req = service.buildImportRequestFromTrace(sampleJson, com.schemabridge.domain.enums.Direction.SOURCE_TO_TARGET, "QatarLegacy", "/books");

        assertThat(req.getSchemaType()).isEqualTo(SchemaType.SAMPLE_JSON);
        assertThat(req.getSystemName()).isEqualTo("QatarLegacy");
        assertThat(req.getSchemaName()).contains("QatarLegacy__books");

        List<FieldExtractionDto> fields = service.extractFields(req.getSchemaContent(), req.getSchemaType());
        assertThat(fields).hasSize(3);
        Set<String> names = fields.stream().map(FieldExtractionDto::getFieldName).collect(Collectors.toSet());
        assertThat(names).contains("bookTitle", "authorName", "year");
    }
}
