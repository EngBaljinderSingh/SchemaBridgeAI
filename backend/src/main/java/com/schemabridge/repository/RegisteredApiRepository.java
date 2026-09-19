package com.schemabridge.repository;

import com.schemabridge.domain.RegisteredApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisteredApiRepository extends JpaRepository<RegisteredApi, String> {

    List<RegisteredApi> findByProjectIdOrderByCreatedAtAsc(String projectId);

    Optional<RegisteredApi> findByProjectIdAndId(String projectId, String id);

    boolean existsByProjectIdAndHttpMethodAndEndpointPath(String projectId, String httpMethod, String endpointPath);
}
