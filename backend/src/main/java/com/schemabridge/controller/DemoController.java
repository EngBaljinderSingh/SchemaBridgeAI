package com.schemabridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.*;
import com.schemabridge.service.MappingService;
import com.schemabridge.service.ProjectService;
import com.schemabridge.service.TransformationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Live Demo & System Simulation", description = "Pre-configured bi-directional demo endpoints between System A (Legacy) and System B (Modern Cloud)")
public class DemoController {

    private final TransformationEngine transformationEngine;
    private final ProjectService projectService;
    private final MappingService mappingService;
    private final ObjectMapper objectMapper;

    public DemoController(
            TransformationEngine transformationEngine,
            ProjectService projectService,
            MappingService mappingService,
            ObjectMapper objectMapper) {
        this.transformationEngine = transformationEngine;
        this.projectService = projectService;
        this.mappingService = mappingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns full metadata, schemas, and explanations for the System A <-> System B demo.
     */
    @GetMapping("/scenario")
    @Operation(summary = "Get System A and System B demo scenario specifications")
    public ResponseEntity<Map<String, Object>> getScenario() {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("title", "Enterprise System A <-> System B Bi-Directional Integration");
        scenario.put("systemA", Map.of(
                "name", "System A (Legacy Core POS & Billing)",
                "role", "Source System for Writes / Client for Reads",
                "description", "On-premise core billing system using legacy flat JSON, snake_case identifiers, DD/MM/YYYY dates, and string flags.",
                "samplePayload", getSampleSystemAPayload()
        ));
        scenario.put("systemB", Map.of(
                "name", "System B (Modern Cloud ERP / Salesforce)",
                "role", "Target Enterprise System of Record",
                "description", "Cloud enterprise platform with strict typed schemas, camelCase identifiers, ISO-8601 dates, and enum codes.",
                "samplePayload", getSampleSystemBPayload()
        ));
        scenario.put("writeFlowExplanation", List.of(
                "1. [Action] System A sends customer/order data in its native legacy format.",
                "2. [Routing] Instead of calling System B directly (which would reject the payload), System A calls SchemaBridge AI.",
                "3. [Deterministic Execution] SchemaBridge AI executes 9 stored transformation rules in pure Java (<5ms):",
                "     - order_num -> orderId (RENAME)",
                "     - cust_id -> customerId (RENAME)",
                "     - name -> userName (RENAME / Synonym Match)",
                "     - dob -> dateOfBirth (DATE_FORMAT: 'dd/MM/yyyy' -> 'yyyy-MM-dd')",
                "     - active -> accountEnabled (STRING_TO_BOOLEAN: 'Yes' -> true)",
                "     - tier -> membershipLevel (ENUM_MAP: 'GOLD_TIER' -> 'GLD')",
                "     - contact.email -> emailAddress (FLATTEN)",
                "     - contact.phone -> telephone (FLATTEN)",
                "     - total_amount -> totalAmount (RENAME)",
                "4. [Validation] SchemaBridge AI validates the payload against System B's JSON schema.",
                "5. [Delivery] SchemaBridge AI delivers the compliant payload to System B, which creates the record and acknowledges receipt."
        ));
        scenario.put("readFlowExplanation", List.of(
                "1. [Action] System A requests customer/order details from System B via SchemaBridge AI.",
                "2. [Cloud Response] System B responds with its modern enterprise payload.",
                "3. [Reverse Transformation] SchemaBridge AI applies the reverse deterministic mapping in pure Java (<5ms):",
                "     - orderId -> order_num (RENAME)",
                "     - customerId -> cust_id (RENAME)",
                "     - userName -> name (RENAME)",
                "     - dateOfBirth -> dob (DATE_FORMAT: 'yyyy-MM-dd' -> 'dd/MM/yyyy')",
                "     - accountEnabled -> active (BOOLEAN_TO_STRING: true -> 'Yes')",
                "     - membershipLevel -> tier (ENUM_MAP: 'GLD' -> 'GOLD_TIER')",
                "     - emailAddress -> contact.email (NEST)",
                "     - telephone -> contact.phone (NEST)",
                "     - totalAmount -> total_amount (RENAME)",
                "4. [Outcome] System A receives the exact format its legacy codebase expects, with ZERO modifications required to System A."
        ));
        return ResponseEntity.ok(scenario);
    }

    /**
     * Returns sample OpenAPI 3.0 / Swagger specification for Host System (System A - Legacy Core POS).
     */
    @GetMapping(value = "/swagger/host", produces = "application/json")
    @Operation(summary = "Get sample OpenAPI 3.0 / Swagger specification for Host System (System A)")
    public ResponseEntity<String> getHostSystemSwagger() {
        return ResponseEntity.ok(loadSampleSwaggerResource("samples/host-system-a-swagger.json"));
    }

    /**
     * Returns sample OpenAPI 3.0 / Swagger specification for Destination System (System B - Modern Cloud ERP).
     */
    @GetMapping(value = "/swagger/destination", produces = "application/json")
    @Operation(summary = "Get sample OpenAPI 3.0 / Swagger specification for Destination System (System B)")
    public ResponseEntity<String> getDestinationSystemSwagger() {
        return ResponseEntity.ok(loadSampleSwaggerResource("samples/destination-system-b-swagger.json"));
    }

    /**
     * Returns both sample Swagger specifications and metadata for easy UI demo loading.
     */
    @GetMapping("/swagger")
    @Operation(summary = "Get both Host (System A) and Destination (System B) sample Swagger specifications")
    public ResponseEntity<Map<String, Object>> getAllSampleSwaggers() {
        Map<String, Object> resp = new LinkedHashMap<>();
        String hostJson = loadSampleSwaggerResource("samples/host-system-a-swagger.json");
        String destJson = loadSampleSwaggerResource("samples/destination-system-b-swagger.json");
        resp.put("host", Map.of(
                "systemName", "System A (Legacy Core POS & Billing)",
                "direction", "SOURCE_TO_TARGET",
                "schemaType", "OPENAPI",
                "swaggerJson", hostJson
        ));
        resp.put("destination", Map.of(
                "systemName", "System B (Modern Cloud ERP / Salesforce)",
                "direction", "TARGET_TO_SOURCE",
                "schemaType", "OPENAPI",
                "swaggerJson", destJson
        ));
        return ResponseEntity.ok(resp);
    }

    /**
     * WRITE DEMO: System A writes data meant for System B.
     * SchemaBridge intercepts, transforms deterministically into System B format, validates, and simulates System B receipt.
     */
    @PostMapping("/write")
    @Operation(summary = "Execute System A -> System B (Write) deterministic transformation")
    public ResponseEntity<Map<String, Object>> executeWriteDemo(@RequestBody(required = false) Map<String, Object> customInput) {
        Object inputPayload = (customInput != null && !customInput.isEmpty()) ? customInput : getSampleSystemAPayload();

        List<MappingRuleDto> writeRules = getSystemAToSystemBRules();
        TransformationEngine.ExecutionResult result = transformationEngine.executeWithRuleDtos(inputPayload, writeRules);

        Map<String, Object> systemBReceipt = new LinkedHashMap<>();
        systemBReceipt.put("status", "SUCCESS_201_CREATED");
        systemBReceipt.put("systemB_record_id", "SF-CLOUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        systemBReceipt.put("timestamp", Instant.now().toString());
        systemBReceipt.put("message", "Payload accepted and persisted into System B (Salesforce / Cloud ERP).");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation", "WRITE (System A -> SchemaBridge AI -> System B)");
        response.put("systemA_input", inputPayload);
        response.put("schemaBridge_transformation", Map.of(
                "executionEngine", "Deterministic Pure-Java Engine (Zero AI in runtime path)",
                "durationMs", result.durationMs(),
                "rulesAppliedCount", result.appliedRules().size(),
                "appliedRules", result.appliedRules(),
                "status", "SUCCESS"
        ));
        response.put("systemB_received_payload", result.transformedPayload());
        response.put("systemB_mock_acknowledgement", systemBReceipt);

        return ResponseEntity.ok(response);
    }

    /**
     * READ DEMO: System A queries data from System B.
     * System B returns its modern format. SchemaBridge intercepts, transforms reverse deterministically into System A format.
     */
    @PostMapping("/read")
    @Operation(summary = "Execute System B -> System A (Read) reverse deterministic transformation")
    public ResponseEntity<Map<String, Object>> executeReadDemo(@RequestBody(required = false) Map<String, Object> customRecord) {
        Object systemBRecord = (customRecord != null && !customRecord.isEmpty()) ? customRecord : getSampleSystemBPayload();

        List<MappingRuleDto> readRules = getSystemBToSystemARules();
        TransformationEngine.ExecutionResult result = transformationEngine.executeWithRuleDtos(systemBRecord, readRules);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operation", "READ (System B -> SchemaBridge AI -> System A)");
        response.put("systemB_original_record", systemBRecord);
        response.put("schemaBridge_reverse_transformation", Map.of(
                "executionEngine", "Deterministic Pure-Java Engine (Zero AI in runtime path)",
                "durationMs", result.durationMs(),
                "rulesAppliedCount", result.appliedRules().size(),
                "appliedRules", result.appliedRules(),
                "status", "SUCCESS"
        ));
        response.put("systemA_received_payload", result.transformedPayload());
        response.put("systemA_outcome", "Legacy System A parsed the data successfully without requiring code changes.");

        return ResponseEntity.ok(response);
    }

    /**
     * Seeds the demo project into the database so it appears in the Web UI workspace.
     */
    @PostMapping("/seed")
    @Operation(summary = "Seed the System A <-> System B project into SchemaBridge database")
    public ResponseEntity<Map<String, Object>> seedDemoProject() {
        String sourceJson = writeJsonString(getSampleSystemAPayload());
        String targetJson = writeJsonString(getSampleSystemBPayload());

        // 1. Create project
        ProjectCreateRequest req = ProjectCreateRequest.builder()
                .name("Demo: Legacy POS (System A) <-> Cloud ERP (System B)")
                .description("Bi-directional enterprise integration translating legacy snake_case, slash-dates, and string booleans to cloud enterprise schemas.")
                .sourceSystemName("System A (Legacy POS)")
                .targetSystemName("System B (Cloud ERP)")
                .build();
        ProjectResponse project = projectService.createProject(req);

        // 2. Import Source Schema
        SchemaImportRequest srcImport = SchemaImportRequest.builder()
                .direction(Direction.SOURCE_TO_TARGET)
                .systemName("System A (Legacy POS)")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(sourceJson)
                .schemaName("System_A_Order_Schema")
                .build();
        projectService.importSchema(project.getId(), srcImport);

        // 3. Import Target Schema
        SchemaImportRequest tgtImport = SchemaImportRequest.builder()
                .direction(Direction.TARGET_TO_SOURCE)
                .systemName("System B (Cloud ERP)")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(targetJson)
                .schemaName("System_B_Order_Schema")
                .build();
        projectService.importSchema(project.getId(), tgtImport);

        // 4. Generate mapping draft
        MappingDefinitionDto draft = mappingService.generateMappingSuggestions(project.getId(), Direction.SOURCE_TO_TARGET);

        // 5. Update draft with complete rules
        List<RuleUpdateRequest> updateRules = getSystemAToSystemBRules().stream()
                .map(r -> RuleUpdateRequest.builder()
                        .sourcePaths(r.getSourcePaths())
                        .targetPath(r.getTargetPath())
                        .operation(r.getOperation())
                        .parameters(r.getParameters())
                        .explanation("Deterministic rule for " + r.getTargetPath())
                        .build())
                .toList();
        mappingService.updateRules(draft.getId(), updateRules);

        // 6. Approve & Publish
        mappingService.approveMapping(draft.getId(), "Demo Admin");
        MappingDefinitionDto published = mappingService.publishMapping(draft.getId(), "Demo Admin");

        return ResponseEntity.ok(Map.of(
                "message", "Demo project successfully seeded into database and ready for UI demo!",
                "projectId", project.getId(),
                "publishedVersion", published.getVersion(),
                "rulesCount", published.getRules().size()
        ));
    }

    // Helper builders
    private Map<String, Object> getSampleSystemAPayload() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("order_num", "ORD-2026-991");
        a.put("cust_id", "CUST-4081");
        a.put("name", "Dr. Sarah Connor");
        a.put("dob", "15/08/1988");
        a.put("active", "Yes");
        a.put("tier", "GOLD_TIER");
        a.put("contact", Map.of("email", "sarah.c@cyberdyne.org", "phone", "+1-555-0199"));
        a.put("total_amount", 499.99);
        return a;
    }

    private Map<String, Object> getSampleSystemBPayload() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("orderId", "ORD-2026-991");
        b.put("customerId", "CUST-4081");
        b.put("userName", "Dr. Sarah Connor");
        b.put("dateOfBirth", "1988-08-15");
        b.put("accountEnabled", true);
        b.put("membershipLevel", "GLD");
        b.put("emailAddress", "sarah.c@cyberdyne.org");
        b.put("telephone", "+1-555-0199");
        b.put("totalAmount", 499.99);
        return b;
    }

