package com.schemabridge.service.matching;

import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class NormalisedMatchStrategy implements MatchStrategy {

    private static final List<String> TECHNICAL_PREFIXES = List.of(
            "tbl_", "col_", "txt_", "str_", "fld_", "dt_", "num_", "bool_", "in_", "out_"
    );

    @Override
    public MatchMethod getMethod() {
        return MatchMethod.NORMALISED;
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public List<MappingRuleDto> match(
            List<FieldExtractionDto> availableSources,
            List<FieldExtractionDto> availableTargets,
            Set<String> alreadyMappedTargetPaths,
            String projectId) {

        List<MappingRuleDto> matches = new ArrayList<>();

        for (FieldExtractionDto source : availableSources) {
            String normSource = normalise(source.getFieldName());

            for (FieldExtractionDto target : availableTargets) {
                if (alreadyMappedTargetPaths.contains(target.getFieldPath())) {
                    continue;
                }

                String normTarget = normalise(target.getFieldName());

                if (normSource.equals(normTarget)) {
                    TransformationOpType op = determineOperation(source, target);
                    matches.add(MappingRuleDto.builder()
                            .sourcePaths(List.of(source.getFieldPath()))
                            .targetPath(target.getFieldPath())
                            .operation(op)
                            .parameters(new HashMap<>())
                            .matchMethod(MatchMethod.NORMALISED)
                            .confidence(BigDecimal.valueOf(0.950))
                            .confidenceLevel(ConfidenceLevel.HIGH)
                            .status(MappingStatus.SUGGESTED)
                            .explanation("Normalised name match: '" + source.getFieldName() + "' -> '" + target.getFieldName() + "'")
                            .requiresReview(false)
                            .build());

                    alreadyMappedTargetPaths.add(target.getFieldPath());
                    break;
                }
            }
        }

        return matches;
    }

    public static String normalise(String fieldName) {
        if (fieldName == null) return "";
        String s = fieldName.trim().toLowerCase();

        for (String prefix : TECHNICAL_PREFIXES) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
            }
        }

        // Remove underscores, hyphens, spaces, and dots
        return s.replaceAll("[_\\-\\s.]", "");
    }

    private TransformationOpType determineOperation(FieldExtractionDto source, FieldExtractionDto target) {
        String srcType = source.getDataType() != null ? source.getDataType().toLowerCase() : "string";
        String tgtType = target.getDataType() != null ? target.getDataType().toLowerCase() : "string";

        if ((srcType.equals("integer") || srcType.equals("number")) && tgtType.equals("string")) {
            return TransformationOpType.NUMBER_TO_STRING;
        }
        if (srcType.equals("string") && (tgtType.equals("integer") || tgtType.equals("number"))) {
            return TransformationOpType.STRING_TO_NUMBER;
        }
        if (srcType.equals("string") && tgtType.equals("boolean")) {
            return TransformationOpType.STRING_TO_BOOLEAN;
        }
        return TransformationOpType.RENAME;
    }
}
