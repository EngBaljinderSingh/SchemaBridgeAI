package com.schemabridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.SchemaDefinition;
import com.schemabridge.domain.SchemaField;
import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.FieldExtractionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class SchemaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractionService.class);
    private final ObjectMapper objectMapper;

    public SchemaExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            JsonNode root = objectMapper.readTree(schemaContent);
            if (schemaType == SchemaType.JSON_SCHEMA && root.has("properties")) {
                extractFromJsonSchema(root, "", fields, new HashSet<>());
            } else if (schemaType == SchemaType.OPENAPI && root.has("components")) {
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
            boolean isRequired = requiredFields.contains(fieldName);

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
        if (schemas.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = schemas.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                extractFromJsonSchema(entry.getValue(), entry.getKey(), fields, new HashSet<>());
            }
        }
    }
}
