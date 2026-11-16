package com.schemabridge.repository;

import com.schemabridge.domain.GatewayExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GatewayExecutionLogRepository extends JpaRepository<GatewayExecutionLog, String> {
    List<GatewayExecutionLog> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<GatewayExecutionLog> findTop100ByOrderByCreatedAtDesc();
    void deleteByProjectId(String projectId);
}
