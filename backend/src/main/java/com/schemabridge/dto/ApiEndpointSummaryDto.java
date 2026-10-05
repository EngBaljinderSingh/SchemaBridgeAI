package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpointSummaryDto {
    private String httpMethod;
    private String endpointPath;
    private String summary;
    private String operationId;
    private String schemaModelName;
    private String description;
}
