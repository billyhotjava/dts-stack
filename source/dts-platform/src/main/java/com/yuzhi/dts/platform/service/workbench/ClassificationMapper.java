package com.yuzhi.dts.platform.service.workbench;

import java.util.List;
import java.util.Locale;

/**
 * Maps between the database-level {@code classification} vocabulary
 * ({@code PUBLIC / INTERNAL / SECRET / TOP_SECRET}) and the public-facing
 * Sprint-15 API codes ({@code S1 / S2 / S3 / S4}).
 *
 * <p>Mapping table:
 * <pre>
 *   TOP_SECRET -> S1
 *   SECRET     -> S2
 *   INTERNAL   -> S3
 *   PUBLIC     -> S4
 * </pre>
 *
 * <p>Unknown / null values are passed through unchanged so callers can
 * still see raw data if the dictionary is mis-configured.
 */
public final class ClassificationMapper {

    private ClassificationMapper() {}

    public static String toApiCode(String dbClassification) {
        if (dbClassification == null) {
            return null;
        }
        return switch (dbClassification.trim().toUpperCase(Locale.ROOT)) {
            case "TOP_SECRET" -> "S1";
            case "SECRET" -> "S2";
            case "INTERNAL" -> "S3";
            case "PUBLIC" -> "S4";
            default -> dbClassification;
        };
    }

    public static List<String> toDbValues(String apiCode) {
        if (apiCode == null) {
            return List.of();
        }
        return switch (apiCode.trim().toUpperCase(Locale.ROOT)) {
            case "S1" -> List.of("TOP_SECRET");
            case "S2" -> List.of("SECRET");
            case "S3" -> List.of("INTERNAL");
            case "S4" -> List.of("PUBLIC");
            default -> List.of(apiCode);
        };
    }
}
