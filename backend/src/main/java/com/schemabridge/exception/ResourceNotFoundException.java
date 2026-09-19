package com.schemabridge.exception;

public class ResourceNotFoundException extends SchemaBridgeException {
    public ResourceNotFoundException(String resourceName, String id) {
        super("RESOURCE_NOT_FOUND", resourceName + " not found with ID: " + id);
    }
}
