package com.yuzhi.dts.platform.service.goldenchain.modeling;

import java.util.List;

public record GoldenChainDbtMigrationItem(
    String packageName,
    String modelName,
    List<GoldenChainDbtMigrationStatus> statuses,
    GoldenChainDbtMigrationRisk riskLevel,
    String nextAction
) {
    public GoldenChainDbtMigrationItem {
        packageName = requireText(packageName, "dbt 包名不能为空");
        modelName = requireText(modelName, "dbt 模型名不能为空");
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("迁移状态不能为空");
        }
        riskLevel = riskLevel == null ? GoldenChainDbtMigrationRisk.MEDIUM : riskLevel;
        nextAction = requireText(nextAction, "下一步动作不能为空");
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
