package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCreateRequest {

    @NotBlank(message = "API name is required")
    private String name;

    private String description;

    @Builder.Default
    private String httpMethod = "POST";

    @NotBlank(message = "Endpoint path is required (e.g. /customers or /orders)")
    private String endpointPath;

    @Builder.Default
    private Direction direction = Direction.SOURCE_TO_TARGET;

    private String targetUrl;

    private String sourceSchema;

    private String targetSchema;
}
