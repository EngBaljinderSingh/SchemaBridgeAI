package com.schemabridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.IntegrationProject;
import com.schemabridge.domain.MappingDefinition;
import com.schemabridge.domain.MappingRule;
import com.schemabridge.domain.RegisteredApi;
import com.schemabridge.domain.enums.*;
import com.schemabridge.dto.*;
import com.schemabridge.repository.GatewayExecutionLogRepository;
import com.schemabridge.repository.IntegrationProjectRepository;
import com.schemabridge.repository.MappingDefinitionRepository;
import com.schemabridge.repository.RegisteredApiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DemoProjectsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoProjectsSeeder.class);

    @Value("${schemabridge.demo.auto-seed:true}")
    private boolean autoSeedEnabled;

    private final IntegrationProjectRepository projectRepository;
    private final MappingDefinitionRepository mappingRepository;
    private final RegisteredApiRepository apiRepository;
    private final GatewayExecutionLogRepository gatewayLogRepository;
    private final ProjectService projectService;
    private final GatewayProxyService gatewayProxyService;
    private final ObjectMapper objectMapper;

    public DemoProjectsSeeder(
            IntegrationProjectRepository projectRepository,
            MappingDefinitionRepository mappingRepository,
            RegisteredApiRepository apiRepository,
            GatewayExecutionLogRepository gatewayLogRepository,
            ProjectService projectService,
            GatewayProxyService gatewayProxyService,
            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.mappingRepository = mappingRepository;
        this.apiRepository = apiRepository;
        this.gatewayLogRepository = gatewayLogRepository;
        this.projectService = projectService;
        this.gatewayProxyService = gatewayProxyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoSeedEnabled) {
            return;
        }
        try {
            ensureSampleProjectsSeeded(false);
        } catch (Exception e) {
            log.warn("Auto-seeding sample projects skipped or partially completed: {}", e.getMessage());
        }
    }

    /**
     * Ensures both enterprise sample projects (1. SEtoCM3 and 2. LegacyPOS-to-CloudERP)
     * exist with full 3-URL configuration, schemas, published forward & reverse mappings,
     * registered APIs (including unmapped routes), and all 6 gateway execution/error log cases.
     */
    @Transactional
    public Map<String, Object> ensureSampleProjectsSeeded(boolean forceReset) {
        List<IntegrationProject> allProjects = projectRepository.findAll();

        if (forceReset) {
            for (IntegrationProject p : allProjects) {
                if (p.getName().toLowerCase().contains("setocm3")
                        || p.getName().toLowerCase().contains("legacypos")
                        || p.getName().toLowerCase().contains("legacy pos")) {
                    projectRepository.delete(p);
                }
            }
            projectRepository.flush();
            allProjects = projectRepository.findAll();
        }

        IntegrationProject seToCm3 = allProjects.stream()
                .filter(p -> p.getName().toLowerCase().contains("setocm3"))
                .findFirst()
                .orElse(null);

        IntegrationProject posToErp = allProjects.stream()
                .filter(p -> p.getName().toLowerCase().contains("legacypos") || p.getName().toLowerCase().contains("legacy pos"))
                .findFirst()
                .orElse(null);

        String seProjectId;
        if (seToCm3 == null) {
            seProjectId = buildSeToCm3Project(null);
        } else {
            seProjectId = upgradeExistingSeToCm3IfNeeded(seToCm3);
        }

        String posProjectId;
        if (posToErp == null) {
            posProjectId = buildPosToErpProject();
        } else {
            posProjectId = upgradeExistingPosToErpIfNeeded(posToErp);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "SEEDED_2_COMPLETE_PROJECTS_WITH_ALL_6_CASES");
        summary.put("project1_SEtoCM3", seProjectId);
        summary.put("project2_LegacyPOS_to_CloudERP", posProjectId);
        return summary;
    }

    private String upgradeExistingSeToCm3IfNeeded(IntegrationProject existing) {
        existing.setDescription("Enterprise SaaS Subscription & Metering Integration: Translates OpenText Subscription Engine (SE - System A) contracts into Cloud Metering & Billing (CM3 - System B) with 3-URL Gateway routing, bi-directional inversion, and full error/unmapped governance.");
        existing.setSourceSystemName("OpenText Subscription Engine (SE - System A)");
        existing.setSourceSystemUrl("http://localhost:8080/mock/se-system-a");
        existing.setTargetSystemName("Cloud Metering & Billing (CM3 - System B)");
        existing.setTargetSystemUrl("http://localhost:8080/mock/cm3-system-b");
        existing.setUnmappedFieldPolicy("DROP_WITH_WARNING");
        existing.setMissingMappingPolicy("STRICT_REJECT_422");
        existing.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(existing);

        boolean hasPublished = mappingRepository.findByProjectIdOrderByVersionDesc(existing.getId())
                .stream().anyMatch(m -> m.getStatus() == MappingStatus.PUBLISHED);
        boolean hasLogs = !gatewayLogRepository.findByProjectIdOrderByCreatedAtDesc(existing.getId()).isEmpty();

        if (!hasPublished || !hasLogs) {
            return buildSeToCm3Project(existing.getId());
        }
        return existing.getId();
    }

    private String upgradeExistingPosToErpIfNeeded(IntegrationProject existing) {
        existing.setSourceSystemName("Legacy Core POS & Billing (System A)");
        existing.setSourceSystemUrl("http://localhost:8080/mock/pos-system-a");
        existing.setTargetSystemName("Salesforce Cloud ERP (System B)");
        existing.setTargetSystemUrl("http://localhost:8080/mock/erp-system-b");
        existing.setStatus(ProjectStatus.ACTIVE);
        projectRepository.save(existing);

        if (gatewayLogRepository.findByProjectIdOrderByCreatedAtDesc(existing.getId()).isEmpty()) {
            gatewayProxyService.simulateAllEnterpriseCases(existing.getId());
        }
        return existing.getId();
    }

    private String buildSeToCm3Project(String existingProjectId) {
        String projectId = existingProjectId;
        if (projectId == null) {
            ProjectResponse p = projectService.createProject(ProjectCreateRequest.builder()
                    .name("SEtoCM3 (Subscription Engine <-> Cloud Metering CM3)")
                    .description("Enterprise SaaS Subscription & Metering Integration: Translates OpenText Subscription Engine (SE - System A) contracts into Cloud Metering & Billing (CM3 - System B) with 3-URL Gateway routing, bi-directional inversion, and full error/unmapped governance.")
                    .sourceSystemName("OpenText Subscription Engine (SE - System A)")
                    .sourceSystemUrl("http://localhost:8080/mock/se-system-a")
                    .targetSystemName("Cloud Metering & Billing (CM3 - System B)")
                    .targetSystemUrl("http://localhost:8080/mock/cm3-system-b")
                    .unmappedFieldPolicy("DROP_WITH_WARNING")
                    .missingMappingPolicy("STRICT_REJECT_422")
                    .approverEmail("baljinders@opentext.com")
                    .autoApproveEnabled(true)
                    .build());
            projectId = p.getId();
        }

        IntegrationProject project = projectRepository.findById(projectId).orElseThrow();

        // 1. Import Source & Target Schemas for SEtoCM3
        Map<String, Object> seSourceSample = new LinkedHashMap<>();
        seSourceSample.put("sub_id", "SUB-2026-9001");
        seSourceSample.put("acct_num", "ACCT-OTX-4410");
        seSourceSample.put("customer_name", "Global FinCorp Holdings");
        seSourceSample.put("start_date", "15/11/2026");
        seSourceSample.put("auto_renew", "Yes");
        seSourceSample.put("plan_code", "ENTERPRISE_PLUS");
        seSourceSample.put("seat_count", "250");
        seSourceSample.put("admin", Map.of("email", "cloud-admin@fincorp.io", "phone", "+1-415-890-2211"));

        Map<String, Object> cm3TargetContract = new LinkedHashMap<>();
        cm3TargetContract.put("subscriptionId", "string");
        cm3TargetContract.put("tenantAccountId", "string");
        cm3TargetContract.put("subscriberName", "string");
        cm3TargetContract.put("billingStartDate", "yyyy-MM-dd");
        cm3TargetContract.put("autoRenewalEnabled", "boolean");
        cm3TargetContract.put("tierCode", "string");
        cm3TargetContract.put("licensedSeats", "integer");
        cm3TargetContract.put("primaryContactEmail", "string");
        cm3TargetContract.put("primaryContactPhone", "string");

        String seSourceJson = writeJson(seSourceSample);
        String cm3TargetJson = writeJson(cm3TargetContract);

        SchemaResponse srcSchema = projectService.importSchema(projectId, SchemaImportRequest.builder()
                .direction(Direction.SOURCE_TO_TARGET)
                .systemName("OpenText Subscription Engine (SE - System A)")
                .schemaName("SE_Legacy_Subscription_Schema")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(seSourceJson)
                .build());

        SchemaResponse tgtSchema = projectService.importSchema(projectId, SchemaImportRequest.builder()
                .direction(Direction.TARGET_TO_SOURCE)
                .systemName("Cloud Metering & Billing (CM3 - System B)")
                .schemaName("CM3_Cloud_Subscription_Contract")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(cm3TargetJson)
                .build());

        // 2. Create Published Forward Mapping (WRITE: SE -> CM3) & Reverse Mapping (READ: CM3 -> SE)
        List<MappingDefinition> existingMappings = mappingRepository.findByProjectIdOrderByVersionDesc(projectId);
        int baseVersion = existingMappings.isEmpty() ? 1 : existingMappings.get(0).getVersion() + 1;

        MappingDefinition forwardDef = MappingDefinition.builder()
                .project(project)
                .direction(Direction.SOURCE_TO_TARGET)
                .version(baseVersion)
                .sourceSchemaId(srcSchema.getId())
                .targetSchemaId(tgtSchema.getId())
                .status(MappingStatus.PUBLISHED)
                .createdBy("Aviator 5-Level Pipeline")
                .approvedBy("baljinders@opentext.com")
                .approvedAt(LocalDateTime.now())
                .build();
        forwardDef = mappingRepository.save(forwardDef);

        List<MappingRule> forwardRules = List.of(
                buildRule(forwardDef, List.of("sub_id"), "subscriptionId", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.NORMALISED, 0.96, "Normalised primary identifier match: 'sub_id' -> 'subscriptionId'"),
                buildRule(forwardDef, List.of("acct_num"), "tenantAccountId", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.SYNONYM, 0.93, "Domain synonym match: 'acct_num' -> 'tenantAccountId'"),
                buildRule(forwardDef, List.of("customer_name"), "subscriberName", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.SYNONYM, 0.94, "Domain synonym match: 'customer_name' -> 'subscriberName'"),
                buildRule(forwardDef, List.of("start_date"), "billingStartDate", TransformationOpType.DATE_FORMAT,
                        Map.of("sourceFormat", "dd/MM/yyyy", "targetFormat", "yyyy-MM-dd"),
                        MatchMethod.NORMALISED, 0.95, "Date format conversion: 'dd/MM/yyyy' -> ISO-8601 'yyyy-MM-dd'"),
                buildRule(forwardDef, List.of("auto_renew"), "autoRenewalEnabled", TransformationOpType.STRING_TO_BOOLEAN,
                        Map.of("trueValues", List.of("Yes", "Y", "true", "1")),
                        MatchMethod.NORMALISED, 0.95, "Coerces legacy 'Yes'/'No' string into native boolean"),
                buildRule(forwardDef, List.of("plan_code"), "tierCode", TransformationOpType.ENUM_MAP,
                        Map.of("mappings", Map.of("ENTERPRISE_PLUS", "ENT_PLUS", "STANDARD_PRO", "STD_PRO", "STARTER", "STR")),
                        MatchMethod.AI_SEMANTIC, 0.92, "Aviator AI mapped legacy plan codes to CM3 canonical 3-letter tier codes"),
                buildRule(forwardDef, List.of("seat_count"), "licensedSeats", TransformationOpType.STRING_TO_NUMBER,
                        Map.of(), MatchMethod.SYNONYM, 0.95, "Coerces string seat count '250' into integer licensedSeats"),
                buildRule(forwardDef, List.of("admin.email"), "primaryContactEmail", TransformationOpType.FLATTEN,
                        Map.of(), MatchMethod.AI_SEMANTIC, 0.95, "Flattens nested 'admin.email' into root 'primaryContactEmail'"),
                buildRule(forwardDef, List.of("admin.phone"), "primaryContactPhone", TransformationOpType.FLATTEN,
                        Map.of(), MatchMethod.AI_SEMANTIC, 0.94, "Flattens nested 'admin.phone' into root 'primaryContactPhone'")
        );
        forwardDef.getRules().addAll(forwardRules);
        mappingRepository.save(forwardDef);

        // 3. Register the 3 APIs (Write, Read, and Unmapped Route)
        apiRepository.deleteAll(apiRepository.findByProjectIdOrderByCreatedAtAsc(projectId));
        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Create Cloud Subscription (WRITE: SE -> CM3)")
                .description("System A (SE) sends legacy subscription payload to SchemaBridge Gateway URL; Gateway transforms in <3ms and forwards to CM3 Target URL.")
                .httpMethod("POST")
                .endpointPath("/api/v1/subscriptions")
                .direction(Direction.SOURCE_TO_TARGET)
                .sourceUrl("http://localhost:8080/mock/se-system-a/api/v1/subscriptions")
                .targetUrl("http://localhost:8080/mock/cm3-system-b/api/v1/subscriptions")
                .sourceSchema(seSourceJson)
                .targetSchema(cm3TargetJson)
                .status("ACTIVE")
                .build());

        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Get Subscription by ID (READ: CM3 -> Reverse -> SE)")
                .description("System A (SE) calls SchemaBridge Gateway GET; Gateway fetches modern CM3 JSON from Target URL and reverse-transforms it back to SE format.")
                .httpMethod("GET")
                .endpointPath("/api/v1/subscriptions/{id}")
                .direction(Direction.TARGET_TO_SOURCE)
                .sourceUrl("http://localhost:8080/mock/se-system-a/api/v1/subscriptions/{id}")
                .targetUrl("http://localhost:8080/mock/cm3-system-b/api/v1/subscriptions/{id}")
                .sourceSchema(cm3TargetJson)
                .targetSchema(seSourceJson)
                .status("ACTIVE")
                .build());

        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Push Metered Usage Events (UNMAPPED ROUTE DEMO)")
                .description("Demonstrates what happens when System A calls an endpoint where NO mapping is published yet (Quarantined HTTP 422 + Payload Captured in Logs).")
                .httpMethod("POST")
                .endpointPath("/api/v1/usage-events")
                .direction(Direction.SOURCE_TO_TARGET)
                .sourceUrl("http://localhost:8080/mock/se-system-a/api/v1/usage-events")
                .targetUrl("http://localhost:8080/mock/cm3-system-b/api/v1/usage-events")
                .sourceSchema(writeJson(Map.of("event_id", "EVT-88210", "sub_id", "SUB-2026-9001", "api_calls_consumed", 14500)))
                .targetSchema("")
                .status("UNMAPPED")
                .build());

        // 4. Simulate all 6 enterprise cases so Gateway Logs are pre-populated!
        gatewayLogRepository.deleteByProjectId(projectId);
        gatewayProxyService.simulateAllEnterpriseCases(projectId);

        return projectId;
    }

    private String buildPosToErpProject() {
        ProjectResponse p = projectService.createProject(ProjectCreateRequest.builder()
                .name("LegacyPOS-to-CloudERP (Retail Core POS <-> Salesforce ERP)")
                .description("Bi-directional Order & Customer Integration: Connects Legacy On-Premise Core POS (System A) with Salesforce Cloud ERP (System B). Demonstrates 5-level matching, <5ms pure-Java transformation, unmapped field handling, and validation error quarantine.")
                .sourceSystemName("Legacy Core POS & Billing (System A)")
                .sourceSystemUrl("http://localhost:8080/mock/pos-system-a")
                .targetSystemName("Salesforce Cloud ERP (System B)")
                .targetSystemUrl("http://localhost:8080/mock/erp-system-b")
                .unmappedFieldPolicy("DROP_WITH_WARNING")
                .missingMappingPolicy("STRICT_REJECT_422")
                .approverEmail("baljinders@opentext.com")
                .autoApproveEnabled(true)
                .build());
        String projectId = p.getId();
        IntegrationProject project = projectRepository.findById(projectId).orElseThrow();

        Map<String, Object> posSourceSample = new LinkedHashMap<>();
        posSourceSample.put("order_num", "ORD-2026-991");
        posSourceSample.put("cust_id", "CUST-4081");
        posSourceSample.put("name", "Dr. Sarah Connor");
        posSourceSample.put("dob", "15/08/1988");
        posSourceSample.put("active", "Yes");
        posSourceSample.put("tier", "GOLD_TIER");
        posSourceSample.put("contact", Map.of("email", "sarah.c@cyberdyne.org", "phone", "+1-555-0199"));
        posSourceSample.put("total_amount", 499.99);

        Map<String, Object> erpTargetContract = new LinkedHashMap<>();
        erpTargetContract.put("orderId", "string");
        erpTargetContract.put("customerId", "string");
        erpTargetContract.put("userName", "string");
        erpTargetContract.put("dateOfBirth", "yyyy-MM-dd");
        erpTargetContract.put("accountEnabled", "boolean");
        erpTargetContract.put("membershipLevel", "string");
        erpTargetContract.put("emailAddress", "string");
        erpTargetContract.put("telephone", "string");
        erpTargetContract.put("totalAmount", "number");

        String posJson = writeJson(posSourceSample);
        String erpJson = writeJson(erpTargetContract);

        SchemaResponse srcSchema = projectService.importSchema(projectId, SchemaImportRequest.builder()
                .direction(Direction.SOURCE_TO_TARGET)
                .systemName("Legacy Core POS & Billing (System A)")
                .schemaName("POS_Legacy_Order_Schema")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(posJson)
                .build());

        SchemaResponse tgtSchema = projectService.importSchema(projectId, SchemaImportRequest.builder()
                .direction(Direction.TARGET_TO_SOURCE)
                .systemName("Salesforce Cloud ERP (System B)")
                .schemaName("Cloud_ERP_Order_Contract")
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(erpJson)
                .build());

        MappingDefinition forwardDef = MappingDefinition.builder()
                .project(project)
                .direction(Direction.SOURCE_TO_TARGET)
                .version(1)
                .sourceSchemaId(srcSchema.getId())
                .targetSchemaId(tgtSchema.getId())
                .status(MappingStatus.PUBLISHED)
                .createdBy("Aviator 5-Level Pipeline")
                .approvedBy("baljinders@opentext.com")
                .approvedAt(LocalDateTime.now())
                .build();
        forwardDef = mappingRepository.save(forwardDef);

        List<MappingRule> rules = List.of(
                buildRule(forwardDef, List.of("order_num"), "orderId", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.NORMALISED, 0.95, "Normalised identifier match: 'order_num' -> 'orderId'"),
                buildRule(forwardDef, List.of("cust_id"), "customerId", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.SYNONYM, 0.93, "Synonym dictionary match: 'cust_id' -> 'customerId'"),
                buildRule(forwardDef, List.of("name"), "userName", TransformationOpType.RENAME, Map.of(),
                        MatchMethod.SYNONYM, 0.93, "Synonym dictionary match: 'name' ~ 'userName'"),
                buildRule(forwardDef, List.of("dob"), "dateOfBirth", TransformationOpType.DATE_FORMAT,
                        Map.of("sourceFormat", "dd/MM/yyyy", "targetFormat", "yyyy-MM-dd"),
                        MatchMethod.SYNONYM, 0.95, "Converts slash date 'dd/MM/yyyy' to ISO-8601 'yyyy-MM-dd'"),
                buildRule(forwardDef, List.of("active"), "accountEnabled", TransformationOpType.STRING_TO_BOOLEAN,
                        Map.of("trueValues", List.of("Yes", "Y", "true", "1")),
                        MatchMethod.SYNONYM, 0.93, "Converts legacy 'Yes'/'No' flag to boolean accountEnabled"),
                buildRule(forwardDef, List.of("tier"), "membershipLevel", TransformationOpType.ENUM_MAP,
                        Map.of("mappings", Map.of("GOLD_TIER", "GLD", "SILVER_TIER", "SLV", "PLATINUM_TIER", "PLT")),
                        MatchMethod.AI_SEMANTIC, 0.91, "Maps legacy tier strings to canonical 3-letter ERP enum codes"),
                buildRule(forwardDef, List.of("contact.email"), "emailAddress", TransformationOpType.FLATTEN,
                        Map.of(), MatchMethod.AI_SEMANTIC, 0.95, "Flattens nested 'contact.email' to root 'emailAddress'"),
                buildRule(forwardDef, List.of("contact.phone"), "telephone", TransformationOpType.FLATTEN,
                        Map.of(), MatchMethod.AI_SEMANTIC, 0.94, "Flattens nested 'contact.phone' to root 'telephone'"),
                buildRule(forwardDef, List.of("total_amount"), "totalAmount", TransformationOpType.RENAME,
                        Map.of(), MatchMethod.NORMALISED, 0.95, "Normalised name match: 'total_amount' -> 'totalAmount'")
        );
        forwardDef.getRules().addAll(rules);
        mappingRepository.save(forwardDef);

        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Create Cloud ERP Order (WRITE: POS -> ERP)")
                .description("Legacy POS sends snake_case order payload to SchemaBridge Gateway URL; Gateway translates & validates in <3ms and forwards to Salesforce ERP Target URL.")
                .httpMethod("POST")
                .endpointPath("/api/v1/orders")
                .direction(Direction.SOURCE_TO_TARGET)
                .sourceUrl("http://localhost:8080/mock/pos-system-a/api/v1/orders")
                .targetUrl("http://localhost:8080/mock/erp-system-b/api/v1/orders")
                .sourceSchema(posJson)
                .targetSchema(erpJson)
                .status("ACTIVE")
                .build());

        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Get Customer Order (READ: ERP -> Reverse -> POS)")
                .description("Legacy POS queries order from SchemaBridge Gateway GET; Gateway fetches modern Cloud ERP JSON and inverts it back to Legacy POS format.")
                .httpMethod("GET")
                .endpointPath("/api/v1/orders/{id}")
                .direction(Direction.TARGET_TO_SOURCE)
                .sourceUrl("http://localhost:8080/mock/pos-system-a/api/v1/orders/{id}")
                .targetUrl("http://localhost:8080/mock/erp-system-b/api/v1/orders/{id}")
                .sourceSchema(erpJson)
                .targetSchema(posJson)
                .status("ACTIVE")
                .build());

        apiRepository.save(RegisteredApi.builder()
                .project(project)
                .name("Process POS Refund (UNMAPPED ROUTE DEMO)")
                .description("Unmapped endpoint registered to demonstrate how SchemaBridge quarantines and logs calls when a mapping contract is missing.")
                .httpMethod("POST")
                .endpointPath("/api/v1/refunds")
                .direction(Direction.SOURCE_TO_TARGET)
                .sourceUrl("http://localhost:8080/mock/pos-system-a/api/v1/refunds")
                .targetUrl("http://localhost:8080/mock/erp-system-b/api/v1/refunds")
                .sourceSchema(writeJson(Map.of("refund_id", "RFD-5510", "orig_order_num", "ORD-2026-991", "refund_amt", 120.00)))
                .targetSchema("")
                .status("UNMAPPED")
                .build());

        gatewayProxyService.simulateAllEnterpriseCases(projectId);
        return projectId;
    }

    private MappingRule buildRule(
            MappingDefinition def,
            List<String> sources,
            String target,
            TransformationOpType op,
            Map<String, Object> params,
            MatchMethod method,
            double confidence,
            String explanation) {
        return MappingRule.builder()
                .mappingDefinition(def)
                .sourcePaths(writeJson(sources))
                .targetPath(target)
                .operation(op)
                .parametersJson(writeJson(params))
                .matchMethod(method)
                .confidence(BigDecimal.valueOf(confidence))
                .status(MappingStatus.APPROVED)
                .explanation(explanation)
                .build();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
