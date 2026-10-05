package com.schemabridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.schemabridge.domain.SchemaDefinition;
import com.schemabridge.domain.SchemaField;
import com.schemabridge.domain.enums.Direction;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.ApiEndpointSummaryDto;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.SchemaImportRequest;
import com.schemabridge.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class SchemaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractionService.class);
    private final ObjectMapper objectMapper;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public SchemaExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a SchemaImportRequest from an uploaded Swagger/OpenAPI (or JSON Schema) file.
     * Accepts both JSON and YAML content and auto-detects the schema type.
     */
    public SchemaImportRequest buildImportRequestFromFile(MultipartFile file, Direction direction, String systemName) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded schema file is empty");
        }
        String rawContent;
        try {
            rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException("Unable to read uploaded schema file: " + e.getMessage());
        }

        JsonNode root = parseToJsonNode(rawContent, file.getOriginalFilename());
        String normalizedJson = root.toString();
        SchemaType detectedType = detectSchemaType(root);

        String baseName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("\\.(json|ya?ml)$", "")
                : (direction == Direction.SOURCE_TO_TARGET ? "SourceSchema" : "TargetSchema");

        return SchemaImportRequest.builder()
                .direction(direction)
                .systemName(systemName)
                .schemaType(detectedType)
                .schemaContent(normalizedJson)
                .schemaName(baseName)
                .build();
    }

    /**
     * Parses raw file content as JSON, falling back to YAML (common for Swagger docs).
     */
    private JsonNode parseToJsonNode(String rawContent, String fileName) {
        try {
            return objectMapper.readTree(rawContent);
        } catch (Exception jsonEx) {
            try {
                return yamlMapper.readTree(rawContent);
            } catch (Exception yamlEx) {
                throw new ValidationException("Uploaded file '" + fileName + "' is neither valid JSON nor valid YAML");
            }
        }
    }

    /**
     * Detects whether the parsed document is an OpenAPI/Swagger spec, a JSON Schema, or a plain sample payload.
     */
    public SchemaType detectSchemaType(JsonNode root) {
        if (root.has("openapi") || root.has("swagger")) {
            return SchemaType.OPENAPI;
        }
        if (root.has("$schema") || (root.has("type") && root.has("properties"))) {
            return SchemaType.JSON_SCHEMA;
        }
        return SchemaType.SAMPLE_JSON;
    }

    public String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(content.getBytes()).toString().replace("-", "");
        }
    }

    public List<FieldExtractionDto> extractFields(String schemaContent, SchemaType schemaType) {
        List<FieldExtractionDto> fields = new ArrayList<>();
        try {
            JsonNode root = parseToJsonNode(schemaContent, "schemaContent");
            if (schemaType == SchemaType.JSON_SCHEMA && root.has("properties")) {
                extractFromJsonSchema(root, "", fields, new HashSet<>());
            } else if (schemaType == SchemaType.OPENAPI && (root.has("components") || root.has("paths") || root.has("definitions") || root.has("swagger") || root.has("openapi"))) {
                extractFromOpenApi(root, fields);
            } else {
                // Treat as Sample JSON or Key-Value Schema
                extractFromSampleJson(root, "", fields);
            }
        } catch (Exception e) {
            log.error("Failed to parse schema for field extraction: {}", e.getMessage(), e);
        }
        return fields;
    }

    public List<SchemaField> convertToEntities(List<FieldExtractionDto> dtos, SchemaDefinition schemaDefinition) {
        List<SchemaField> entities = new ArrayList<>();
        for (FieldExtractionDto dto : dtos) {
            SchemaField field = SchemaField.builder()
                    .schemaDefinition(schemaDefinition)
                    .fieldPath(dto.getFieldPath())
                    .fieldName(dto.getFieldName())
                    .description(dto.getDescription())
                    .dataType(dto.getDataType())
                    .format(dto.getFormat())
                    .required(dto.isRequired())
                    .array(dto.isArray())
                    .parentPath(dto.getParentPath())
                    .build();
            entities.add(field);
        }
        return entities;
    }

    private void extractFromJsonSchema(JsonNode node, String parentPath, List<FieldExtractionDto> fields, Set<String> requiredFields) {
        if (node.has("required") && node.get("required").isArray()) {
            for (JsonNode req : node.get("required")) {
                requiredFields.add(req.asText());
            }
        }

        JsonNode properties = node.path("properties");
        Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String fieldName = entry.getKey();
            JsonNode prop = entry.getValue();
            String fullPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

            String type = prop.path("type").asText("string");
            String format = prop.has("format") ? prop.get("format").asText() : null;
            String desc = prop.has("description") ? prop.get("description").asText() : null;
            if (desc == null && prop.has("title")) {
                desc = prop.get("title").asText();
            }
            boolean isRequired = requiredFields.contains(fieldName);

            // Extract example / default / examples for semantic context and profiling
            Object sampleValue = null;
            if (prop.has("example")) {
                JsonNode exNode = prop.get("example");
                sampleValue = exNode.isValueNode() ? exNode.asText() : exNode.toString();
            } else if (prop.has("default")) {
                JsonNode defNode = prop.get("default");
                sampleValue = defNode.isValueNode() ? defNode.asText() : defNode.toString();
            } else if (prop.has("examples")) {
                JsonNode exs = prop.get("examples");
                if (exs.isArray() && !exs.isEmpty()) {
                    sampleValue = exs.get(0).isValueNode() ? exs.get(0).asText() : exs.get(0).toString();
                } else if (exs.isObject() && exs.fields().hasNext()) {
                    JsonNode firstEx = exs.fields().next().getValue();
                    if (firstEx.has("value")) {
                        JsonNode valNode = firstEx.get("value");
                        sampleValue = valNode.isValueNode() ? valNode.asText() : valNode.toString();
                    }
                }
            }

            List<String> enums = new ArrayList<>();
            if (prop.has("enum") && prop.get("enum").isArray()) {
                for (JsonNode e : prop.get("enum")) {
                    enums.add(e.asText());
                }
            }

            if ("object".equalsIgnoreCase(type) && prop.has("properties")) {
                Set<String> subRequired = new HashSet<>();
                extractFromJsonSchema(prop, fullPath, fields, subRequired);
            } else if ("array".equalsIgnoreCase(type) && prop.has("items")) {
                JsonNode items = prop.get("items");
                fields.add(FieldExtractionDto.builder()
                        .fieldPath(fullPath)
                        .fieldName(fieldName)
                        .description(desc)
                        .dataType("array")
                        .format(format)
                        .required(isRequired)
                        .array(true)
                        .parentPath(parentPath.isEmpty() ? null : parentPath)
                        .sampleValue(sampleValue)
                        .enumValues(enums)
                        .build());
                if (items.has("properties")) {
                    extractFromJsonSchema(items, fullPath + "[]", fields, new HashSet<>());
                }
            } else {
                fields.add(FieldExtractionDto.builder()
                        .fieldPath(fullPath)
                        .fieldName(fieldName)
                        .description(desc)
                        .dataType(type)
                        .format(format)
                        .required(isRequired)
                        .array(false)
                        .parentPath(parentPath.isEmpty() ? null : parentPath)
                        .sampleValue(sampleValue)
                        .enumValues(enums)
                        .build());
            }
        }
    }

    private void extractFromSampleJson(JsonNode node, String parentPath, List<FieldExtractionDto> fields) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();
                String fullPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

                if (child.isObject()) {
                    extractFromSampleJson(child, fullPath, fields);
                } else if (child.isArray()) {
                    fields.add(FieldExtractionDto.builder()
                            .fieldPath(fullPath)
                            .fieldName(fieldName)
                            .dataType("array")
                            .required(true)
                            .array(true)
                            .parentPath(parentPath.isEmpty() ? null : parentPath)
                            .build());
                    if (!child.isEmpty() && child.get(0).isObject()) {
                        extractFromSampleJson(child.get(0), fullPath + "[]", fields);
                    }
                } else {
                    String dataType = "string";
                    String format = null;
                    if (child.isBoolean()) {
                        dataType = "boolean";
                    } else if (child.isNumber()) {
                        dataType = child.isIntegralNumber() ? "integer" : "number";
                    } else if (child.isTextual()) {
                        String text = child.asText();
                        if (text.contains("/") || (text.contains("-") && text.length() >= 8 && Character.isDigit(text.charAt(0)))) {
                            format = "date";
                        }
                    }

                    // Check if value is a type declaration like "string", "yyyy-MM-dd", "boolean"
                    if (child.isTextual()) {
                        String textVal = child.asText().trim();
                        if (textVal.equalsIgnoreCase("string") || textVal.equalsIgnoreCase("boolean") ||
                            textVal.equalsIgnoreCase("number") || textVal.equalsIgnoreCase("integer")) {
                            dataType = textVal.toLowerCase();
                        } else if (textVal.contains("yyyy") || textVal.contains("MM") || textVal.contains("dd")) {
                            dataType = "date";
                            format = textVal;
                        }
                    }

                    fields.add(FieldExtractionDto.builder()
                            .fieldPath(fullPath)
                            .fieldName(fieldName)
                            .dataType(dataType)
                            .format(format)
                            .required(true)
                            .array(false)
                            .parentPath(parentPath.isEmpty() ? null : parentPath)
                            .sampleValue(child.asText())
                            .build());
                }
            }
        }
    }

    private void extractFromOpenApi(JsonNode root, List<FieldExtractionDto> fields) {
        JsonNode schemas = root.path("components").path("schemas");
        if (!schemas.isObject() || schemas.isEmpty()) {
            schemas = root.path("definitions");
        }
        if (schemas.isObject() && schemas.size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> it = schemas.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                String prefix = schemas.size() == 1 ? "" : entry.getKey();
                extractFromJsonSchema(entry.getValue(), prefix, fields, new HashSet<>());
            }
            return;
        }

        // Fallback: some Swagger docs inline schemas directly on path request/response bodies
        // instead of (or in addition to) components.schemas.
        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            Set<String> seenPaths = new HashSet<>();
            for (Iterator<JsonNode> pathIt = paths.elements(); pathIt.hasNext(); ) {
                JsonNode pathItem = pathIt.next();
                for (Iterator<JsonNode> opIt = pathItem.elements(); opIt.hasNext(); ) {
                    JsonNode operation = opIt.next();
                    extractInlineOpenApiSchema(root, operation.path("requestBody"), fields, seenPaths);
                    JsonNode responses = operation.path("responses");
                    for (Iterator<JsonNode> respIt = responses.elements(); respIt.hasNext(); ) {
                        extractInlineOpenApiSchema(root, respIt.next(), fields, seenPaths);
                    }
                }
            }
        }
    }

    private void extractInlineOpenApiSchema(JsonNode root, JsonNode container, List<FieldExtractionDto> fields, Set<String> seenPaths) {
        JsonNode schema = container.path("content").path("application/json").path("schema");
        if (schema.isMissingNode() || schema.isNull()) {
            schema = container.path("schema");
        }
        if (schema.isMissingNode() || schema.isNull()) {
            return;
        }

        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asText();
            JsonNode resolved = resolveRef(root, ref);
            if (resolved != null && !resolved.isMissingNode()) {
                schema = resolved;
            }
        }

        // Use object identity of the schema's field names as a lightweight de-dup key
        String key = schema.toString();
        if (!seenPaths.add(key)) {
            return;
        }
        extractFromJsonSchema(schema, "", fields, new HashSet<>());
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        if (ref == null || !ref.startsWith("#/")) {
            return null;
        }
        String[] parts = ref.substring(2).split("/");
        JsonNode current = root;
        for (String part : parts) {
            current = current.path(part);
            if (current.isMissingNode()) return null;
        }
        return current;
    }

    /**
     * Extracts a list of all discoverable API endpoints from an OpenAPI/Swagger spec.
     */
    public List<ApiEndpointSummaryDto> extractEndpointsFromSpec(String schemaContent) {
        List<ApiEndpointSummaryDto> endpoints = new ArrayList<>();
        try {
            JsonNode root = parseToJsonNode(schemaContent, "spec");
            JsonNode paths = root.path("paths");
            if (paths.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> pathEntry = it.next();
                    String pathKey = pathEntry.getKey();
                    JsonNode pathItem = pathEntry.getValue();

                    Iterator<Map.Entry<String, JsonNode>> opIt = pathItem.fields();
                    while (opIt.hasNext()) {
                        Map.Entry<String, JsonNode> opEntry = opIt.next();
                        String method = opEntry.getKey().toUpperCase();
                        if (!List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                            continue;
                        }
                        JsonNode op = opEntry.getValue();
                        String summary = op.path("summary").asText(op.path("description").asText(pathKey));
                        String opId = op.path("operationId").asText("");
                        String desc = op.path("description").asText("");

                        // Discover model name
                        String modelName = "";
                        JsonNode reqSchema = op.path("requestBody").path("content").path("application/json").path("schema");
                        if (reqSchema.has("$ref")) {
                            String ref = reqSchema.get("$ref").asText();
                            modelName = ref.substring(ref.lastIndexOf('/') + 1);
                        } else {
                            JsonNode respSchema = op.path("responses").path("200").path("content").path("application/json").path("schema");
                            if (!respSchema.has("$ref")) {
                                respSchema = op.path("responses").path("201").path("content").path("application/json").path("schema");
                            }
                            if (respSchema.has("$ref")) {
                                String ref = respSchema.get("$ref").asText();
                                modelName = ref.substring(ref.lastIndexOf('/') + 1);
                            }
                        }

                        endpoints.add(ApiEndpointSummaryDto.builder()
                                .httpMethod(method)
                                .endpointPath(pathKey)
                                .summary(summary)
                                .operationId(opId)
                                .schemaModelName(modelName.isEmpty() ? "InlinePayload" : modelName)
                                .description(desc)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract endpoints from spec: {}", e.getMessage());
        }
        return endpoints;
    }

    /**
     * Extracts field definitions filtered strictly by the selected API endpoint paths.
     */
    public List<FieldExtractionDto> extractFieldsForEndpoints(String schemaContent, List<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.isEmpty() || selectedPaths.contains("*")) {
            return extractFields(schemaContent, SchemaType.OPENAPI);
        }

        List<FieldExtractionDto> fields = new ArrayList<>();
        Set<String> selectedSet = new HashSet<>(selectedPaths);
        try {
            JsonNode root = parseToJsonNode(schemaContent, "spec");
            JsonNode paths = root.path("paths");
            if (paths.isObject()) {
                Set<String> seenKeys = new HashSet<>();
                Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> pathEntry = it.next();
                    String pathKey = pathEntry.getKey();
                    if (!selectedSet.contains(pathKey)) {
                        continue;
                    }
                    JsonNode pathItem = pathEntry.getValue();
                    for (Iterator<JsonNode> opIt = pathItem.elements(); opIt.hasNext(); ) {
                        JsonNode operation = opIt.next();
                        extractInlineOpenApiSchema(root, operation.path("requestBody"), fields, seenKeys);
                        extractInlineOpenApiSchema(root, operation.path("responses").path("200"), fields, seenKeys);
                        extractInlineOpenApiSchema(root, operation.path("responses").path("201"), fields, seenKeys);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed extracting fields for selected endpoints: {}", e.getMessage(), e);
        }

        if (fields.isEmpty()) {
            return extractFields(schemaContent, SchemaType.OPENAPI);
        }
        return fields;
    }

    /**
     * Builds a SchemaImportRequest from a live traced HTTP payload / sample JSON.
     */
    public SchemaImportRequest buildImportRequestFromTrace(String rawPayload, Direction direction, String systemName, String endpointPath) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new ValidationException("Payload is empty");
        }
        JsonNode root = parseToJsonNode(rawPayload, "tracedPayload");
        String normalizedJson = root.toString();
        String sys = (systemName != null && !systemName.isBlank()) ? systemName.trim() : (direction == Direction.SOURCE_TO_TARGET ? "LegacySource" : "TargetSystem");
        String ep = (endpointPath != null && !endpointPath.isBlank()) ? endpointPath.replaceAll("[^a-zA-Z0-9]", "_") : "TracedPayload";

        return SchemaImportRequest.builder()
                .direction(direction)
                .systemName(sys)
                .schemaType(SchemaType.SAMPLE_JSON)
                .schemaContent(normalizedJson)
                .schemaName(sys + "_" + ep)
                .build();
    }
}
