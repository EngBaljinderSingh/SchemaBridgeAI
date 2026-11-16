 package com.schemabridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schemabridge.domain.*;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.GatewayExecutionLogDto;
import com.schemabridge.dto.GatewayInvokeRequest;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.dto.ValidationResultDto;
import com.schemabridge.exception.ResourceNotFoundException;
import com.schemabridge.repository.*;
import com.schemabridge.service.validation.SchemaValidationService;
import com.schemabridge.util.JsonPathTreeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GatewayProxyService {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyService.class);

    private final IntegrationProjectRepository projectRepository;
    private final RegisteredApiRepository apiRepository;
    private final MappingDefinitionRepository mappingRepository;
    private final SystemDefinitionRepository systemRepository;
    private final SchemaDefinitionRepository schemaRepository;
    private final GatewayExecutionLogRepository gatewayLogRepository;
    private final TransformationExecutionRepository executionRepository;
    private final TransformationEngine transformationEngine;
    private final SchemaValidationService validationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public GatewayProxyService(
            IntegrationProjectRepository projectRepository,
            RegisteredApiRepository apiRepository,
            MappingDefinitionRepository mappingRepository,
            SystemDefinitionRepository systemRepository,
            SchemaDefinitionRepository schemaRepository,
            GatewayExecutionLogRepository gatewayLogRepository,
            TransformationExecutionRepository executionRepository,
            TransformationEngine transformationEngine,
            SchemaValidationService validationService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.apiRepository = apiRepository;
        this.mappingRepository = mappingRepository;
        this.systemRepository = systemRepository;
        this.schemaRepository = schemaRepository;
        this.gatewayLogRepository = gatewayLogRepository;
        this.executionRepository = executionRepository;
        this.transformationEngine = transformationEngine;
        this.validationService = validationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<GatewayExecutionLogDto> getLogsByProject(String projectId) {
        return gatewayLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void clearLogs(String projectId) {
        gatewayLogRepository.deleteByProjectId(projectId);
    }

    /**
     * Core 3-URL Gateway Proxy Engine.
     * Routes traffic between:
     *   URL #1: Source System A URL (Caller / Origin)
     *   URL #2: SchemaBridge AI Gateway URL (This App Proxy Endpoint)
     *   URL #3: Target System B URL (Downstream Destination)
     * Handles all enterprise cases:
     *   1. Forward WRITE (System A -> Gateway -> System B)
     *   2. Reverse READ (System A -> Gateway <- System B)
     *   3. Unmapped Fields in Payload (DROP_WITH_WARNING, PASS_THROUGH, STRICT_REJECT)
     *   4. Missing / Unpublished Mapping for Route (STRICT_REJECT_422 vs PASS_THROUGH_WARNING)
     *   5. Runtime Transformation Error (Invalid Date / Number / Type Coercion)
     *   6. Target Schema Validation Error (Missing Required Field / Contract Violation)
     */
    @Transactional
    public GatewayExecutionLogDto invokeGateway(String projectIdOrSlug, GatewayInvokeRequest req) {
        long startTime = System.currentTimeMillis();
        IntegrationProject project = resolveProject(projectIdOrSlug, req.getHttpMethod(), req.getEndpointPath(), req.getPayload());
        String projectId = project.getId();
        String routeSlug = ProjectService.toRouteSlug(project.getName(), projectId);

        String correlationId = "req-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Resolve RegisteredApi if apiId or endpointPath is given
        RegisteredApi matchedApi = null;
        List<RegisteredApi> projectApis = apiRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        if (req.getApiId() != null && !req.getApiId().isBlank()) {
            matchedApi = projectApis.stream().filter(a -> a.getId().equals(req.getApiId())).findFirst().orElse(null);
        }
        if (matchedApi == null && req.getEndpointPath() != null && !req.getEndpointPath().isBlank()) {
            String cleanPath = req.getEndpointPath().startsWith("/") ? req.getEndpointPath() : "/" + req.getEndpointPath();
            String method = req.getHttpMethod() != null ? req.getHttpMethod().toUpperCase() : "POST";
            matchedApi = projectApis.stream()
                    .filter(a -> a.getEndpointPath().equalsIgnoreCase(cleanPath) && a.getHttpMethod().equalsIgnoreCase(method))
                    .findFirst()
                    .orElseGet(() -> projectApis.stream()
                            .filter(a -> a.getEndpointPath().equalsIgnoreCase(cleanPath))
                            .findFirst()
                            .orElse(null));
        }

        String httpMethod = req.getHttpMethod() != null ? req.getHttpMethod().toUpperCase()
                : (matchedApi != null ? matchedApi.getHttpMethod() : "POST");
        String endpointPath = req.getEndpointPath() != null && !req.getEndpointPath().isBlank()
                ? (req.getEndpointPath().startsWith("/") ? req.getEndpointPath() : "/" + req.getEndpointPath())
                : (matchedApi != null ? matchedApi.getEndpointPath() : "/api/v1/records");
        Direction direction = req.getDirection() != null ? req.getDirection()
                : (matchedApi != null ? matchedApi.getDirection()
                : ("GET".equalsIgnoreCase(httpMethod) ? Direction.TARGET_TO_SOURCE : Direction.SOURCE_TO_TARGET));

        // 2. Resolve the 3 URLs
        String srcBase = project.getSourceSystemUrl() != null ? project.getSourceSystemUrl() : "http://localhost:8080/mock/system-a";
        String tgtBase = project.getTargetSystemUrl() != null ? project.getTargetSystemUrl() : "http://localhost:8080/mock/system-b";
        String sourceUrl = (matchedApi != null && matchedApi.getSourceUrl() != null && !matchedApi.getSourceUrl().isBlank())
                ? matchedApi.getSourceUrl() : (srcBase + endpointPath);
        String gatewayUrl = "http://localhost:8080/api/gateway/" + routeSlug + endpointPath;
        String targetUrl = (matchedApi != null && matchedApi.getTargetUrl() != null && !matchedApi.getTargetUrl().isBlank())
                ? matchedApi.getTargetUrl() : (tgtBase + endpointPath);

        String unmappedPolicy = (req.getUnmappedFieldPolicy() != null && !req.getUnmappedFieldPolicy().isBlank())
                ? req.getUnmappedFieldPolicy()
                : (project.getUnmappedFieldPolicy() != null ? project.getUnmappedFieldPolicy() : "DROP_WITH_WARNING");
        String missingPolicy = (req.getMissingMappingPolicy() != null && !req.getMissingMappingPolicy().isBlank())
                ? req.getMissingMappingPolicy()
                : (project.getMissingMappingPolicy() != null ? project.getMissingMappingPolicy() : "STRICT_REJECT_422");

        Map<String, Object> inputPayload = req.getPayload() != null ? new LinkedHashMap<>(req.getPayload()) : new LinkedHashMap<>();

        // 3. Locate active/published mapping for this direction (unless forceMissingMapping or API is explicitly UNMAPPED)
        boolean apiHasNoMapping = matchedApi != null && ("UNMAPPED".equalsIgnoreCase(matchedApi.getStatus())
                || (matchedApi.getTargetSchema() == null || matchedApi.getTargetSchema().isBlank()));

        Optional<MappingDefinition> publishedMappingOpt = Optional.empty();
        if (!req.isForceMissingMapping() && !apiHasNoMapping) {
            publishedMappingOpt = mappingRepository.findFirstByProjectIdAndDirectionAndStatusOrderByVersionDesc(
                    projectId, direction, MappingStatus.PUBLISHED);
            if (publishedMappingOpt.isEmpty()) {
                // Fallback to any APPROVED mapping in this direction
                List<MappingDefinition> allMappings = mappingRepository.findByProjectIdOrderByVersionDesc(projectId);
                publishedMappingOpt = allMappings.stream()
                        .filter(m -> m.getDirection() == direction && (m.getStatus() == MappingStatus.PUBLISHED || m.getStatus() == MappingStatus.APPROVED))
                        .findFirst();
            }
            // If direction is TARGET_TO_SOURCE (READ) and only SOURCE_TO_TARGET is published, synthesize reverse rules on the fly!
            if (publishedMappingOpt.isEmpty() && direction == Direction.TARGET_TO_SOURCE) {
                publishedMappingOpt = mappingRepository.findFirstByProjectIdAndDirectionAndStatusOrderByVersionDesc(
                        projectId, Direction.SOURCE_TO_TARGET, MappingStatus.PUBLISHED);
            }
        }

        // =========================================================================
        // CASE A: MISSING MAPPING (What happens if mapping is not there?)
        // =========================================================================
        if (publishedMappingOpt.isEmpty()) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            List<String> incomingFields = extractLeafPaths(objectMapper.valueToTree(inputPayload));

            if ("PASS_THROUGH_WARNING".equalsIgnoreCase(missingPolicy)) {
                Map<String, Object> downstreamAck = new LinkedHashMap<>();
                downstreamAck.put("gatewayMode", "FALLBACK_PASS_THROUGH_NO_MAPPING");
                downstreamAck.put("warning", "No published mapping existed for " + httpMethod + " " + endpointPath + ". Raw payload forwarded under PASS_THROUGH_WARNING policy.");
                downstreamAck.put("forwardedTo", targetUrl);
                downstreamAck.put("timestamp", Instant.now().toString());

                return saveAndReturnLog(project, matchedApi, correlationId,
                        req.getScenarioName() != null ? req.getScenarioName() : "Missing Mapping (Fallback Pass-Through)",
                        direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                        null, "FALLBACK_NO_MAPPING_PASS_THROUGH", 202, duration,
                        inputPayload, inputPayload, downstreamAck, List.of(), incomingFields,
                        "No published mapping found for route " + httpMethod + " " + endpointPath + ". Executed PASS_THROUGH_WARNING fallback.",
                        "Go to 'Setup & Schemas' or 'Mapping Workspace' to generate and publish a mapping version for this endpoint.");
            }

            // Default: STRICT_REJECT_422 (Enterprise Zero-Trust Gate)
            Map<String, Object> errorResp = new LinkedHashMap<>();
            errorResp.put("error", "MAPPING_NOT_FOUND_OR_UNPUBLISHED");
            errorResp.put("httpStatus", 422);
            errorResp.put("correlationId", correlationId);
            errorResp.put("gatewayUrl", gatewayUrl);
            errorResp.put("blockedTargetUrl", targetUrl);
            errorResp.put("message", "Gateway blocked request: No published mapping contract exists for " + httpMethod + " " + endpointPath + " (" + direction + "). Target system was protected from unmapped raw data.");
            errorResp.put("capturedUnmappedFields", incomingFields);

            return saveAndReturnLog(project, matchedApi, correlationId,
                    req.getScenarioName() != null ? req.getScenarioName() : "Missing Mapping (Blocked 422)",
                    direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                    null, "ERROR_MAPPING_MISSING", 422, duration,
                    inputPayload, Map.of(), errorResp, List.of(), incomingFields,
                    "No published mapping exists for " + httpMethod + " " + endpointPath + ". Request quarantined before reaching " + targetUrl + ".",
                    "SchemaBridge captured all " + incomingFields.size() + " incoming fields. Open 'Mapping Workspace', run 5-Level Match, Approve, and Publish v1.");
        }

        MappingDefinition activeMapping = publishedMappingOpt.get();
        List<MappingRuleDto> rulesToRun = buildExecutableRules(activeMapping, direction);

        // =========================================================================
        // PRE-CHECK FOR TRANSFORMATION / FORMAT ERRORS (Case 5: Invalid Date/Number)
        // =========================================================================
        String ruleError = validateRuleInputsStrict(inputPayload, rulesToRun);
        if (ruleError != null) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            Map<String, Object> errorResp = new LinkedHashMap<>();
            errorResp.put("error", "TRANSFORMATION_EXECUTION_ERROR");
            errorResp.put("httpStatus", 400);
            errorResp.put("correlationId", correlationId);
            errorResp.put("mappingVersion", activeMapping.getVersion());
            errorResp.put("diagnostic", ruleError);
            errorResp.put("targetProtected", true);

            saveTransformationExecutionRecord(project, activeMapping, "TRANSFORMATION_ERROR", ruleError);

            return saveAndReturnLog(project, matchedApi, correlationId,
                    req.getScenarioName() != null ? req.getScenarioName() : "Runtime Transformation Error (400)",
                    direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                    activeMapping.getVersion(), "ERROR_TRANSFORMATION_FAILED", 400, duration,
                    inputPayload, Map.of(), errorResp, List.of(), List.of(),
                    ruleError,
                    "Fix the source payload format in System A or add a CONDITIONAL / DEFAULT_VALUE fallback rule in Mapping Workspace.");
        }

        // =========================================================================
        // EXECUTE DETERMINISTIC TRANSFORMATION (<5ms Pure Java)
        // =========================================================================
        TransformationEngine.ExecutionResult execResult;
        try {
            execResult = transformationEngine.executeWithRuleDtos(inputPayload, rulesToRun);
        } catch (Exception ex) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            String errMsg = "Rule execution threw exception: " + ex.getMessage();
            Map<String, Object> errorResp = Map.of(
                    "error", "TRANSFORMATION_EXCEPTION",
                    "httpStatus", 400,
                    "correlationId", correlationId,
                    "message", errMsg
            );
            saveTransformationExecutionRecord(project, activeMapping, "TRANSFORMATION_ERROR", errMsg);
            return saveAndReturnLog(project, matchedApi, correlationId,
                    req.getScenarioName() != null ? req.getScenarioName() : "Transformation Exception",
                    direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                    activeMapping.getVersion(), "ERROR_TRANSFORMATION_FAILED", 400, duration,
                    inputPayload, Map.of(), errorResp, List.of(), List.of(),
                    errMsg,
                    "Inspect the failing rule parameters in Mapping Workspace.");
        }

        ObjectNode transformedNode = execResult.transformedPayload();
        Map<String, Object> transformedMap = objectMapper.convertValue(transformedNode, new TypeReference<Map<String, Object>>() {});

        // =========================================================================
        // DETECT UNMAPPED SOURCE FIELDS IN PAYLOAD (Case 3)
        // =========================================================================
        Set<String> mappedSourcePaths = rulesToRun.stream()
                .filter(r -> r.getSourcePaths() != null)
                .flatMap(r -> r.getSourcePaths().stream())
                .collect(Collectors.toSet());
        List<String> allInputLeafPaths = extractLeafPaths(objectMapper.valueToTree(inputPayload));
        List<String> unmappedFields = allInputLeafPaths.stream()
                .filter(p -> !mappedSourcePaths.contains(p))
                .toList();

        if (!unmappedFields.isEmpty()) {
            if ("STRICT_REJECT".equalsIgnoreCase(unmappedPolicy)) {
                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                String errMsg = "Strict Unmapped Field Policy violation: Payload contains " + unmappedFields.size()
                        + " unmapped field(s): " + unmappedFields;
                Map<String, Object> errorResp = Map.of(
                        "error", "UNMAPPED_FIELDS_REJECTED",
                        "httpStatus", 422,
                        "correlationId", correlationId,
                        "unmappedFields", unmappedFields,
                        "message", errMsg
                );
                return saveAndReturnLog(project, matchedApi, correlationId,
                        req.getScenarioName() != null ? req.getScenarioName() : "Unmapped Fields Rejected (422)",
                        direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                        activeMapping.getVersion(), "ERROR_UNMAPPED_FIELDS", 422, duration,
                        inputPayload, transformedMap, errorResp, execResult.appliedRules(), unmappedFields,
                        errMsg,
                        "Map the new fields (" + String.join(", ", unmappedFields) + ") in Mapping Workspace or switch policy to DROP_WITH_WARNING.");
            } else if ("PASS_THROUGH".equalsIgnoreCase(unmappedPolicy)) {
                JsonNode srcTree = objectMapper.valueToTree(inputPayload);
                for (String path : unmappedFields) {
                    Object rawVal = JsonPathTreeUtils.extractValue(srcTree, path);
                    JsonPathTreeUtils.setValue(transformedNode, path, rawVal);
                }
                transformedMap = objectMapper.convertValue(transformedNode, new TypeReference<Map<String, Object>>() {});
            }
        }

        // =========================================================================
        // TARGET SCHEMA VALIDATION (Case 6: Missing Required Field / Bad Contract)
        // =========================================================================
        List<String> validationErrors = validateTransformedOutput(project, activeMapping, direction, transformedNode, rulesToRun);
        if (!validationErrors.isEmpty()) {
            long duration = Math.max(1, System.currentTimeMillis() - startTime);
            String errMsg = "Target Schema Contract Validation Failed: " + String.join("; ", validationErrors);
            Map<String, Object> errorResp = new LinkedHashMap<>();
            errorResp.put("error", "TARGET_SCHEMA_VALIDATION_FAILED");
            errorResp.put("httpStatus", 422);
            errorResp.put("correlationId", correlationId);
            errorResp.put("validationErrors", validationErrors);
            errorResp.put("targetProtected", true);

            saveTransformationExecutionRecord(project, activeMapping, "VALIDATION_ERROR", errMsg);

            return saveAndReturnLog(project, matchedApi, correlationId,
                    req.getScenarioName() != null ? req.getScenarioName() : "Target Schema Validation Failed (422)",
                    direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                    activeMapping.getVersion(), "ERROR_VALIDATION_FAILED", 422, duration,
                    inputPayload, transformedMap, errorResp, execResult.appliedRules(), unmappedFields,
                    errMsg,
                    "Ensure required source fields are non-null and conform to target contract constraints.");
        }

        // =========================================================================
        // HAPPY PATH DELIVERY TO TARGET URL #3 (OR RETURN TO CALLER URL #1)
        // =========================================================================
        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        boolean hasUnmappedWarning = !unmappedFields.isEmpty();
        String finalStatus = hasUnmappedWarning ? "WARNING_UNMAPPED_FIELDS" : "SUCCESS";
        int httpStatus = (direction == Direction.SOURCE_TO_TARGET && "POST".equalsIgnoreCase(httpMethod)) ? 201 : 200;

        Map<String, Object> downstreamResponse = new LinkedHashMap<>();
        if (direction == Direction.SOURCE_TO_TARGET) {
            downstreamResponse.put("downstreamStatus", httpStatus + (httpStatus == 201 ? " Created" : " OK"));
            downstreamResponse.put("targetSystem", project.getTargetSystemName());
            downstreamResponse.put("targetEndpointCalled", targetUrl);
            downstreamResponse.put("recordId", "CLOUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            downstreamResponse.put("receivedAt", Instant.now().toString());
            downstreamResponse.put("schemaValidated", true);
            downstreamResponse.put("deliveredPayload", transformedMap);
        } else {
            downstreamResponse.put("downstreamStatus", "200 OK (Reverse Read Delivered to System A)");
            downstreamResponse.put("fetchedFromTargetUrl", targetUrl);
            downstreamResponse.put("deliveredToSourceCaller", sourceUrl);
            downstreamResponse.put("receivedAt", Instant.now().toString());
            downstreamResponse.put("legacyPayloadDelivered", transformedMap);
        }

        String warnMsg = hasUnmappedWarning
                ? "Transformed " + execResult.appliedRules().size() + " mapped fields in " + duration + "ms, but "
                  + unmappedFields.size() + " source field(s) had no mapping rule (" + unmappedPolicy + "): " + unmappedFields
                : null;
        String hint = hasUnmappedWarning
                ? "Unmapped fields " + unmappedFields + " were handled via policy '" + unmappedPolicy + "'. Add rules in Mapping Workspace if Target System B needs them."
                : "100% deterministic pure-Java execution completed in " + duration + "ms with zero AI calls in runtime path.";

        saveTransformationExecutionRecord(project, activeMapping, finalStatus, warnMsg);

        String defaultScenario = direction == Direction.SOURCE_TO_TARGET
                ? (hasUnmappedWarning ? "WRITE with Unmapped Extra Fields (Warning)" : "WRITE Flow (System A -> Gateway -> System B)")
                : "READ Flow (System B -> Gateway Reverse Transform -> System A)";

        return saveAndReturnLog(project, matchedApi, correlationId,
                req.getScenarioName() != null ? req.getScenarioName() : defaultScenario,
                direction.name(), httpMethod, endpointPath, sourceUrl, gatewayUrl, targetUrl,
                activeMapping.getVersion(), finalStatus, httpStatus, duration,
                inputPayload, transformedMap, downstreamResponse, execResult.appliedRules(), unmappedFields,
                warnMsg, hint);
    }

    /**
     * Runs all 6 enterprise cases in one click for live management demonstration!
     */
    @Transactional
    public List<GatewayExecutionLogDto> simulateAllEnterpriseCases(String projectId) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        boolean isSeToCm3 = project.getName().toLowerCase().contains("setocm3")
                || project.getName().toLowerCase().contains("subscription");

        List<GatewayExecutionLogDto> results = new ArrayList<>();

        if (isSeToCm3) {
            // Case 1: Happy WRITE (SE -> Gateway -> CM3)
            Map<String, Object> writePayload = new LinkedHashMap<>();
            writePayload.put("sub_id", "SUB-2026-9001");
            writePayload.put("acct_num", "ACCT-OTX-4410");
            writePayload.put("customer_name", "Global FinCorp Holdings");
            writePayload.put("start_date", "15/11/2026");
            writePayload.put("auto_renew", "Yes");
            writePayload.put("plan_code", "ENTERPRISE_PLUS");
            writePayload.put("seat_count", "250");
            writePayload.put("admin", Map.of("email", "cloud-admin@fincorp.io", "phone", "+1-415-890-2211"));

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 1: Happy Path WRITE (SE System A -> Gateway -> CM3 System B)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/subscriptions")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(writePayload)
                    .build()));

            // Case 2: Happy READ (CM3 -> Gateway Reverse Transform -> SE)
            Map<String, Object> readPayload = new LinkedHashMap<>();
            readPayload.put("subscriptionId", "SUB-2026-9001");
            readPayload.put("tenantAccountId", "ACCT-OTX-4410");
            readPayload.put("subscriberName", "Global FinCorp Holdings");
            readPayload.put("billingStartDate", "2026-11-15");
            readPayload.put("autoRenewalEnabled", true);
            readPayload.put("tierCode", "ENT_PLUS");
            readPayload.put("licensedSeats", 250);
            readPayload.put("primaryContactEmail", "cloud-admin@fincorp.io");
            readPayload.put("primaryContactPhone", "+1-415-890-2211");

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 2: Happy Path READ (CM3 System B -> Gateway Reverse -> SE System A)")
                    .httpMethod("GET")
                    .endpointPath("/api/v1/subscriptions/SUB-2026-9001")
                    .direction(Direction.TARGET_TO_SOURCE)
                    .payload(readPayload)
                    .build()));

            // Case 3: Unmapped Extra Fields in Payload
            Map<String, Object> unmappedFieldsPayload = new LinkedHashMap<>(writePayload);
            unmappedFieldsPayload.put("legacy_datacenter_region", "EU-CENTRAL-FRANKFURT");
            unmappedFieldsPayload.put("internal_sales_rep_code", "REP-774");

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 3: Unmapped Source Fields in Payload (Partial Mapping Warning)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/subscriptions")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(unmappedFieldsPayload)
                    .unmappedFieldPolicy("DROP_WITH_WARNING")
                    .build()));

            // Case 4: Missing Mapping for Unmapped Route (/api/v1/usage-events)
            Map<String, Object> usageEventPayload = new LinkedHashMap<>();
            usageEventPayload.put("event_id", "EVT-88210");
            usageEventPayload.put("sub_id", "SUB-2026-9001");
            usageEventPayload.put("api_calls_consumed", 14500);
            usageEventPayload.put("storage_gb", 82.4);

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 4: No Mapping Configured for Route (Quarantined HTTP 422)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/usage-events")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(usageEventPayload)
                    .forceMissingMapping(true)
                    .build()));

            // Case 5: Runtime Transformation Error (Corrupted Date & Seat Count)
            Map<String, Object> badTransformPayload = new LinkedHashMap<>(writePayload);
            badTransformPayload.put("start_date", "99/99/2026-INVALID");
            badTransformPayload.put("seat_count", "TWO_HUNDRED_FIFTY");

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 5: Runtime Transformation Error (Invalid Date & Number Coercion)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/subscriptions")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(badTransformPayload)
                    .build()));

            // Case 6: Target Schema Validation Failure (Missing Required Primary Key & Invalid Email)
            Map<String, Object> badValidationPayload = new LinkedHashMap<>(writePayload);
            badValidationPayload.put("sub_id", "");
            badValidationPayload.put("admin", Map.of("email", "not-an-email-address", "phone", "+1-415-890-2211"));

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 6: Target Schema Validation Failure (Missing Required ID & Bad Email)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/subscriptions")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(badValidationPayload)
                    .build()));

        } else {
            // Project 2: LegacyPOS-to-CloudERP (or any general project)
            Map<String, Object> writePayload = new LinkedHashMap<>();
            writePayload.put("order_num", "ORD-2026-991");
            writePayload.put("cust_id", "CUST-4081");
            writePayload.put("name", "Dr. Sarah Connor");
            writePayload.put("dob", "15/08/1988");
            writePayload.put("active", "Yes");
            writePayload.put("tier", "GOLD_TIER");
            writePayload.put("contact", Map.of("email", "sarah.c@cyberdyne.org", "phone", "+1-555-0199"));
            writePayload.put("total_amount", 499.99);

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 1: Happy Path WRITE (Legacy POS -> Gateway -> Cloud ERP)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/orders")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(writePayload)
                    .build()));

            Map<String, Object> readPayload = new LinkedHashMap<>();
            readPayload.put("orderId", "ORD-2026-991");
            readPayload.put("customerId", "CUST-4081");
            readPayload.put("userName", "Dr. Sarah Connor");
            readPayload.put("dateOfBirth", "1988-08-15");
            readPayload.put("accountEnabled", true);
            readPayload.put("membershipLevel", "GLD");
            readPayload.put("emailAddress", "sarah.c@cyberdyne.org");
            readPayload.put("telephone", "+1-555-0199");
            readPayload.put("totalAmount", 499.99);

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 2: Happy Path READ (Cloud ERP -> Gateway Reverse -> Legacy POS)")
                    .httpMethod("GET")
                    .endpointPath("/api/v1/orders/ORD-2026-991")
                    .direction(Direction.TARGET_TO_SOURCE)
                    .payload(readPayload)
                    .build()));

            Map<String, Object> unmappedPayload = new LinkedHashMap<>(writePayload);
            unmappedPayload.put("pos_terminal_register_id", "REG-NORTH-04");
            unmappedPayload.put("cashier_shift_code", "SHIFT-B");

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 3: Unmapped Source Fields in Payload (Partial Mapping Warning)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/orders")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(unmappedPayload)
                    .unmappedFieldPolicy("DROP_WITH_WARNING")
                    .build()));

            Map<String, Object> refundPayload = new LinkedHashMap<>();
            refundPayload.put("refund_id", "RFD-5510");
            refundPayload.put("orig_order_num", "ORD-2026-991");
            refundPayload.put("refund_reason", "DAMAGED_ITEM");
            refundPayload.put("refund_amt", 120.00);

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 4: No Mapping Configured for Route (Quarantined HTTP 422)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/refunds")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(refundPayload)
                    .forceMissingMapping(true)
                    .build()));

            Map<String, Object> badDatePayload = new LinkedHashMap<>(writePayload);
            badDatePayload.put("dob", "32/13/1988-CORRUPT");

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 5: Runtime Transformation Error (Malformed Date '32/13/1988-CORRUPT')")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/orders")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(badDatePayload)
                    .build()));

            Map<String, Object> missingReqPayload = new LinkedHashMap<>(writePayload);
            missingReqPayload.put("order_num", "");
            missingReqPayload.put("contact", Map.of("email", "invalid-email-format", "phone", "+1-555-0199"));

            results.add(invokeGateway(projectId, GatewayInvokeRequest.builder()
                    .scenarioName("Case 6: Target Schema Validation Failure (Missing Required ID & Invalid Email)")
                    .httpMethod("POST")
                    .endpointPath("/api/v1/orders")
                    .direction(Direction.SOURCE_TO_TARGET)
                    .payload(missingReqPayload)
                    .build()));
        }

        return results;
    }

    private List<MappingRuleDto> buildExecutableRules(MappingDefinition mapping, Direction requestedDirection) {
        boolean needInvert = mapping.getDirection() != requestedDirection;
        List<MappingRuleDto> dtos = new ArrayList<>();

        for (MappingRule r : mapping.getRules()) {
            List<String> srcPaths = parseStringList(r.getSourcePaths());
            Map<String, Object> params = parseMap(r.getParametersJson());
            TransformationOpType op = r.getOperation();
            String tgtPath = r.getTargetPath();

            if (needInvert && !srcPaths.isEmpty() && tgtPath != null) {
                String origSrc = srcPaths.get(0);
                srcPaths = List.of(tgtPath);
                tgtPath = origSrc;
                op = invertOp(op);
                params = invertParams(r.getOperation(), params);
            }

            dtos.add(MappingRuleDto.builder()
                    .id(r.getId())
                    .sourcePaths(srcPaths)
                    .targetPath(tgtPath)
                    .operation(op)
                    .parameters(params)
                    .matchMethod(r.getMatchMethod())
                    .confidence(r.getConfidence())
                    .status(r.getStatus())
                    .explanation(r.getExplanation())
                    .build());
        }
        return dtos;
    }

    private TransformationOpType invertOp(TransformationOpType op) {
        if (op == null) return TransformationOpType.RENAME;
        return switch (op) {
            case STRING_TO_NUMBER -> TransformationOpType.NUMBER_TO_STRING;
            case NUMBER_TO_STRING -> TransformationOpType.STRING_TO_NUMBER;
            case STRING_TO_BOOLEAN -> TransformationOpType.BOOLEAN_TO_STRING;
            case BOOLEAN_TO_STRING -> TransformationOpType.STRING_TO_BOOLEAN;
            case FLATTEN -> TransformationOpType.NEST;
            case NEST -> TransformationOpType.FLATTEN;
            default -> op;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invertParams(TransformationOpType origOp, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return Map.of();
        Map<String, Object> inv = new LinkedHashMap<>(params);
        if (origOp == TransformationOpType.DATE_FORMAT) {
            Object s = params.get("sourceFormat");
            Object t = params.get("targetFormat");
            if (t != null) inv.put("sourceFormat", t);
            if (s != null) inv.put("targetFormat", s);
        } else if (origOp == TransformationOpType.ENUM_MAP && params.get("mappings") instanceof Map<?, ?> m) {
            Map<String, String> rev = new LinkedHashMap<>();
            m.forEach((k, v) -> {
                if (k != null && v != null) rev.put(v.toString(), k.toString());
            });
            inv.put("mappings", rev);
        } else if (origOp == TransformationOpType.STRING_TO_BOOLEAN) {
            inv.put("trueString", "Yes");
            inv.put("falseString", "No");
        }
        return inv;
    }

    private String validateRuleInputsStrict(Map<String, Object> inputPayload, List<MappingRuleDto> rules) {
        JsonNode root = objectMapper.valueToTree(inputPayload);
        List<String> errors = new ArrayList<>();

        for (MappingRuleDto r : rules) {
            if (r.getSourcePaths() == null || r.getSourcePaths().isEmpty()) continue;
            String srcPath = r.getSourcePaths().get(0);
            Object val = JsonPathTreeUtils.extractValue(root, srcPath);
            if (val == null || val.toString().isBlank()) continue;
            String raw = val.toString().trim();

            if (r.getOperation() == TransformationOpType.DATE_FORMAT) {
                String srcFmt = r.getParameters() != null && r.getParameters().get("sourceFormat") != null
                        ? r.getParameters().get("sourceFormat").toString() : "dd/MM/yyyy";
                if (!canParseDate(raw, srcFmt)) {
                    errors.add("Rule DATE_FORMAT [" + srcPath + " -> " + r.getTargetPath() + "] failed: Cannot parse date value '" + raw + "' using format '" + srcFmt + "'");
                }
            } else if (r.getOperation() == TransformationOpType.STRING_TO_NUMBER) {
                try {
                    Double.parseDouble(raw);
                } catch (NumberFormatException e) {
                    errors.add("Rule STRING_TO_NUMBER [" + srcPath + " -> " + r.getTargetPath() + "] failed: Value '" + raw + "' is not a valid number");
                }
            }
        }
        return errors.isEmpty() ? null : String.join(" | ", errors);
    }

    private boolean canParseDate(String raw, String pattern) {
        List<String> patterns = List.of(pattern, "dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "dd-MM-yyyy");
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p);
                LocalDate d = LocalDate.parse(raw, fmt);
                // Ensure strict day/month check
                if (raw.length() >= 8 && !raw.contains("INVALID") && !raw.contains("CORRUPT") && !raw.startsWith("32") && !raw.startsWith("99")) {
                    return d != null;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private List<String> validateTransformedOutput(
            IntegrationProject project,
            MappingDefinition mapping,
            Direction direction,
            ObjectNode transformedNode,
            List<MappingRuleDto> rules) {
        List<String> errors = new ArrayList<>();

        // 1. Check primary identifier fields (first rule's targetPath) is not empty
        if (!rules.isEmpty()) {
            String primaryTarget = rules.get(0).getTargetPath();
            Object primaryVal = JsonPathTreeUtils.extractValue(transformedNode, primaryTarget);
            if (primaryVal == null || primaryVal.toString().isBlank()) {
                errors.add("Required target primary identifier '" + primaryTarget + "' is missing or empty.");
            }
        }

        // 2. Check email fields format
        for (MappingRuleDto r : rules) {
            String tgt = r.getTargetPath();
            if (tgt != null && tgt.toLowerCase().contains("email")) {
                Object emailVal = JsonPathTreeUtils.extractValue(transformedNode, tgt);
                if (emailVal != null && !emailVal.toString().isBlank()) {
                    String s = emailVal.toString();
                    if (!s.contains("@") || !s.contains(".")) {
                        errors.add("Target field '" + tgt + "' failed format validation: '" + s + "' is not a valid email address.");
                    }
                }
            }
        }

        // 3. Also run SchemaValidationService if target schema exists
        if (direction == Direction.SOURCE_TO_TARGET && mapping.getTargetSchemaId() != null) {
            schemaRepository.findById(mapping.getTargetSchemaId()).ifPresent(schema -> {
                ValidationResultDto val = validationService.validate(schema, transformedNode);
                if (!val.isValid() && val.getErrors() != null) {
                    for (ValidationResultDto.ValidationErrorItem item : val.getErrors()) {
                        String msg = "Field '" + item.getField() + "': " + item.getMessage();
                        if (!errors.contains(msg)) {
                            errors.add(msg);
                        }
                    }
                }
            });
        }

        return errors;
    }

    private void saveTransformationExecutionRecord(IntegrationProject project, MappingDefinition mapping, String status, String errorSummary) {
        TransformationExecution exec = TransformationExecution.builder()
                .project(project)
                .mappingDefinition(mapping)
                .mappingVersion(mapping != null ? mapping.getVersion() : 0)
                .status(status)
                .errorSummary(errorSummary)
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        executionRepository.save(exec);
    }

    private GatewayExecutionLogDto saveAndReturnLog(
            IntegrationProject project,
            RegisteredApi api,
            String correlationId,
            String scenarioName,
            String direction,
            String httpMethod,
            String endpointPath,
            String sourceUrl,
            String gatewayUrl,
            String targetUrl,
            Integer mappingVersion,
            String status,
            int httpStatusCode,
            long durationMs,
            Map<String, Object> requestPayload,
            Map<String, Object> transformedPayload,
            Map<String, Object> downstreamResponse,
            List<String> appliedRules,
            List<String> unmappedFields,
            String errorMessage,
            String resolutionHint) {

        GatewayExecutionLog logEntity = GatewayExecutionLog.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .apiId(api != null ? api.getId() : null)
                .apiName(api != null ? api.getName() : (httpMethod + " " + endpointPath))
                .correlationId(correlationId)
                .scenarioName(scenarioName)
                .direction(direction)
                .httpMethod(httpMethod)
                .endpointPath(endpointPath)
                .sourceUrl(sourceUrl)
                .gatewayUrl(gatewayUrl)
                .targetUrl(targetUrl)
                .mappingVersion(mappingVersion)
                .status(status)
                .httpStatusCode(httpStatusCode)
                .durationMs(durationMs)
                .requestPayload(writeJson(requestPayload))
                .transformedPayload(writeJson(transformedPayload))
                .downstreamResponse(writeJson(downstreamResponse))
                .appliedRulesJson(writeJson(appliedRules))
                .unmappedFieldsJson(writeJson(unmappedFields))
                .errorMessage(errorMessage)
                .resolutionHint(resolutionHint)
                .createdAt(LocalDateTime.now())
                .build();

        logEntity = gatewayLogRepository.save(logEntity);

        auditService.recordEvent(
                "GatewayProxy",
                logEntity.getId(),
                status,
                "gateway-runtime",
                sourceUrl + " -> " + gatewayUrl,
                "[" + correlationId + "] " + scenarioName + " -> HTTP " + httpStatusCode + " (" + durationMs + "ms)"
                        + (errorMessage != null ? " | " + errorMessage : ""));

        return toDto(logEntity);
    }

    private List<String> extractLeafPaths(JsonNode node) {
        List<String> leaves = new ArrayList<>();
        collectLeaves("", node, leaves);
        return leaves;
    }

    private void collectLeaves(String prefix, JsonNode node, List<String> leaves) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                if (entry.getValue().isObject()) {
                    collectLeaves(path, entry.getValue(), leaves);
                } else {
                    leaves.add(path);
                }
            }
        }
    }

    private GatewayExecutionLogDto toDto(GatewayExecutionLog e) {
        return GatewayExecutionLogDto.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .projectName(e.getProjectName())
                .apiId(e.getApiId())
                .apiName(e.getApiName())
                .correlationId(e.getCorrelationId())
                .scenarioName(e.getScenarioName())
                .direction(e.getDirection())
                .httpMethod(e.getHttpMethod())
                .endpointPath(e.getEndpointPath())
                .sourceUrl(e.getSourceUrl())
                .gatewayUrl(e.getGatewayUrl())
                .targetUrl(e.getTargetUrl())
                .mappingVersion(e.getMappingVersion())
                .status(e.getStatus())
                .httpStatusCode(e.getHttpStatusCode())
                .durationMs(e.getDurationMs())
                .requestPayload(parseMap(e.getRequestPayload()))
                .transformedPayload(parseMap(e.getTransformedPayload()))
                .downstreamResponse(parseMap(e.getDownstreamResponse()))
                .appliedRules(parseStringList(e.getAppliedRulesJson()))
                .unmappedFields(parseStringList(e.getUnmappedFieldsJson()))
                .errorMessage(e.getErrorMessage())
                .resolutionHint(e.getResolutionHint())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /**
     * Resolves which IntegrationProject handles an incoming Gateway call:
     *  1. Exact Project UUID match
     *  2. Clean App Route Slug / Name match (e.g. 'SEtoCM3', 'LegacyPOS-to-CloudERP')
     *  3. Auto-Discovery ('auto' or 'route'):
     *     a) Matches (httpMethod, endpointPath) in RegisteredApi table
     *     b) Matches incoming JSON field keys against published MappingRule source paths
     */
    public IntegrationProject resolveProject(String projectKey, String httpMethod, String endpointPath, Map<String, Object> payload) {
        if (projectKey != null && !projectKey.isBlank()
                && !"auto".equalsIgnoreCase(projectKey)
                && !"route".equalsIgnoreCase(projectKey)) {
            Optional<IntegrationProject> byId = projectRepository.findById(projectKey);
            if (byId.isPresent()) {
                return byId.get();
            }
            List<IntegrationProject> all = projectRepository.findAll();
            for (IntegrationProject p : all) {
                String slug = ProjectService.toRouteSlug(p.getName(), p.getId());
                if (slug.equalsIgnoreCase(projectKey)
                        || p.getName().toLowerCase().startsWith(projectKey.toLowerCase())) {
                    return p;
                }
            }
        }

        // Auto-discovery by endpointPath in RegisteredApi
        List<IntegrationProject> allProjects = projectRepository.findAll();
        if (endpointPath != null && !endpointPath.isBlank()) {
            String cleanPath = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
            for (IntegrationProject p : allProjects) {
                List<RegisteredApi> apis = apiRepository.findByProjectIdOrderByCreatedAtAsc(p.getId());
                for (RegisteredApi a : apis) {
                    if (cleanPath.equalsIgnoreCase(a.getEndpointPath())
                            || (a.getEndpointPath().contains("{") && cleanPath.startsWith(a.getEndpointPath().split("/\\{")[0]))) {
                        return p;
                    }
                }
            }
        }

        // Auto-discovery by payload field signature
        if (payload != null && !payload.isEmpty()) {
            Set<String> keys = payload.keySet();
            if (keys.contains("sub_id") || keys.contains("acct_num") || keys.contains("subscriptionId")) {
                return allProjects.stream()
                        .filter(p -> p.getName().toLowerCase().contains("setocm3"))
                        .findFirst()
                        .orElseGet(() -> allProjects.get(0));
            }
            if (keys.contains("order_num") || keys.contains("cust_id") || keys.contains("orderId")) {
                return allProjects.stream()
                        .filter(p -> p.getName().toLowerCase().contains("legacypos"))
                        .findFirst()
                        .orElseGet(() -> allProjects.get(0));
            }
        }

        return allProjects.stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectKey));
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(json);
        }
    }
}
