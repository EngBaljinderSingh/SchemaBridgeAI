package com.schemabridge.controller;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.dto.GatewayExecutionLogDto;
import com.schemabridge.dto.GatewayInvokeRequest;
import com.schemabridge.service.GatewayProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "3-URL Live Gateway Proxy & Telemetry", description = "Routes traffic across Source System URL -> SchemaBridge Gateway URL -> Target System URL with full execution & error logging")
public class GatewayController {

    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @GetMapping("/api/projects/{projectId}/gateway/logs")
    @Operation(summary = "List all live gateway execution & error logs for a project")
    public ResponseEntity<List<GatewayExecutionLogDto>> getProjectGatewayLogs(@PathVariable String projectId) {
        return ResponseEntity.ok(gatewayProxyService.getLogsByProject(projectId));
    }

    @DeleteMapping("/api/projects/{projectId}/gateway/logs")
    @Operation(summary = "Clear gateway execution logs for a project")
    public ResponseEntity<Void> clearProjectGatewayLogs(@PathVariable String projectId) {
        gatewayProxyService.clearLogs(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/projects/{projectId}/gateway/invoke")
    @Operation(summary = "Invoke the 3-URL Gateway Proxy for a specific API or test scenario")
    public ResponseEntity<GatewayExecutionLogDto> invokeGateway(
            @PathVariable String projectId,
            @RequestBody GatewayInvokeRequest request) {
        return ResponseEntity.ok(gatewayProxyService.invokeGateway(projectId, request));
    }

    @PostMapping("/api/projects/{projectId}/gateway/simulate-all")
    @Operation(summary = "Execute all 6 enterprise cases (Write, Read, Unmapped Fields, Missing Mapping, Transform Error, Validation Error)")
    public ResponseEntity<List<GatewayExecutionLogDto>> simulateAllCases(@PathVariable String projectId) {
        return ResponseEntity.ok(gatewayProxyService.simulateAllEnterpriseCases(projectId));
    }

    /**
     * Direct Live Gateway URL #2 Endpoint:
     * Callers (System A or cURL/Postman) can invoke:
     *   POST/GET http://localhost:8080/api/gateway/{projectId}/api/v1/subscriptions
     *   POST/GET http://localhost:8080/api/gateway/{projectId}/api/v1/orders
     */
    @RequestMapping(value = "/api/gateway/{projectId}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    @Operation(summary = "Direct SchemaBridge Gateway Proxy URL (URL #2) called by Source System A")
    public ResponseEntity<GatewayExecutionLogDto> handleDirectGatewayProxy(
            @PathVariable String projectId,
            @RequestParam(required = false) String unmappedPolicy,
            @RequestParam(required = false) String missingPolicy,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest servletRequest) {

        String fullUri = servletRequest.getRequestURI();
        String prefix = "/api/gateway/" + projectId;
        String endpointPath = fullUri.startsWith(prefix) ? fullUri.substring(prefix.length()) : "/api/v1/records";
        if (endpointPath.isEmpty()) {
            endpointPath = "/api/v1/records";
        }
        String method = servletRequest.getMethod();
        Direction dir = "GET".equalsIgnoreCase(method) ? Direction.TARGET_TO_SOURCE : Direction.SOURCE_TO_TARGET;

        Map<String, Object> effectivePayload = body;
        if (effectivePayload == null || effectivePayload.isEmpty()) {
            // If GET request without body, supply a sample cloud record from Target System B so GET proxy works out-of-the-box in browser/curl!
            effectivePayload = new LinkedHashMap<>();
            if (endpointPath.contains("subscription")) {
                effectivePayload.put("subscriptionId", "SUB-2026-9001");
                effectivePayload.put("tenantAccountId", "ACCT-OTX-4410");
                effectivePayload.put("subscriberName", "Global FinCorp Holdings");
                effectivePayload.put("billingStartDate", "2026-11-15");
                effectivePayload.put("autoRenewalEnabled", true);
                effectivePayload.put("tierCode", "ENT_PLUS");
                effectivePayload.put("licensedSeats", 250);
                effectivePayload.put("primaryContactEmail", "cloud-admin@fincorp.io");
                effectivePayload.put("primaryContactPhone", "+1-415-890-2211");
            } else {
                effectivePayload.put("orderId", "ORD-2026-991");
                effectivePayload.put("customerId", "CUST-4081");
                effectivePayload.put("userName", "Dr. Sarah Connor");
                effectivePayload.put("dateOfBirth", "1988-08-15");
                effectivePayload.put("accountEnabled", true);
                effectivePayload.put("membershipLevel", "GLD");
                effectivePayload.put("emailAddress", "sarah.c@cyberdyne.org");
                effectivePayload.put("telephone", "+1-555-0199");
                effectivePayload.put("totalAmount", 499.99);
            }
        }

        GatewayExecutionLogDto result = gatewayProxyService.invokeGateway(projectId, GatewayInvokeRequest.builder()
                .scenarioName("Direct Gateway Call: " + method + " " + endpointPath)
                .httpMethod(method)
                .endpointPath(endpointPath)
                .direction(dir)
                .payload(effectivePayload)
                .unmappedFieldPolicy(unmappedPolicy)
                .missingMappingPolicy(missingPolicy)
                .build());

        return ResponseEntity.status(result.getHttpStatusCode()).body(result);
    }

    /**
     * Built-in Mock Endpoints for URL #1 (Source System A) and URL #3 (Target System B)
     * so clicking or calling any of the 3 URLs works live during demos!
     */
    @RequestMapping(value = "/mock/{systemSlug}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT})
    @Operation(summary = "Built-in Mock Source System A & Target System B endpoints (URL #1 and URL #3)")
    public ResponseEntity<Map<String, Object>> handleMockSystemEndpoint(
            @PathVariable String systemSlug,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("mockSystem", systemSlug);
        resp.put("endpoint", request.getRequestURI());
        resp.put("method", request.getMethod());
        resp.put("timestamp", Instant.now().toString());
        if (systemSlug.contains("system-a") || systemSlug.contains("se-") || systemSlug.contains("pos-")) {
            resp.put("role", "URL #1 — Source System A (Legacy Origin)");
            resp.put("note", "To translate data to System B, send payloads to URL #2 (SchemaBridge Gateway URL: /api/gateway/{projectId}/...).");
        } else {
            resp.put("role", "URL #3 — Target System B (Modern Cloud Destination)");
            resp.put("status", "200 OK / 201 Created");
            resp.put("receivedPayload", body != null ? body : "GET Record Lookup");
        }
        return ResponseEntity.ok(resp);
    }
}
