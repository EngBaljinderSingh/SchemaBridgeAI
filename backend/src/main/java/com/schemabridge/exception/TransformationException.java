package com.schemabridge.exception;

public class TransformationException extends SchemaBridgeException {
    public TransformationException(String message) {
        super("TRANSFORMATION_FAILED", message);
    }

    public TransformationException(String message, Throwable cause) {
        super("TRANSFORMATION_FAILED", message, cause);
    }
}
