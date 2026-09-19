package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AviatorHealthResponse {
    private String status; // UP, DEGRADED, DOWN
    private String provider;
    private String model;
    private String location;
    private long latencyMs;
    private String message;
    private Map<String, Object> details;
}
