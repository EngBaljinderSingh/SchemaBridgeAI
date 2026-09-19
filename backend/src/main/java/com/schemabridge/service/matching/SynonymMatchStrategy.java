package com.schemabridge.service.matching;

import com.schemabridge.domain.SynonymEntry;
import com.schemabridge.domain.enums.ConfidenceLevel;
import com.schemabridge.domain.enums.MappingStatus;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.repository.SynonymEntryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class SynonymMatchStrategy implements MatchStrategy {

    private final SynonymEntryRepository synonymRepository;

    public SynonymMatchStrategy(SynonymEntryRepository synonymRepository) {
        this.synonymRepository = synonymRepository;
    }

    @Override
    public MatchMethod getMethod() {
        return MatchMethod.SYNONYM;
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public List<MappingRuleDto> match(
            List<FieldExtractionDto> availableSources,
            List<FieldExtractionDto> availableTargets,
            Set<String> alreadyMappedTargetPaths,
            String projectId) {

        List<MappingRuleDto> matches = new ArrayList<>();
        List<SynonymEntry> allSynonyms = synonymRepository.findByEnabledTrue();

        for (FieldExtractionDto source : availableSources) {
            String srcName = source.getFieldName().toLowerCase();
            String srcPath = source.getFieldPath().toLowerCase();

            for (FieldExtractionDto target : availableTargets) {
                if (alreadyMappedTargetPaths.contains(target.getFieldPath())) {
                    continue;
                }

                String tgtName = target.getFieldName().toLowerCase();
                String tgtPath = target.getFieldPath().toLowerCase();

                boolean isSynonymMatch = checkSynonymMatch(srcName, tgtName, allSynonyms) ||
                                        checkSynonymMatch(srcPath, tgtPath, allSynonyms);

                // Check nested leaf matching (e.g. contact.email -> emailAddress)
                if (!isSynonymMatch && srcPath.contains(".")) {
                    String leafSrc = srcPath.substring(srcPath.lastIndexOf('.') + 1);
                    isSynonymMatch = checkSynonymMatch(leafSrc, tgtName, allSynonyms);
                }

                if (isSynonymMatch) {
                    TransformationOpType op = determineOperation(source, target);
                    Map<String, Object> params = new HashMap<>();

                    if (op == TransformationOpType.DATE_FORMAT) {
                        String targetFormat = target.getFormat() != null ? target.getFormat() : "yyyy-MM-dd";
                        params.put("targetFormat", targetFormat);
                        if (source.getSampleValue() != null && source.getSampleValue().toString().contains("/")) {
                            params.put("sourceFormat", "dd/MM/yyyy");
                        }
                    }

                    matches.add(MappingRuleDto.builder()
                            .sourcePaths(List.of(source.getFieldPath()))
                            .targetPath(target.getFieldPath())
                            .operation(op)
                            .parameters(params)
                            .matchMethod(MatchMethod.SYNONYM)
                            .confidence(BigDecimal.valueOf(0.930))
                            .confidenceLevel(ConfidenceLevel.HIGH)
                            .status(MappingStatus.SUGGESTED)
                            .explanation("Synonym dictionary match: '" + source.getFieldName() + "' ~ '" + target.getFieldName() + "'")
                            .requiresReview(false)
                            .build());

                    alreadyMappedTargetPaths.add(target.getFieldPath());
                    break;
                }
            }
        }

        return matches;
    }

    private boolean checkSynonymMatch(String term1, String term2, List<SynonymEntry> synonyms) {
        String t1 = NormalisedMatchStrategy.normalise(term1);
        String t2 = NormalisedMatchStrategy.normalise(term2);

        for (SynonymEntry entry : synonyms) {
            String canon = NormalisedMatchStrategy.normalise(entry.getCanonicalTerm());
            String syn = NormalisedMatchStrategy.normalise(entry.getSynonym());

            if ((t1.equals(canon) && t2.equals(syn)) || (t1.equals(syn) && t2.equals(canon))) {
                return true;
            }
        }
        return false;
    }

    private TransformationOpType determineOperation(FieldExtractionDto source, FieldExtractionDto target) {
        String srcType = source.getDataType() != null ? source.getDataType().toLowerCase() : "string";
        String tgtType = target.getDataType() != null ? target.getDataType().toLowerCase() : "string";
        String tgtFormat = target.getFormat() != null ? target.getFormat().toLowerCase() : "";

        if (tgtFormat.contains("yyyy") || tgtType.contains("date") ||
            (source.getFormat() != null && source.getFormat().contains("date"))) {
            return TransformationOpType.DATE_FORMAT;
        }
        if (tgtType.equals("boolean") && !srcType.equals("boolean")) {
            return TransformationOpType.STRING_TO_BOOLEAN;
        }
        if ((srcType.equals("integer") || srcType.equals("number")) && tgtType.equals("string")) {
            return TransformationOpType.NUMBER_TO_STRING;
        }
        if (srcType.equals("string") && (tgtType.equals("integer") || tgtType.equals("number"))) {
            return TransformationOpType.STRING_TO_NUMBER;
        }
        return TransformationOpType.RENAME;
    }
}
