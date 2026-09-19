package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EnumMapOperation implements TransformationOperation {

    @Override
    public TransformationOpType getOpType() {
        return TransformationOpType.ENUM_MAP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
        if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
            return parameters != null ? parameters.get("defaultValue") : null;
        }

        String sourceVal = sourceValues.get(0).toString().trim();
        if (parameters == null || !parameters.containsKey("mapping")) {
            return sourceVal;
        }

        Object mappingObj = parameters.get("mapping");
        if (mappingObj instanceof Map) {
            Map<String, Object> mapping = (Map<String, Object>) mappingObj;
            // Check exact match
            if (mapping.containsKey(sourceVal)) {
                return mapping.get(sourceVal);
            }
            // Check case-insensitive match
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(sourceVal)) {
                    return entry.getValue();
                }
            }
        }

        return parameters.containsKey("defaultValue") ? parameters.get("defaultValue") : sourceVal;
    }
}
