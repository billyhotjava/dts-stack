package com.yuzhi.dts.platform.service.modeling;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Closed logical-to-physical type contract for generated PostgreSQL model artifacts. */
final class ModelFieldPhysicalTypeContract {

    private static final int MAX_VARCHAR_LENGTH = 10_485_760;
    private static final int MAX_NUMERIC_PRECISION = 1000;
    private static final Pattern VARCHAR = Pattern.compile(
        "^varchar\\(\\s*([0-9]+)\\s*\\)$"
    );
    private static final Pattern NUMERIC = Pattern.compile(
        "^(?:decimal|numeric)\\(\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*\\)$"
    );
    private static final Map<String, String> SIMPLE_TYPES = Map.ofEntries(
        Map.entry("string", "text"),
        Map.entry("varchar", "text"),
        Map.entry("text", "text"),
        Map.entry("int", "integer"),
        Map.entry("integer", "integer"),
        Map.entry("bigint", "bigint"),
        Map.entry("decimal", "numeric"),
        Map.entry("numeric", "numeric"),
        Map.entry("date", "date"),
        Map.entry("timestamp", "timestamp without time zone"),
        Map.entry("datetime", "timestamp without time zone"),
        Map.entry("timestamptz", "timestamp with time zone"),
        Map.entry("boolean", "boolean"),
        Map.entry("bool", "boolean"),
        Map.entry("uuid", "uuid"),
        Map.entry("jsonb", "jsonb")
    );

    private ModelFieldPhysicalTypeContract() {}

    record TypeDescriptor(
        String logicalType,
        String postgresType
    ) {}

    static TypeDescriptor requireSupported(String value) {
        String logical = normalize(value);
        String simple = SIMPLE_TYPES.get(logical);
        if (simple != null) {
            return new TypeDescriptor(logical, simple);
        }
        Matcher varchar = VARCHAR.matcher(logical);
        if (varchar.matches()) {
            int length = positiveInt(varchar.group(1));
            if (length <= MAX_VARCHAR_LENGTH) {
                return new TypeDescriptor(
                    "varchar(" + length + ")",
                    "text"
                );
            }
        }
        Matcher numeric = NUMERIC.matcher(logical);
        if (numeric.matches()) {
            int precision = positiveInt(numeric.group(1));
            int scale = nonNegativeInt(numeric.group(2));
            if (
                precision <= MAX_NUMERIC_PRECISION &&
                scale <= precision
            ) {
                String canonical =
                    "numeric(" + precision + "," + scale + ")";
                return new TypeDescriptor(canonical, canonical);
            }
        }
        throw new IllegalArgumentException(
            "unsupported model field type"
        );
    }

    static boolean postgresTypesMatch(
        String expected,
        String actual
    ) {
        try {
            String canonicalExpected = canonicalPostgresType(expected);
            String canonicalActual = canonicalPostgresType(actual);
            return canonicalExpected.equals(canonicalActual);
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    static String canonicalPostgresType(String value) {
        String normalized = normalize(value)
            .replaceAll("\\s+", " ");
        if (
            normalized.equals("timestamp") ||
            normalized.equals("timestamp without time zone")
        ) {
            return "timestamp without time zone";
        }
        if (
            normalized.equals("timestamptz") ||
            normalized.equals("timestamp with time zone")
        ) {
            return "timestamp with time zone";
        }
        TypeDescriptor descriptor = requireSupported(normalized);
        return descriptor.postgresType();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "model field type is required"
            );
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int positiveInt(String value) {
        int parsed = nonNegativeInt(value);
        return parsed > 0 ? parsed : Integer.MAX_VALUE;
    }

    private static int nonNegativeInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            return Integer.MAX_VALUE;
        }
    }
}
