package com.schemabridge.service;

import com.schemabridge.domain.AuditEvent;
import com.schemabridge.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void recordEvent(String entityType, String entityId, String action, String changedBy, String previousValue, String newValue) {
        AuditEvent event = AuditEvent.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changedBy(changedBy != null ? changedBy : "system")
                .previousValue(previousValue)
                .newValue(newValue)
                .createdAt(LocalDateTime.now())
                .build();
        auditEventRepository.save(event);
        log.info("Audit recorded: [{}] entity={} id={} action={}", entityType, entityType, entityId, action);
    }

    public List<AuditEvent> getEventsForEntity(String entityType, String entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    public List<AuditEvent> getAllEvents() {
        return auditEventRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void notifyApprover(String approverEmail, String subject, String messageDetails) {
        String recipient = (approverEmail != null && !approverEmail.isBlank()) ? approverEmail : "approver@enterprise.com";
        recordEvent("NOTIFICATION", recipient, "EMAIL_DISPATCH", "system", null,
                "Notification to " + recipient + " | Subject: " + subject + " | " + messageDetails);
        log.info("DISPATCH NOTIFICATION EMAIL: To: {} | Subject: {} | Message: {}", recipient, subject, messageDetails);
    }
}
