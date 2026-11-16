package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoMatchEndpointsRequest {
    private String sourceSpec;
    private String targetSpec;
}
