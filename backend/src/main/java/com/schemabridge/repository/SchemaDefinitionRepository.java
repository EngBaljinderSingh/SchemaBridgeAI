package com.schemabridge.repository;

import com.schemabridge.domain.SchemaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemaDefinitionRepository extends JpaRepository<SchemaDefinition, String> {
    List<SchemaDefinition> findBySystemDefinitionId(String systemDefinitionId);
    Optional<SchemaDefinition> findFirstBySystemDefinitionIdOrderByCreatedAtDesc(String systemDefinitionId);
    Optional<SchemaDefinition> findBySchemaHash(String schemaHash);
}
