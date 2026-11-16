package com.schemabridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.IntegrationProject;
import com.schemabridge.domain.RegisteredApi;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.*;
import com.schemabridge.exception.ResourceNotFoundException;
import com.schemabridge.exception.ValidationException;
import com.schemabridge.repository.IntegrationProjectRepository;
import com.schemabridge.repository.RegisteredApiRepository;
import com.schemabridge.service.matching.MatchingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RegisteredApiService {

    private static final Logger log = LoggerFactory.getLogger(RegisteredApiService.class);

    private final RegisteredApiRepository apiRepository;
    private final IntegrationProjectRepository projectRepository;
    private final ProjectService projectService;
    private final SchemaExtractionService extractionService;
    private final MatchingPipeline matchingPipeline;
    private final TransformationEngine transformationEngine;
    private final GatewayProxyService gatewayProxyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RegisteredApiService(
            RegisteredApiRepository apiRepository,
            IntegrationProjectRepository projectRepository,
            ProjectService projectService,
            SchemaExtractionService extractionService,
            MatchingPipeline matchingPipeline,
            TransformationEngine transformationEngine,
            GatewayProxyService gatewayProxyService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.extractionService = extractionService;
        this.matchingPipeline = matchingPipeline;
        this.transformationEngine = transformationEngine;
        this.gatewayProxyService = gatewayProxyService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<RegisteredApiDto> getApis(String projectId) {
        return apiRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    public RegisteredApiDto getApiById(String projectId, String apiId) {
        RegisteredApi api = apiRepository.findByProjectIdAndId(projectId, apiId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredApi", apiId));
        return toDto(api);
    }

    @Transactional
    public RegisteredApiDto createApi(String projectId, ApiCreateRequest req) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        String method = (req.getHttpMethod() != null ? req.getHttpMethod().toUpperCase() : "POST").trim();
        String path = req.getEndpointPath().trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String defaultSrcBase = project.getSourceSystemUrl() != null ? project.getSourceSystemUrl() : "http://localhost:8080/mock/system-a";
        String defaultTgtBase = project.getTargetSystemUrl() != null ? project.getTargetSystemUrl() : "http://localhost:8080/mock/system-b";
        String srcUrl = (req.getSourceUrl() != null && !req.getSourceUrl().isBlank())
                ? req.getSourceUrl().trim() : (defaultSrcBase + path);
        String tgtUrl = (req.getTargetUrl() != null && !req.getTargetUrl().isBlank())
                ? req.getTargetUrl().trim() : (defaultTgtBase + path);

        RegisteredApi api = RegisteredApi.builder()
                .project(project)
                .name(req.getName().trim())
                .description(req.getDescription())
                .httpMethod(method)
                .endpointPath(path)
                .direction(req.getDirection() != null ? req.getDirection() : Direction.SOURCE_TO_TARGET)
                .sourceUrl(srcUrl)
                .targetUrl(tgtUrl)
                .sourceSchema(req.getSourceSchema())
                .targetSchema(req.getTargetSchema())
                .status("ACTIVE")
                .build();

        api = apiRepository.save(api);
        auditService.recordEvent("RegisteredApi", api.getId(), "CREATE", "system", null,
                "Registered API: " + method + " " + path + " under App " + project.getName());

        return toDto(api);
    }

    @Transactional
    public RegisteredApiDto updateApi(String projectId, String apiId, ApiUpdateRequest req) {
        RegisteredApi api = apiRepository.findByProjectIdAndId(projectId, apiId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredApi", apiId));

        if (req.getName() != null && !req.getName().isBlank()) api.setName(req.getName().trim());
        if (req.getDescription() != null) api.setDescription(req.getDescription());
        if (req.getHttpMethod() != null && !req.getHttpMethod().isBlank()) api.setHttpMethod(req.getHttpMethod().toUpperCase().trim());
        if (req.getEndpointPath() != null && !req.getEndpointPath().isBlank()) {
            String p = req.getEndpointPath().trim();
            api.setEndpointPath(p.startsWith("/") ? p : "/" + p);
        }
        if (req.getDirection() != null) api.setDirection(req.getDirection());
        if (req.getSourceUrl() != null) api.setSourceUrl(req.getSourceUrl());
        if (req.getTargetUrl() != null) api.setTargetUrl(req.getTargetUrl());
        if (req.getSourceSchema() != null) api.setSourceSchema(req.getSourceSchema());
        if (req.getTargetSchema() != null) api.setTargetSchema(req.getTargetSchema());
        if (req.getStatus() != null && !req.getStatus().isBlank()) api.setStatus(req.getStatus().trim());

        api = apiRepository.save(api);
        auditService.recordEvent("RegisteredApi", api.getId(), "UPDATE", "system", null,
                "Updated details for API: " + api.getName());

        return toDto(api);
    }

    @Transactional
    public void deleteApi(String projectId, String apiId) {
        RegisteredApi api = apiRepository.findByProjectIdAndId(projectId, apiId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredApi", apiId));
        apiRepository.delete(api);
        auditService.recordEvent("RegisteredApi", apiId, "DELETE", "system", null,
                "Deleted API: " + api.getHttpMethod() + " " + api.getEndpointPath());
    }

    /**
     * Generates 5-level mapping suggestions specifically for this registered API's schemas.
     */
    public List<MappingRuleDto> generateMappingsForApi(String projectId, String apiId) {
        RegisteredApi api = apiRepository.findByProjectIdAndId(projectId, apiId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredApi", apiId));

        if (api.getSourceSchema() == null || api.getSourceSchema().isBlank()) {
            throw new ValidationException("Source schema/sample is not configured for API " + api.getName());
        }
        if (api.getTargetSchema() == null || api.getTargetSchema().isBlank()) {
            throw new ValidationException("Target schema/sample is not configured for API " + api.getName());
        }

        List<FieldExtractionDto> srcFields = extractionService.extractFields(api.getSourceSchema(), SchemaType.SAMPLE_JSON);
        List<FieldExtractionDto> tgtFields = extractionService.extractFields(api.getTargetSchema(), SchemaType.SAMPLE_JSON);

        MatchingPipeline.PipelineResult result = matchingPipeline.execute(srcFields, tgtFields, projectId, api.getName());
        return result.rules();
    }

    /**
     * Executes deterministic transformation for a payload against this registered API and logs to the 3-URL Gateway.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTransformForApi(String projectId, String apiId, Object inputPayload) {
        RegisteredApi api = apiRepository.findByProjectIdAndId(projectId, apiId)
                .orElseThrow(() -> new ResourceNotFoundException("RegisteredApi", apiId));

        Map<String, Object> payloadMap = inputPayload instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();

        GatewayExecutionLogDto logDto = gatewayProxyService.invokeGateway(projectId, GatewayInvokeRequest.builder()
                .apiId(api.getId())
                .scenarioName("API Test: " + api.getName())
                .httpMethod(api.getHttpMethod())
                .endpointPath(api.getEndpointPath())
                .direction(api.getDirection())
                .payload(payloadMap)
                .build());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("apiId", apiId);
        resp.put("projectId", projectId);
        resp.put("correlationId", logDto.getCorrelationId());
        resp.put("sourceUrl", logDto.getSourceUrl());
        resp.put("gatewayUrl", logDto.getGatewayUrl());
        resp.put("targetUrl", logDto.getTargetUrl());
        resp.put("httpStatusCode", logDto.getHttpStatusCode());
        resp.put("status", logDto.getStatus());
        resp.put("durationMs", logDto.getDurationMs());
        resp.put("transformedPayload", logDto.getTransformedPayload());
        resp.put("appliedRules", logDto.getAppliedRules());
        resp.put("unmappedFields", logDto.getUnmappedFields());
        resp.put("downstreamResponse", logDto.getDownstreamResponse());
        if (logDto.getErrorMessage() != null) {
            resp.put("errorMessage", logDto.getErrorMessage());
            resp.put("resolutionHint", logDto.getResolutionHint());
        }
        return resp;
    }

    /**
     * Auto-matches API endpoints between System A (source) and System B (target) OpenAPI/Swagger specs.
     */
    public List<AutoMappedApiPairDto> autoMatchEndpoints(String projectId, AutoMatchEndpointsRequest req) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        String srcSpec = req != null ? req.getSourceSpec() : null;
        String tgtSpec = req != null ? req.getTargetSpec() : null;

        if ((srcSpec == null || srcSpec.isBlank()) || (tgtSpec == null || tgtSpec.isBlank())) {
            List<SchemaResponse> schemas = projectService.getSchemasByProject(projectId);
            for (SchemaResponse s : schemas) {
                if ((srcSpec == null || srcSpec.isBlank()) && s.getDirection() == Direction.SOURCE_TO_TARGET
                        && s.getOriginalSchema() != null && !s.getOriginalSchema().isBlank()) {
                    srcSpec = s.getOriginalSchema();
                }
                if ((tgtSpec == null || tgtSpec.isBlank()) && s.getDirection() == Direction.TARGET_TO_SOURCE
                        && s.getOriginalSchema() != null && !s.getOriginalSchema().isBlank()) {
                    tgtSpec = s.getOriginalSchema();
                }
            }
        }

        if (srcSpec == null || srcSpec.isBlank()) {
            throw new ValidationException("Source Swagger/OpenAPI specification is required.");
        }
        if (tgtSpec == null || tgtSpec.isBlank()) {
            throw new ValidationException("Target Swagger/OpenAPI specification is required.");
        }

        List<ApiEndpointSummaryDto> sourceEndpoints = extractionService.extractEndpointsFromSpec(srcSpec);
        List<ApiEndpointSummaryDto> targetEndpoints = extractionService.extractEndpointsFromSpec(tgtSpec);

        if (sourceEndpoints.isEmpty()) {
            throw new ValidationException("No API endpoints found in Source Swagger/OpenAPI spec.");
        }
        if (targetEndpoints.isEmpty()) {
            throw new ValidationException("No API endpoints found in Target Swagger/OpenAPI spec.");
        }

        List<AutoMappedApiPairDto> results = new ArrayList<>();
        Set<Integer> usedTargetIndices = new HashSet<>();

        for (ApiEndpointSummaryDto src : sourceEndpoints) {
            int bestIdx = -1;
            double bestScore = -1.0;
            String bestReason = "Unmatched — please select target endpoint";

            for (int i = 0; i < targetEndpoints.size(); i++) {
                ApiEndpointSummaryDto tgt = targetEndpoints.get(i);
                EndpointScore score = scoreEndpointPair(src, tgt, usedTargetIndices.contains(i));
                if (score.score() > bestScore) {
                    bestScore = score.score();
                    bestIdx = i;
                    bestReason = score.reason();
                }
            }

            ApiEndpointSummaryDto matchedTgt = bestIdx >= 0 ? targetEndpoints.get(bestIdx) : targetEndpoints.get(0);
            if (bestIdx >= 0 && bestScore >= 0.55) {
                usedTargetIndices.add(bestIdx);
            }

            double clampedScore = Math.max(0.25, Math.min(0.99, Math.round(bestScore * 100.0) / 100.0));
            boolean reviewNeeded = clampedScore < 0.85;

            String srcSample = buildSampleJsonForEndpoint(srcSpec, src.getEndpointPath());
            String tgtSample = buildSampleJsonForEndpoint(tgtSpec, matchedTgt.getEndpointPath());

            String suggestedName = (src.getSummary() != null && !src.getSummary().isBlank())
                    ? src.getSummary()
                    : (src.getHttpMethod() + " " + src.getEndpointPath());

            results.add(AutoMappedApiPairDto.builder()
                    .sourceMethod(src.getHttpMethod())
                    .sourcePath(src.getEndpointPath())
                    .sourceSummary(src.getSummary())
                    .sourceModelName(src.getSchemaModelName())
                    .targetMethod(matchedTgt.getHttpMethod())
                    .targetPath(matchedTgt.getEndpointPath())
                    .targetSummary(matchedTgt.getSummary())
                    .targetModelName(matchedTgt.getSchemaModelName())
                    .suggestedName(suggestedName)
                    .confidence(clampedScore)
                    .matchReason(bestReason)
                    .requiresReview(reviewNeeded)
                    .selected(clampedScore >= 0.55)
                    .sourceSampleJson(srcSample)
                    .targetSampleJson(tgtSample)
                    .build());
        }

        return results;
    }

    /**
     * Bulk-registers approved API endpoint pairs under the project, skipping duplicates.
     */
    @Transactional
    public BulkRegisterApisResponse bulkRegisterApis(String projectId, List<ApiCreateRequest> requests) {
        IntegrationProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<RegisteredApi> existing = apiRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        Set<String> existingKeys = new HashSet<>();
        for (RegisteredApi e : existing) {
            existingKeys.add((e.getHttpMethod() + "::" + e.getEndpointPath()).toUpperCase());
        }

        List<RegisteredApiDto> created = new ArrayList<>();
        int skipped = 0;

        for (ApiCreateRequest req : requests) {
            String method = (req.getHttpMethod() != null ? req.getHttpMethod().toUpperCase() : "POST").trim();
            String path = req.getEndpointPath() != null ? req.getEndpointPath().trim() : "/";
            if (!path.startsWith("/")) path = "/" + path;

            String key = (method + "::" + path).toUpperCase();
            if (existingKeys.contains(key)) {
                skipped++;
                continue;
            }
            existingKeys.add(key);
            created.add(createApi(projectId, req));
        }

        auditService.recordEvent("RegisteredApi", projectId, "BULK_REGISTER", "system", null,
                "Auto-registered " + created.size() + " APIs from Swagger under " + project.getName() + " (skipped " + skipped + ")");

        return BulkRegisterApisResponse.builder()
                .created(created)
                .skippedCount(skipped)
                .build();
    }

    private record EndpointScore(double score, String reason) {}

    private EndpointScore scoreEndpointPair(ApiEndpointSummaryDto src, ApiEndpointSummaryDto tgt, boolean alreadyUsed) {
        String sMethod = src.getHttpMethod() != null ? src.getHttpMethod().toUpperCase() : "";
        String tMethod = tgt.getHttpMethod() != null ? tgt.getHttpMethod().toUpperCase() : "";
        String sPath = src.getEndpointPath() != null ? src.getEndpointPath() : "";
        String tPath = tgt.getEndpointPath() != null ? tgt.getEndpointPath() : "";

        if (sMethod.equals(tMethod) && sPath.equalsIgnoreCase(tPath)) {
            return new EndpointScore(0.99, "Exact HTTP method & path match");
        }

        double score = 0.0;
        List<String> reasons = new ArrayList<>();

        if (sMethod.equals(tMethod)) {
            score += 0.40;
            reasons.add("Same HTTP " + sMethod);
        } else {
            score += 0.05;
        }

        Set<String> sTokens = tokenizeEndpoint(src);
        Set<String> tTokens = tokenizeEndpoint(tgt);
        if (!sTokens.isEmpty() && !tTokens.isEmpty()) {
            Set<String> intersection = new HashSet<>(sTokens);
            intersection.retainAll(tTokens);
            Set<String> union = new HashSet<>(sTokens);
            union.addAll(tTokens);
            double jaccard = (double) intersection.size() / union.size();
            score += jaccard * 0.55;
            if (!intersection.isEmpty()) {
                reasons.add("Shared domain terms: " + String.join(", ", intersection));
            }
        }

        // Parameter-count parity bonus (e.g. /{id} <-> /{subscriptionId})
        boolean sHasParam = sPath.contains("{");
        boolean tHasParam = tPath.contains("{");
        if (sHasParam == tHasParam) {
            score += 0.05;
        }

        if (alreadyUsed) {
            score -= 0.15;
        }

        String reasonText = reasons.isEmpty() ? "Low semantic overlap" : String.join(" + ", reasons);
        return new EndpointScore(score, reasonText);
    }

    private Set<String> tokenizeEndpoint(ApiEndpointSummaryDto ep) {
        String combined = String.join(" ",
                ep.getEndpointPath() != null ? ep.getEndpointPath() : "",
                ep.getOperationId() != null ? ep.getOperationId() : "",
                ep.getSummary() != null ? ep.getSummary() : "",
                ep.getSchemaModelName() != null ? ep.getSchemaModelName() : "");

        String splitCamel = combined.replaceAll("([a-z])([A-Z])", "$1 $2");
        String[] raw = splitCamel.toLowerCase().split("[^a-z0-9]+");

        Map<String, String> synonyms = Map.ofEntries(
                Map.entry("sub", "subscription"),
                Map.entry("subs", "subscription"),
                Map.entry("subscriptions", "subscription"),
                Map.entry("acct", "account"),
                Map.entry("accounts", "account"),
                Map.entry("cust", "customer"),
                Map.entry("customers", "customer"),
                Map.entry("ord", "order"),
                Map.entry("orders", "order"),
                Map.entry("inv", "invoice"),
                Map.entry("invoices", "invoice"),
                Map.entry("prod", "product"),
                Map.entry("products", "product"),
                Map.entry("usr", "user"),
                Map.entry("users", "user")
        );
        Set<String> stopWords = Set.of("api", "v1", "v2", "v3", "se", "cm3", "legacy", "cloud", "erp", "pos", "inlinepayload", "id", "get", "post", "put", "create", "update", "fetch", "read");

        Set<String> tokens = new LinkedHashSet<>();
        for (String w : raw) {
            if (w.length() < 2 || stopWords.contains(w)) continue;
            tokens.add(synonyms.getOrDefault(w, w));
        }
        return tokens;
    }

    private String buildSampleJsonForEndpoint(String spec, String endpointPath) {
        try {
            List<FieldExtractionDto> fields = extractionService.extractFieldsForEndpoints(spec, List.of(endpointPath));
            Map<String, Object> sample = new LinkedHashMap<>();
            for (FieldExtractionDto f : fields) {
                if (f.getFieldPath() == null || f.getFieldPath().contains(".")) continue;
                Object val = f.getSampleValue();
                if (val == null || val.toString().isBlank()) {
                    String dt = f.getDataType() != null ? f.getDataType().toUpperCase() : "STRING";
                    val = switch (dt) {
                        case "NUMBER", "INTEGER" -> 100;
                        case "BOOLEAN" -> true;
                        case "DATE" -> "2026-11-16";
                        default -> "sample_" + f.getFieldPath();
                    };
                }
                sample.put(f.getFieldPath(), val);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            return "{}";
        }
    }

    private RegisteredApiDto toDto(RegisteredApi a) {
        String projId = a.getProject().getId();
        String ep = a.getEndpointPath() != null && a.getEndpointPath().startsWith("/")
                ? a.getEndpointPath() : "/" + (a.getEndpointPath() != null ? a.getEndpointPath() : "");
        String defaultSrcBase = a.getProject().getSourceSystemUrl() != null
                ? a.getProject().getSourceSystemUrl() : "http://localhost:8080/mock/system-a";
        String defaultTgtBase = a.getProject().getTargetSystemUrl() != null
                ? a.getProject().getTargetSystemUrl() : "http://localhost:8080/mock/system-b";
        String resolvedSourceUrl = (a.getSourceUrl() != null && !a.getSourceUrl().isBlank())
                ? a.getSourceUrl() : (defaultSrcBase + ep);
        String resolvedTargetUrl = (a.getTargetUrl() != null && !a.getTargetUrl().isBlank())
                ? a.getTargetUrl() : (defaultTgtBase + ep);
        String routeSlug = ProjectService.toRouteSlug(a.getProject().getName(), projId);
        String gatewayUrl = "http://localhost:8080/api/gateway/" + routeSlug + ep;

        return RegisteredApiDto.builder()
                .id(a.getId())
                .projectId(projId)
                .name(a.getName())
                .description(a.getDescription())
                .httpMethod(a.getHttpMethod())
                .endpointPath(a.getEndpointPath())
                .direction(a.getDirection())
                .sourceUrl(resolvedSourceUrl)
                .gatewayUrl(gatewayUrl)
                .targetUrl(resolvedTargetUrl)
                .sourceSchema(a.getSourceSchema())
                .targetSchema(a.getTargetSchema())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
