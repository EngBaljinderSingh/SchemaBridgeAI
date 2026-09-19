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
public class FieldExtractionDto {
    private String id;
    private String fieldPath;
    private String fieldName;
    private String description;
    private String dataType;
    private String format;
    private boolean required;
    private boolean array;
    private String parentPath;
    private Object sampleValue;
    private List<String> enumValues;
}
