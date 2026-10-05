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
public class TracePayloadRequest {
    private Direction direction;
    private String systemName;
    private String endpointPath;
    @NotBlank(message = "Payload content is required")
    private String payload;
}
