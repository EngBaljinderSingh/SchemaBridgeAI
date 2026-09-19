package com.schemabridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaChangeAnalysisResponse {
    private String previousSchemaHash;
    private String newSchemaHash;
    private boolean hasChanges;

    @Builder.Default
    private List<FieldChange> addedFields = new ArrayList<>();
    @Builder.Default
    private List<FieldChange> removedFields = new ArrayList<>();
    @Builder.Default
    private List<FieldChange> modifiedFields = new ArrayList<>();
    @Builder.Default
    private List<ImpactedRule> impactedRules = new ArrayList<>();
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        private String fieldPath;
        private String changeType; // ADDED, REMOVED, TYPE_CHANGED, REQUIRED_CHANGED, ENUM_CHANGED
        private String description;
        private String previousValue;
        private String newValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactedRule {
        private String ruleId;
        private String targetPath;
        private String impactType; // BROKEN_SOURCE, BROKEN_TARGET, TYPE_MISMATCH
        private String suggestedRemedy;
    }
}
