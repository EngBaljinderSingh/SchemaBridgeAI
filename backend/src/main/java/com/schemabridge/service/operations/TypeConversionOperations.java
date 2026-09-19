package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeConversionOperations {

    private static final Set<String> TRUTHY_VALUES = Set.of(
            "yes", "y", "true", "t", "1", "active", "enabled", "on"
    );
    private static final Set<String> FALSY_VALUES = Set.of(
            "no", "n", "false", "f", "0", "inactive", "disabled", "off"
    );

    @Component
    public static class StringToNumberOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.STRING_TO_NUMBER;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            String val = sourceValues.get(0).toString().trim();
            if (val.isEmpty()) {
                return null;
            }
            try {
                if (val.contains(".")) {
                    return Double.parseDouble(val);
                } else {
                    return Long.parseLong(val);
                }
            } catch (NumberFormatException e) {
                return new BigDecimal(val);
            }
        }
    }

    @Component
    public static class NumberToStringOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.NUMBER_TO_STRING;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            return String.valueOf(sourceValues.get(0));
        }
    }

    @Component
    public static class StringToBooleanOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.STRING_TO_BOOLEAN;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            String val = sourceValues.get(0).toString().trim().toLowerCase();
            if (TRUTHY_VALUES.contains(val)) {
                return Boolean.TRUE;
            }
            if (FALSY_VALUES.contains(val)) {
                return Boolean.FALSE;
            }
            return Boolean.parseBoolean(val);
        }
    }

    @Component
    public static class BooleanToStringOperation implements TransformationOperation {
        @Override
        public TransformationOpType getOpType() {
            return TransformationOpType.BOOLEAN_TO_STRING;
        }

        @Override
        public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
            if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
                return null;
            }
            boolean b = Boolean.parseBoolean(sourceValues.get(0).toString());
            String trueVal = (parameters != null && parameters.containsKey("trueValue"))
                    ? parameters.get("trueValue").toString() : "Yes";
            String falseVal = (parameters != null && parameters.containsKey("falseValue"))
                    ? parameters.get("falseValue").toString() : "No";
            return b ? trueVal : falseVal;
        }
    }
}