    private List<MappingRuleDto> getSystemAToSystemBRules() {
        return List.of(
                MappingRuleDto.builder()
                        .sourcePaths(List.of("order_num"))
                        .targetPath("orderId")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("cust_id"))
                        .targetPath("customerId")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
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
                        .parameters(Map.of("trueValues", List.of("Yes", "Y", "true", "1")))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("tier"))
                        .targetPath("membershipLevel")
                        .operation(TransformationOpType.ENUM_MAP)
                        .parameters(Map.of("mappings", Map.of(
                                "GOLD_TIER", "GLD",
                                "SILVER_TIER", "SLV",
                                "PLATINUM_TIER", "PLT"
                        )))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("contact.email"))
                        .targetPath("emailAddress")
                        .operation(TransformationOpType.FLATTEN)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("contact.phone"))
                        .targetPath("telephone")
                        .operation(TransformationOpType.FLATTEN)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("total_amount"))
                        .targetPath("totalAmount")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build()
        );
    }

    private List<MappingRuleDto> getSystemBToSystemARules() {
        return List.of(
                MappingRuleDto.builder()
                        .sourcePaths(List.of("orderId"))
                        .targetPath("order_num")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("customerId"))
                        .targetPath("cust_id")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("userName"))
                        .targetPath("name")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("dateOfBirth"))
                        .targetPath("dob")
                        .operation(TransformationOpType.DATE_FORMAT)
                        .parameters(Map.of("sourceFormat", "yyyy-MM-dd", "targetFormat", "dd/MM/yyyy"))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("accountEnabled"))
                        .targetPath("active")
                        .operation(TransformationOpType.BOOLEAN_TO_STRING)
                        .parameters(Map.of("trueString", "Yes", "falseString", "No"))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("membershipLevel"))
                        .targetPath("tier")
                        .operation(TransformationOpType.ENUM_MAP)
                        .parameters(Map.of("mappings", Map.of(
                                "GLD", "GOLD_TIER",
                                "SLV", "SILVER_TIER",
                                "PLT", "PLATINUM_TIER"
                        )))
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("emailAddress"))
                        .targetPath("contact.email")
                        .operation(TransformationOpType.NEST)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("telephone"))
                        .targetPath("contact.phone")
                        .operation(TransformationOpType.NEST)
                        .parameters(Map.of())
                        .build(),
                MappingRuleDto.builder()
                        .sourcePaths(List.of("totalAmount"))
                        .targetPath("total_amount")
                        .operation(TransformationOpType.RENAME)
                        .parameters(Map.of())
                        .build()
        );
    }

    private String loadSampleSwaggerResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // fallback
        }
        return "{}";
    }

    private String writeJsonString(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
