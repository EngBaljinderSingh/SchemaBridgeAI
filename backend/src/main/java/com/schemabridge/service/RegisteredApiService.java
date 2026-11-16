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
    private final SchemaExtractionService extractionService;
    private final MatchingPipeline matchingPipeline;
    private final TransformationEngine transformationEngine;
    private final GatewayProxyService gatewayProxyService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RegisteredApiService(
            RegisteredApiRepository apiRepository,
            IntegrationProjectRepository projectRepository,
            SchemaExtractionService extractionService,
            MatchingPipeline matchingPipeline,
            TransformationEngine transformationEngine,
            GatewayProxyService gatewayProxyService,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.projectRepository = projectRepository;
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
