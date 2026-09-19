package com.schemabridge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;

public class JsonPathTreeUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extracts a value from a JSON root node given a dot-separated path (e.g. "contact.email" or "items[0].id").
     */
    public static Object extractValue(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }

        String[] tokens = path.split("\\.");
        JsonNode current = root;

        for (String token : tokens) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array index e.g. items[0]
            if (token.contains("[") && token.endsWith("]")) {
                int openBracket = token.indexOf('[');
                String fieldName = token.substring(0, openBracket);
                int index = Integer.parseInt(token.substring(openBracket + 1, token.length() - 1));

                if (!fieldName.isEmpty()) {
                    current = current.path(fieldName);
                }
                if (current.isArray() && index >= 0 && index < current.size()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.path(token);
            }
        }

        return convertJsonNodeToObject(current);
    }

    /**
     * Sets a value in a target JSON object node given a dot-separated path (e.g. "profile.userName").
     */
    public static void setValue(ObjectNode root, String path, Object value) {
        if (root == null || path == null || path.isBlank()) {
            return;
        }

        String[] tokens = path.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < tokens.length - 1; i++) {
            String token = tokens[i];
            JsonNode next = current.get(token);
            if (next == null || !next.isObject()) {
                ObjectNode newNode = current.putObject(token);
                current = newNode;
            } else {
                current = (ObjectNode) next;
            }
        }

        String leafToken = tokens[tokens.length - 1];
        JsonNode valueNode = MAPPER.valueToTree(value);
        current.set(leafToken, valueNode);
    }

    public static Object convertJsonNodeToObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(convertJsonNodeToObject(item));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                map.put(entry.getKey(), convertJsonNodeToObject(entry.getValue()));
            }
            return map;
        }
        return node.asText();
    }
}
