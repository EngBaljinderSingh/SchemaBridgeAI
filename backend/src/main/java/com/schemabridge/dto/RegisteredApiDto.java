package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredApiDto {
    private String id;
    private String projectId;
    private String name;
    private String description;
    private String httpMethod;
    private String endpointPath;
    private Direction direction;
    private String targetUrl;
    private String sourceSchema;
    private String targetSchema;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
