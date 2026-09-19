package com.schemabridge.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.schemabridge.domain.SchemaDefinition;
import com.schemabridge.domain.SchemaField;
import com.schemabridge.dto.ValidationResultDto;
import com.schemabridge.util.JsonPathTreeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public SchemaValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Validates a target payload against the target SchemaDefinition.
     */
    public ValidationResultDto validate(SchemaDefinition schemaDef, Object payload) {
        if (schemaDef == null) {
            return ValidationResultDto.builder().valid(true).build();
        }
        return validate(schemaDef.getOriginalSchema(), schemaDef.getFields(), payload);
    }

    /**
     * Validates a target payload against original schema string and/or extracted fields.
     */
    public ValidationResultDto validate(String schemaContent, List<SchemaField> fields, Object payload) {
        JsonNode payloadNode = convertToJsonNode(payload);
        List<ValidationResultDto.ValidationErrorItem> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Try standard JSON Schema validation if schema contains "$schema" or "properties"
        if (schemaContent != null && (schemaContent.contains("\"$schema\"") || schemaContent.contains("\"properties\""))) {
            try {
                JsonNode schemaNode = objectMapper.readTree(schemaContent);
                JsonSchema jsonSchema = schemaFactory.getSchema(schemaNode);
                Set<ValidationMessage> validationMessages = jsonSchema.validate(payloadNode);

                for (ValidationMessage msg : validationMessages) {
                    String path = msg.getInstanceLocation() != null ? msg.getInstanceLocation().toString() : "";
                    errors.add(ValidationResultDto.ValidationErrorItem.builder()
                            .field(path)
                            .message(msg.getMessage())
                            .rule(msg.getType())
                            .build());
                }
            } catch (Exception e) {
                warnings.add("Could not run standard JSON Schema validator: " + e.getMessage());
            }
        }

        // 2. Validate against explicit SchemaField list or simplified schema definition
        if (fields != null && !fields.isEmpty()) {
            validateAgainstFields(payloadNode, fields, errors, warnings);
        } else if (schemaContent != null) {
            validateSimplifiedSchema(payloadNode, schemaContent, errors, warnings);
        }

        boolean isValid = errors.isEmpty();
        return ValidationResultDto.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private void validateAgainstFields(JsonNode payload, List<SchemaField> fields,
                                       List<ValidationResultDto.ValidationErrorItem> errors,
                                       List<String> warnings) {
        for (SchemaField field : fields) {
            Object val = JsonPathTreeUtils.extractValue(payload, field.getFieldPath());

            if (field.isRequired() && (val == null || val.toString().isBlank())) {
                errors.add(ValidationResultDto.ValidationErrorItem.builder()
                        .field(field.getFieldPath())
                        .message("Required target field '" + field.getFieldPath() + "' is missing or empty.")
                        .rule("REQUIRED")
                        .build());
                continue;
            }

            if (val != null) {
                validateFieldTypeAndFormat(field.getFieldPath(), field.getDataType(), field.getFormat(), val, errors);
            }
        }
    }

    private void validateSimplifiedSchema(JsonNode payload, String schemaContent,
                                         List<ValidationResultDto.ValidationErrorItem> errors,
                                         List<String> warnings) {
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            if (schemaNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = schemaNode.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    String fieldPath = entry.getKey();
                    JsonNode spec = entry.getValue();

                    String expectedType = spec.isTextual() ? spec.asText() : "string";
                    Object actualValue = JsonPathTreeUtils.extractValue(payload, fieldPath);

                    if (actualValue == null) {
                        warnings.add("Field '" + fieldPath + "' is specified in target schema but null in output.");
                    } else {
                        validateFieldTypeAndFormat(fieldPath, expectedType, expectedType, actualValue, errors);
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("Simplified schema check skipped: " + e.getMessage());
        }
    }

    private void validateFieldTypeAndFormat(String path, String expectedType, String format, Object val,
                                           List<ValidationResultDto.ValidationErrorItem> errors) {
        String typeLower = expectedType != null ? expectedType.toLowerCase() : "string";
        String valStr = val.toString();

        if (typeLower.contains("yyyy") || (format != null && format.contains("yyyy"))) {
            // Date format check
            String fmt = (format != null && format.contains("yyyy")) ? format : "yyyy-MM-dd";
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmt);
                LocalDate.parse(valStr, formatter);
            } catch (DateTimeParseException e) {
                errors.add(ValidationResultDto.ValidationErrorItem.builder()
                        .field(path)
                        .message("Expected format " + fmt + " but got '" + valStr + "'.")
                        .rule("DATE_FORMAT")
                        .build());
            }
        } else if (typeLower.equals("boolean")) {
            if (!(val instanceof Boolean) && !valStr.equalsIgnoreCase("true") && !valStr.equalsIgnoreCase("false")) {
                errors.add(ValidationResultDto.ValidationErrorItem.builder()
                        .field(path)
                        .message("Expected boolean value for field '" + path + "' but got: " + valStr)
                        .rule("TYPE_MISMATCH")
                        .build());
            }
        } else if (typeLower.equals("number") || typeLower.equals("integer")) {
            if (!(val instanceof Number)) {
                try {
                    Double.parseDouble(valStr);
                } catch (NumberFormatException e) {
                    errors.add(ValidationResultDto.ValidationErrorItem.builder()
                            .field(path)
                            .message("Expected number for field '" + path + "' but got: " + valStr)
                            .rule("TYPE_MISMATCH")
                            .build());
                }
            }
        }

        // Email format check
        if (path.toLowerCase().contains("email") || (format != null && format.equalsIgnoreCase("email"))) {
            if (!EMAIL_PATTERN.matcher(valStr).matches()) {
                errors.add(ValidationResultDto.ValidationErrorItem.builder()
                        .field(path)
                        .message("Invalid email address format for field '" + path + "': " + valStr)
                        .rule("EMAIL_FORMAT")
                        .build());
            }
        }
    }

    private JsonNode convertToJsonNode(Object payload) {
        if (payload instanceof JsonNode jn) {
            return jn;
        }
        if (payload instanceof String str) {
            try {
                return objectMapper.readTree(str);
            } catch (Exception e) {
                return objectMapper.getNodeFactory().textNode(str);
            }
        }
        return objectMapper.valueToTree(payload);
    }
}
