package com.yuzhi.dts.platform.service.catalog;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Database-independent identity for one physical relation in one registered data source.
 */
public record CatalogPhysicalLocator(
    UUID sourceId,
    String schemaName,
    String tableName
) {

    public CatalogPhysicalLocator {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId is required");
        }
        schemaName = normalized(schemaName, "schemaName");
        tableName = normalized(tableName, "tableName");
    }

    public UUID assetId() {
        String value = String.join(
            ":",
            "catalog-physical",
            sourceId.toString(),
            schemaName,
            tableName
        );
        return UUID.nameUUIDFromBytes(
            value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String normalized(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
