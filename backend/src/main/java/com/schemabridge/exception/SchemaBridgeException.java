package com.schemabridge.exception;

import com.schemabridge.dto.ErrorResponse;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class SchemaBridgeException extends RuntimeException {
    private final String code;
    private final String correlationId;
    private final List<ErrorResponse.FieldErrorDetail> details;

    public SchemaBridgeException(String code, String message) {
        super(message);
        this.code = code;
        this.correlationId = UUID.randomUUID().toString();
        this.details = new ArrayList<>();
    }

    public SchemaBridgeException(String code, String message, List<ErrorResponse.FieldErrorDetail> details) {
        super(message);
        this.code = code;
        this.correlationId = UUID.randomUUID().toString();
        this.details = details != null ? details : new ArrayList<>();
    }

    public SchemaBridgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.correlationId = UUID.randomUUID().toString();
        this.details = new ArrayList<>();
    }
}
