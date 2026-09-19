package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.util.*;

public class StructuralAndConditionalOperations {

    @Component
    public static class DefaultValueOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.DEFAULT_VALUE;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            Object fallback = parameters != null ? parameters.get("defaultValue") : null;
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return fallback;
            }
            Object val = sourceValues.get(0);
            if (val instanceof String str && str.trim().isEmpty()) {
                return fallback;
            }
            return val;
        }
    }

    @Component
    public static class ConstantValueOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.CONSTANT_VALUE;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            return parameters != null ? parameters.get("value") : null;
        }
    }

    @Component
    public static class ConditionalOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.CONDITIONAL;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || parameters == null) {
                return null;
            }

            Object sourceVal = sourceValues.get(0);
            String operator = parameters.getOrDefault("operator", "EQUALS").toString().toUpperCase();
            Object expected = parameters.get("expectedValue");
            Object trueVal = parameters.get("trueValue");
            Object falseVal = parameters.get("falseValue");

            boolean conditionMet = false;
            String sourceStr = sourceVal != null ? sourceVal.toString() : "";
            String expectedStr = expected != null ? expected.toString() : "";

            switch (operator) {
                case "EQUALS" -> conditionMet = sourceStr.equalsIgnoreCase(expectedStr);
                case "NOT_EQUALS" -> conditionMet = !sourceStr.equalsIgnoreCase(expectedStr);
                case "CONTAINS" -> conditionMet = sourceStr.contains(expectedStr);
                case "NOT_EMPTY" -> conditionMet = sourceVal != null && !sourceStr.isBlank();
                case "EMPTY" -> conditionMet = sourceVal == null || sourceStr.isBlank();
                default -> conditionMet = Objects.equals(sourceVal, expected);
            }

            return conditionMet ? trueVal : falseVal;
        }
    }

    @Component
    public static class RemoveOperation implements TransformationOperation {
        public static final String REMOVE_MARKER = "__SCHEMA_BRIDGE_REMOVE__";

        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.REMOVE;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            return REMOVE_MARKER;
        }
    }

    @Component
    public static class FlattenOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.FLATTEN;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            Object val = sourceValues.get(0);
            if (val instanceof List<?> list) {
                List<Object> flattened = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof List<?> innerList) {
                        flattened.addAll(innerList);
                    } else {
                        flattened.add(item);
                    }
                }
                return flattened;
            }
            return val;
        }
    }

    @Component
    public static class NestOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.NEST;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty()) {
                return null;
            }
            String key = (parameters != null && parameters.containsKey("key"))
                    ? parameters.get("key").toString() : "value";
            Map<String, Object> nested = new HashMap<>();
            nested.put(key, sourceValues.get(0));
            return nested;
        }
    }

    @Component
    public static class ArrayMapOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.ARRAY_MAP;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || !(sourceValues.get(0) instanceof List<?> list)) {
                return sourceValues != null && !sourceValues.isEmpty() ? sourceValues.get(0) : Collections.emptyList();
            }
            // For general array mapping, pass through or transform element items
            return list;
        }
    }
}
