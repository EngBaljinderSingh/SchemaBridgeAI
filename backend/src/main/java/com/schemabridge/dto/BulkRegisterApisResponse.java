package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkRegisterApisResponse {
    private List<RegisteredApiDto> created;
    private int skippedCount;
}
