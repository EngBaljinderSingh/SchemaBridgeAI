package com.schemabridge.repository;

import com.schemabridge.domain.MappingRule;
import com.schemabridge.domain.enums.MappingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MappingRuleRepository extends JpaRepository<MappingRule, String> {
    List<MappingRule> findByMappingDefinitionId(String mappingDefinitionId);
    void deleteByMappingDefinitionId(String mappingDefinitionId);

    @Query("SELECT r FROM MappingRule r WHERE r.status = :status AND r.sourcePaths LIKE %:sourcePath% AND r.targetPath = :targetPath")
    List<MappingRule> findApprovedHistoricalRules(String sourcePath, String targetPath, MappingStatus status);
}
