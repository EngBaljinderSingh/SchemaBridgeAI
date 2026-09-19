package com.schemabridge.service.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schemabridge.domain.SynonymEntry;
import com.schemabridge.domain.enums.MatchMethod;
import com.schemabridge.domain.enums.TransformationOpType;
import com.schemabridge.dto.FieldExtractionDto;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.repository.MappingRuleRepository;
import com.schemabridge.repository.SynonymEntryRepository;
import com.schemabridge.service.aviator.AviatorMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MatchingStrategiesTest {

    private ExactMatchStrategy exactMatchStrategy;
    private NormalisedMatchStrategy normalisedMatchStrategy;
    private SynonymMatchStrategy synonymMatchStrategy;
    private SynonymEntryRepository synonymRepository;

    @BeforeEach
    void setUp() {
        exactMatchStrategy = new ExactMatchStrategy();
        normalisedMatchStrategy = new NormalisedMatchStrategy();
        synonymRepository = Mockito.mock(SynonymEntryRepository.class);

        when(synonymRepository.findByEnabledTrue()).thenReturn(List.of(
                SynonymEntry.builder().canonicalTerm("name").synonym("userName").enabled(true).build(),
                SynonymEntry.builder().canonicalTerm("dob").synonym("dateOfBirth").enabled(true).build(),
                SynonymEntry.builder().canonicalTerm("active").synonym("accountEnabled").enabled(true).build(),
                SynonymEntry.builder().canonicalTerm("email").synonym("emailAddress").enabled(true).build()
        ));

        synonymMatchStrategy = new SynonymMatchStrategy(synonymRepository);
    }

    @Test
    @DisplayName("Exact Matching: Matches identical field names")
    void testExactMatching() {
        FieldExtractionDto src = FieldExtractionDto.builder().fieldPath("projectId").fieldName("projectId").dataType("string").build();
        FieldExtractionDto tgt = FieldExtractionDto.builder().fieldPath("projectId").fieldName("projectId").dataType("string").build();

        Set<String> mappedTargets = new HashSet<>();
        List<MappingRuleDto> results = exactMatchStrategy.match(List.of(src), List.of(tgt), mappedTargets, "proj-1");

        assertEquals(1, results.size());
        assertEquals("projectId", results.get(0).getTargetPath());
        assertEquals(MatchMethod.EXACT, results.get(0).getMatchMethod());
        assertEquals(1.000, results.get(0).getConfidence().doubleValue(), 0.001);
    }

    @Test
    @DisplayName("Normalised Matching: Matches snake_case, PascalCase, and technical affixes")
    void testNormalisedMatching() {
        FieldExtractionDto src1 = FieldExtractionDto.builder().fieldPath("project_id").fieldName("project_id").dataType("integer").build();
        FieldExtractionDto tgt1 = FieldExtractionDto.builder().fieldPath("projectId").fieldName("projectId").dataType("string").build();

        Set<String> mappedTargets = new HashSet<>();
        List<MappingRuleDto> results = normalisedMatchStrategy.match(List.of(src1), List.of(tgt1), mappedTargets, "proj-1");

        assertEquals(1, results.size());
        assertEquals("project_id", results.get(0).getSourcePaths().get(0));
        assertEquals("projectId", results.get(0).getTargetPath());
        assertEquals(MatchMethod.NORMALISED, results.get(0).getMatchMethod());
        assertEquals(TransformationOpType.NUMBER_TO_STRING, results.get(0).getOperation());
    }

    @Test
    @DisplayName("Synonym Matching: Matches dob ~ dateOfBirth and active ~ accountEnabled")
    void testSynonymMatching() {
        FieldExtractionDto srcDob = FieldExtractionDto.builder().fieldPath("dob").fieldName("dob").dataType("string").sampleValue("10/05/1992").build();
        FieldExtractionDto tgtDob = FieldExtractionDto.builder().fieldPath("dateOfBirth").fieldName("dateOfBirth").dataType("date").format("yyyy-MM-dd").build();

        FieldExtractionDto srcActive = FieldExtractionDto.builder().fieldPath("active").fieldName("active").dataType("string").sampleValue("Yes").build();
        FieldExtractionDto tgtActive = FieldExtractionDto.builder().fieldPath("accountEnabled").fieldName("accountEnabled").dataType("boolean").build();

        Set<String> mappedTargets = new HashSet<>();
        List<MappingRuleDto> results = synonymMatchStrategy.match(
                List.of(srcDob, srcActive), List.of(tgtDob, tgtActive), mappedTargets, "proj-1");

        assertEquals(2, results.size());

        MappingRuleDto dobRule = results.stream().filter(r -> r.getTargetPath().equals("dateOfBirth")).findFirst().orElseThrow();
        assertEquals(TransformationOpType.DATE_FORMAT, dobRule.getOperation());
        assertEquals("yyyy-MM-dd", dobRule.getParameters().get("targetFormat"));

        MappingRuleDto activeRule = results.stream().filter(r -> r.getTargetPath().equals("accountEnabled")).findFirst().orElseThrow();
        assertEquals(TransformationOpType.STRING_TO_BOOLEAN, activeRule.getOperation());
    }

    @Test
    @DisplayName("Matching Pipeline: Falls back gracefully when Aviator throws an exception")
    void testMatchingPipelineFallback() {
        AviatorMappingService aviatorService = Mockito.mock(AviatorMappingService.class);
        when(aviatorService.suggestMappings(Mockito.anyList(), Mockito.anyList(), Mockito.any()))
                .thenThrow(new RuntimeException("Aviator connection refused"));

        MatchingPipeline pipeline = new MatchingPipeline(
                List.of(exactMatchStrategy, normalisedMatchStrategy, synonymMatchStrategy),
                aviatorService,
                0.90,
                0.70
        );

        FieldExtractionDto src = FieldExtractionDto.builder().fieldPath("name").fieldName("name").dataType("string").build();
        FieldExtractionDto tgt = FieldExtractionDto.builder().fieldPath("userName").fieldName("userName").dataType("string").build();

        FieldExtractionDto srcUnmapped = FieldExtractionDto.builder().fieldPath("custom_code_xyz").fieldName("custom_code_xyz").dataType("string").build();
        FieldExtractionDto tgtUnmapped = FieldExtractionDto.builder().fieldPath("internal_ref_abc").fieldName("internal_ref_abc").dataType("string").build();

        MatchingPipeline.PipelineResult res = pipeline.execute(
                List.of(src, srcUnmapped), List.of(tgt, tgtUnmapped), "proj-1", "test");

        // Deterministic synonym match (Level 3) should succeed
        assertFalse(res.rules().isEmpty());
        assertEquals("userName", res.rules().get(0).getTargetPath());
        // Unmapped field triggered Aviator, which failed and logged a warning without failing pipeline
        assertFalse(res.warnings().isEmpty());
        assertTrue(res.unmappedSourceFields().contains("custom_code_xyz"));
    }
}
