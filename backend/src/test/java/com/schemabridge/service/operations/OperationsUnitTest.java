package com.schemabridge.service.operations;

import com.schemabridge.domain.enums.TransformationOpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationsUnitTest {

    private TypeConversionOperations.StringToBooleanOperation stringToBooleanOp;
    private TypeConversionOperations.BooleanToStringOperation booleanToStringOp;
    private TypeConversionOperations.NumberToStringOperation numberToStringOp;
    private TypeConversionOperations.StringToNumberOperation stringToNumberOp;
    private DateFormatOperation dateFormatOp;
    private EnumMapOperation enumMapOp;
    private StandardStringOperations.ConcatOperation concatOp;
    private StandardStringOperations.SplitOperation splitOp;
    private StructuralAndConditionalOperations.DefaultValueOperation defaultValueOp;
    private StructuralAndConditionalOperations.ConditionalOperation conditionalOp;
    private StructuralAndConditionalOperations.NestOperation nestOp;

    @BeforeEach
    void setUp() {
        stringToBooleanOp = new TypeConversionOperations.StringToBooleanOperation();
        booleanToStringOp = new TypeConversionOperations.BooleanToStringOperation();
        numberToStringOp = new TypeConversionOperations.NumberToStringOperation();
        stringToNumberOp = new TypeConversionOperations.StringToNumberOperation();
        dateFormatOp = new DateFormatOperation();
        enumMapOp = new EnumMapOperation();
        concatOp = new StandardStringOperations.ConcatOperation();
        splitOp = new StandardStringOperations.SplitOperation();
        defaultValueOp = new StructuralAndConditionalOperations.DefaultValueOperation();
        conditionalOp = new StructuralAndConditionalOperations.ConditionalOperation();
        nestOp = new StructuralAndConditionalOperations.NestOperation();
    }

    @Test
    @DisplayName("STRING_TO_BOOLEAN: Should convert 'Yes', 'true', '1' to true, and 'No', 'false', '0' to false")
    void testStringToBoolean() {
        assertEquals(Boolean.TRUE, stringToBooleanOp.apply(List.of("Yes"), Map.of()));
        assertEquals(Boolean.TRUE, stringToBooleanOp.apply(List.of("yes"), Map.of()));
        assertEquals(Boolean.TRUE, stringToBooleanOp.apply(List.of("true"), Map.of()));
        assertEquals(Boolean.TRUE, stringToBooleanOp.apply(List.of("1"), Map.of()));
        assertEquals(Boolean.TRUE, stringToBooleanOp.apply(List.of("ACTIVE"), Map.of()));

        assertEquals(Boolean.FALSE, stringToBooleanOp.apply(List.of("No"), Map.of()));
        assertEquals(Boolean.FALSE, stringToBooleanOp.apply(List.of("no"), Map.of()));
        assertEquals(Boolean.FALSE, stringToBooleanOp.apply(List.of("false"), Map.of()));
        assertEquals(Boolean.FALSE, stringToBooleanOp.apply(List.of("0"), Map.of()));
    }

    @Test
    @DisplayName("NUMBER_TO_STRING: Should convert numeric types to string")
    void testNumberToString() {
        assertEquals("1001", numberToStringOp.apply(List.of(1001), Map.of()));
        assertEquals("45.67", numberToStringOp.apply(List.of(45.67), Map.of()));
    }

    @Test
    @DisplayName("STRING_TO_NUMBER: Should convert numeric string to Long or Double")
    void testStringToNumber() {
        assertEquals(1001L, stringToNumberOp.apply(List.of("1001"), Map.of()));
        assertEquals(45.67, stringToNumberOp.apply(List.of("45.67"), Map.of()));
    }

    @Test
    @DisplayName("DATE_FORMAT: Should convert '10/05/1992' to '1992-05-10'")
    void testDateFormat() {
        Map<String, Object> params = Map.of(
                "sourceFormat", "dd/MM/yyyy",
                "targetFormat", "yyyy-MM-dd"
        );
        Object result = dateFormatOp.apply(List.of("10/05/1992"), params);
        assertEquals("1992-05-10", result);
    }

    @Test
    @DisplayName("ENUM_MAP: Should map country 'India' to 'IN' and fallback to default value")
    void testEnumMap() {
        Map<String, Object> params = Map.of(
                "mapping", Map.of("India", "IN", "USA", "US"),
                "defaultValue", "UNKNOWN"
        );
        assertEquals("IN", enumMapOp.apply(List.of("India"), params));
        assertEquals("IN", enumMapOp.apply(List.of("india"), params)); // Case-insensitive
        assertEquals("US", enumMapOp.apply(List.of("USA"), params));
        assertEquals("UNKNOWN", enumMapOp.apply(List.of("Germany"), params));
    }

    @Test
    @DisplayName("CONCAT: Should concatenate multiple fields with custom separator")
    void testConcat() {
        Map<String, Object> params = Map.of("separator", " ");
        Object result = concatOp.apply(List.of("Baljinder", "Singh"), params);
        assertEquals("Baljinder Singh", result);
    }

    @Test
    @DisplayName("SPLIT: Should split string and extract index")
    void testSplit() {
        Map<String, Object> params = Map.of("separator", ",", "index", 1);
        Object result = splitOp.apply(List.of("Apple,Banana,Cherry"), params);
        assertEquals("Banana", result);
    }

    @Test
    @DisplayName("DEFAULT_VALUE: Should return default value when source is null or empty")
    void testDefaultValue() {
        Map<String, Object> params = Map.of("defaultValue", "N/A");
        assertEquals("N/A", defaultValueOp.apply(List.of(""), params));
        assertEquals("Present", defaultValueOp.apply(List.of("Present"), params));
    }

    @Test
    @DisplayName("CONDITIONAL: Should evaluate condition and return trueValue / falseValue")
    void testConditional() {
        Map<String, Object> params = Map.of(
                "operator", "EQUALS",
                "expectedValue", "Yes",
                "trueValue", true,
                "falseValue", false
        );
        assertEquals(true, conditionalOp.apply(List.of("Yes"), params));
        assertEquals(false, conditionalOp.apply(List.of("No"), params));
    }

    @Test
    @DisplayName("NEST: Should wrap source value into a nested object map")
    void testNest() {
        Map<String, Object> params = Map.of("key", "emailAddress");
        Object result = nestOp.apply(List.of("user@example.com"), params);
        assertTrue(result instanceof Map);
        assertEquals("user@example.com", ((Map<?, ?>) result).get("emailAddress"));
    }
}
