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
public class ExactMatchStrategy implements MatchStrategy {

    @Override
    public MatchMethod getMethod() {
        return MatchMethod.EXACT;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public List<MappingRuleDto> match(
            List<FieldExtractionDto> availableSources,
            List<FieldExtractionDto> availableTargets,
            Set<String> alreadyMappedTargetPaths,
            String projectId) {

        List<MappingRuleDto> matches = new ArrayList<>();

        for (FieldExtractionDto source : availableSources) {
            for (FieldExtractionDto target : availableTargets) {
                if (alreadyMappedTargetPaths.contains(target.getFieldPath())) {
                    continue;
                }

                // Check exact fieldName or fieldPath equality
                boolean isExact = source.getFieldName().equals(target.getFieldName()) ||
                                  source.getFieldPath().equals(target.getFieldPath());

                if (isExact) {
                    TransformationOpType op = determineOperation(source, target);
                    matches.add(MappingRuleDto.builder()
                            .sourcePaths(List.of(source.getFieldPath()))
                            .targetPath(target.getFieldPath())
                            .operation(op)
                            .parameters(new HashMap<>())
                            .matchMethod(MatchMethod.EXACT)
                            .confidence(BigDecimal.valueOf(1.000))
                            .confidenceLevel(ConfidenceLevel.HIGH)
                            .status(MappingStatus.SUGGESTED)
                            .explanation("Exact field name match: '" + source.getFieldName() + "'")
                            .requiresReview(false)
                            .build());

                    alreadyMappedTargetPaths.add(target.getFieldPath());
                    break;
                }
            }
        }

        return matches;
    }

    private TransformationOpType determineOperation(FieldExtractionDto source, FieldExtractionDto target) {
        String srcType = source.getDataType() != null ? source.getDataType().toLowerCase() : "string";
        String tgtType = target.getDataType() != null ? target.getDataType().toLowerCase() : "string";

        if (srcType.equals(tgtType)) {
            return TransformationOpType.RENAME;
        }
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
