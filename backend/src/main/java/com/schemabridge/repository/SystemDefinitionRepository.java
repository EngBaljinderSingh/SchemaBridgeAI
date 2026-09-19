package com.schemabridge.repository;

import com.schemabridge.domain.SystemDefinition;
import com.schemabridge.domain.enums.Direction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemDefinitionRepository extends JpaRepository<SystemDefinition, String> {
    List<SystemDefinition> findByProjectId(String projectId);
    Optional<SystemDefinition> findByProjectIdAndDirection(String projectId, Direction direction);
}
