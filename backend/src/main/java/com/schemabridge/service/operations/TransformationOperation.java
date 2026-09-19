package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;

import java.util.List;
import java.util.Map;

public interface TransformationOperation {
    TransformationOpType getOpType();
    Object apply(List<Object> sourceValues, Map<String, Object> parameters);
}
