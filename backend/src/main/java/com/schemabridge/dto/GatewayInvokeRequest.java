package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayInvokeRequest {
    private String apiId;
    private String scenarioName;
    @Builder.Default
    private String httpMethod = "POST";
    private String endpointPath;
    @Builder.Default
    private Direction direction = Direction.SOURCE_TO_TARGET;
    private Map<String, Object> payload;
    private String unmappedFieldPolicy;   // DROP_WITH_WARNING, PASS_THROUGH, STRICT_REJECT
    private String missingMappingPolicy;  // STRICT_REJECT_422, PASS_THROUGH_WARNING
    @Builder.Default
    private boolean forceMissingMapping = false;
}
