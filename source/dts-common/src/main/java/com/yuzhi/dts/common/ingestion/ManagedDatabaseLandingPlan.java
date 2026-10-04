package com.yuzhi.dts.common.ingestion;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Physical landing plans precede catalog observation; they are not quality evidence. */
public final class ManagedDatabaseLandingPlan {

    private static final Set<String> READERS = Set.of(
        "mysqlreader", "postgresqlreader", "oraclereader", "sqlserverreader", "rdbmsreader",
        "clickhousereader", "dmreader", "kingbasereader"
    );

    private ManagedDatabaseLandingPlan() {}

    public static boolean isConfigured(
        String sourceType, String sourceId, String destinationType, String targetSourceId,
        String syncMode, List<String> sources, List<String> targets
    ) {
        if (!READERS.contains(normalize(sourceType)) || !isUuid(sourceId) || !isUuid(targetSourceId)) {
            return false;
        }
        // The managed, non-destructive full-refresh executor supports PostgreSQL.
        if (!"postgresqlwriter".equals(normalize(destinationType))
            || !Set.of("full_refresh", "incremental").contains(normalize(syncMode))) {
            return false;
        }
        if (sources == null || targets == null || sources.isEmpty() || sources.size() > 1000
            || sources.size() != targets.size()) {
            return false;
        }
        return concreteUniqueNames(sources) && concreteUniqueNames(targets);
    }

    private static boolean concreteUniqueNames(List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String value = normalize(name);
            if (value.isEmpty() || value.contains("${") || !seen.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
