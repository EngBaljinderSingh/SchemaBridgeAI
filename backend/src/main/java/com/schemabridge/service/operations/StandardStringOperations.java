package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardStringOperations {

    @Component
    public static class RenameOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.RENAME;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty()) {
                return null;
            }
            return sourceValues.get(0);
        }
    }

    @Component
    public static class CopyOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.COPY;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty()) {
                return null;
            }
            return sourceValues.get(0);
        }
    }

    @Component
    public static class TrimOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.TRIM;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            return sourceValues.get(0).toString().trim();
        }
    }

    @Component
    public static class UppercaseOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.UPPERCASE;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            return sourceValues.get(0).toString().toUpperCase();
        }
    }

    @Component
    public static class LowercaseOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.LOWERCASE;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            return sourceValues.get(0).toString().toLowerCase();
        }
    }

    @Component
    public static class ConcatOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.CONCAT;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty()) {
                return "";
            }
            String separator = " ";
            if (parameters != null && parameters.containsKey("separator")) {
                separator = String.valueOf(parameters.get("separator"));
            }
            return sourceValues.stream()
                    .filter(v -> v != null && !v.toString().isBlank())
                    .map(Object::toString)
                    .collect(Collectors.joining(separator));
        }
    }

    @Component
    public static class SplitOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.SPLIT;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            String val = sourceValues.get(0).toString();
            String separator = ",";
            if (parameters != null && parameters.containsKey("separator")) {
                separator = String.valueOf(parameters.get("separator"));
            }
            String[] parts = val.split(separator);
            if (parameters != null && parameters.containsKey("index")) {
                int index = Integer.parseInt(parameters.get("index").toString());
                if (index >= 0 && index < parts.length) {
                    return parts[index].trim();
                }
                return "";
            }
            return Arrays.stream(parts).map(String::trim).collect(Collectors.toList());
        }
    }
}
