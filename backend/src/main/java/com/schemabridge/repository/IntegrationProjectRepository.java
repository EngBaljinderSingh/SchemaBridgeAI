package com.schemabridge.repository;

import com.schemabridge.domain.IntegrationProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IntegrationProjectRepository extends JpaRepository<IntegrationProject, String> {
}
