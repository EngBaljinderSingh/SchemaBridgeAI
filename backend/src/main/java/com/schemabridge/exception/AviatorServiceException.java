package com.schemabridge.exception;

public class AviatorServiceException extends SchemaBridgeException {
    public AviatorServiceException(String message) {
        super("AVIATOR_SERVICE_ERROR", message);
    }

    public AviatorServiceException(String message, Throwable cause) {
        super("AVIATOR_SERVICE_ERROR", message, cause);
    }
}
