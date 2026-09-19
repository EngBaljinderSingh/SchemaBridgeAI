package com.schemabridge.controller;

import com.schemabridge.domain.AuditEvent;
import com.schemabridge.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Audit History", description = "Query audit log events for approvals, rules changes, and transformations")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/projects/{projectId}/audit")
    @Operation(summary = "Get audit trail for a specific project")
    public ResponseEntity<List<AuditEvent>> getProjectAudit(@PathVariable String projectId) {
        return ResponseEntity.ok(auditService.getEventsForEntity("IntegrationProject", projectId));
    }

    @GetMapping("/audit")
    @Operation(summary = "Get full global audit log")
    public ResponseEntity<List<AuditEvent>> getAllAuditEvents() {
        return ResponseEntity.ok(auditService.getAllEvents());
    }
}
