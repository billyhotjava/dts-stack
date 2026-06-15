package com.yuzhi.dts.platform.service.goldenchain.modeling;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainOdsDbtSourceCandidate(
    boolean publishable,
    String sourceName,
    String schemaName,
    String tableName,
    String owner,
    String refreshCadence,
    List<GoldenChainOdsColumnSnapshot> columns,
    String sourceYaml,
    List<String> blockers,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainOdsDbtSourceCandidate {
        sourceName = requireText(sourceName, "dbt source 名称不能为空");
        schemaName = requireText(schemaName, "ODS schema 不能为空");
        tableName = requireText(tableName, "ODS 表名不能为空");
        owner = requireText(owner, "负责人不能为空");
        refreshCadence = normalize(refreshCadence);
        columns = columns == null ? List.of() : List.copyOf(columns);
        sourceYaml = sourceYaml == null ? "" : sourceYaml;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        stageSnapshot = Objects.requireNonNull(stageSnapshot, "stageSnapshot must not be null");
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
