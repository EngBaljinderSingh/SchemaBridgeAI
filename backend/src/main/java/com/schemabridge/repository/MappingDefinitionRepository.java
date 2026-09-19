package com.schemabridge.repository;

import com.schemabridge.domain.MappingDefinition;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MappingDefinitionRepository extends JpaRepository<MappingDefinition, String> {
    List<MappingDefinition> findByProjectId(String projectId);
    List<MappingDefinition> findByProjectIdOrderByVersionDesc(String projectId);
    Optional<MappingDefinition> findByProjectIdAndVersion(String projectId, Integer version);
    Optional<MappingDefinition> findFirstByProjectIdAndDirectionAndStatusOrderByVersionDesc(
            String projectId, Direction direction, MappingStatus status);
}
