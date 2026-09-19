package com.schemabridge.exception;

import com.schemabridge.dto.ErrorResponse;

import java.util.List;

public class ValidationException extends SchemaBridgeException {
    public ValidationException(String message, List<ErrorResponse.FieldErrorDetail> details) {
        super("TARGET_VALIDATION_FAILED", message, details);
    }

    public ValidationException(String message) {
        super("TARGET_VALIDATION_FAILED", message);
    }
}
