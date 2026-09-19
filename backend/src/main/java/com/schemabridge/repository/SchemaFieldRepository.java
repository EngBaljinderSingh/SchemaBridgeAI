package com.schemabridge.repository;

import com.schemabridge.domain.SchemaField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemaFieldRepository extends JpaRepository<SchemaField, String> {
    List<SchemaField> findBySchemaDefinitionId(String schemaDefinitionId);
    void deleteBySchemaDefinitionId(String schemaDefinitionId);
}
