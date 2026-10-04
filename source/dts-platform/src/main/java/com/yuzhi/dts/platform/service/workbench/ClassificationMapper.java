package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.List;
import java.util.Locale;

/**
 * Maps between the database-level {@code classification} vocabulary
 * ({@code PUBLIC / INTERNAL / SECRET / CONFIDENTIAL}, with legacy aliases) and the public-facing
 * Sprint-15 API codes ({@code S1 / S2 / S3 / S4}).
 *
 * <p>Mapping table:
 * <pre>
 *   CONFIDENTIAL -> S1
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
        SecurityLevelCatalog.DataSecurityLevel level = SecurityLevelCatalog.DataSecurityLevel.parse(dbClassification);
        if (level == null) {
            return dbClassification;
        }
        return switch (level) {
            case CONFIDENTIAL -> "S1";
            case SECRET -> "S2";
            case INTERNAL -> "S3";
            case PUBLIC -> "S4";
        };
    }

    public static List<String> toDbValues(String apiCode) {
        String normalized = normalizeApiCode(apiCode);
        if (normalized.isEmpty()) {
            return List.of();
        }
        SecurityLevelCatalog.DataSecurityLevel level = switch (normalized) {
            case "S1" -> SecurityLevelCatalog.DataSecurityLevel.CONFIDENTIAL;
            case "S2" -> SecurityLevelCatalog.DataSecurityLevel.SECRET;
            case "S3" -> SecurityLevelCatalog.DataSecurityLevel.INTERNAL;
            case "S4" -> SecurityLevelCatalog.DataSecurityLevel.PUBLIC;
            default -> null;
        };
        if (level == null) {
            return List.of(apiCode);
        }
        return SecurityLevelCatalog.dataStorageTokens(level);
    }

    private static String normalizeApiCode(String apiCode) {
        return apiCode == null ? "" : apiCode.trim().toUpperCase(Locale.ROOT);
    }
}
