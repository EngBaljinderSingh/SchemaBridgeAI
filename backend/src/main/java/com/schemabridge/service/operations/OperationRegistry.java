package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OperationRegistry {

    private final Map<TransformationOpType, TransformationOperation> operations = new EnumMap<>(TransformationOpType.class);

    public OperationRegistry(List<TransformationOperation> operationList) {
        for (TransformationOperation op : operationList) {
            operations.put(op.getOpType(), op);
        }
    }

    public TransformationOperation getOperation(TransformationOpType opType) {
        TransformationOperation op = operations.get(opType);
        if (op == null) {
            // Default to Rename if operation is unrecognized
            return operations.get(TransformationOpType.RENAME);
        }
        return op;
    }

    public boolean hasOperation(TransformationOpType opType) {
        return operations.containsKey(opType);
    }
}
