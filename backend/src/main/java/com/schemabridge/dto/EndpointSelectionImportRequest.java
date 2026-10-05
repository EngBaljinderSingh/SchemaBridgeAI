package com.schemabridge.dto;

import com.schemabridge.domain.enums.Direction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointSelectionImportRequest {
    private Direction direction;
    private String systemName;
    private String rawSpecContent;
    private List<String> selectedEndpointPaths;
}
