package com.schemabridge.repository;

import com.schemabridge.domain.TransformationExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransformationExecutionRepository extends JpaRepository<TransformationExecution, String> {
    List<TransformationExecution> findByProjectIdOrderByStartedAtDesc(String projectId);
}
