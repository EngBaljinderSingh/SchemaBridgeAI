package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
public class DateFormatOperation implements TransformationOperation {

    private static final List<DateTimeFormatter> COMMON_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    @Override
    public TransformationOpType getOpType() {
        return TransformationOpType.DATE_FORMAT;
    }

    @Override
    public Object apply(List<Object> sourceValues, Map<String, Object> parameters) {
        if (sourceValues == null || sourceValues.isEmpty() || sourceValues.get(0) == null) {
            return null;
        }

        String rawDate = sourceValues.get(0).toString().trim();
        if (rawDate.isEmpty()) {
            return null;
        }

        String targetFormat = "yyyy-MM-dd";
        if (parameters != null && parameters.containsKey("targetFormat")) {
            targetFormat = parameters.get("targetFormat").toString();
        }

        DateTimeFormatter targetFormatter = DateTimeFormatter.ofPattern(targetFormat);

        // If explicit sourceFormat is provided, use it
        if (parameters != null && parameters.containsKey("sourceFormat")) {
            String sourceFormat = parameters.get("sourceFormat").toString();
            try {
                DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern(sourceFormat);
                LocalDate parsed = LocalDate.parse(rawDate, sourceFormatter);
                return parsed.format(targetFormatter);
            } catch (DateTimeParseException e) {
                // Try as LocalDateTime if full date-time
                try {
                    DateTimeFormatter sourceFormatter = DateTimeFormatter.ofPattern(sourceFormat);
                    LocalDateTime parsedTime = LocalDateTime.parse(rawDate, sourceFormatter);
                    return parsedTime.format(targetFormatter);
                } catch (Exception ex) {
                    // Fallthrough to fallback heuristic
                }
            }
        }

        // Auto-detect from common formats
        for (DateTimeFormatter formatter : COMMON_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(rawDate, formatter);
                return date.format(targetFormatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                LocalDateTime dateTime = LocalDateTime.parse(rawDate, formatter);
                return dateTime.format(targetFormatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Return original if no parser matched
        return rawDate;
    }
}
