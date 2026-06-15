package com.yuzhi.dts.platform.service.goldenchain.modeling;

import java.util.List;

public record GoldenChainOdsDbtSourceRequest(
    String sourceName,
    String schemaName,
    String tableName,
    String owner,
    String refreshCadence,
    List<GoldenChainOdsColumnSnapshot> columns
) {
    public GoldenChainOdsDbtSourceRequest {
        sourceName = requireText(sourceName, "dbt source 名称不能为空");
        schemaName = requireText(schemaName, "ODS schema 不能为空");
        tableName = requireText(tableName, "ODS 表名不能为空");
        owner = normalize(owner);
        refreshCadence = normalize(refreshCadence);
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
