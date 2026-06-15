package com.yuzhi.dts.platform.service.goldenchain.modeling;

public record GoldenChainDbtAssetSnapshot(
    String packageName,
    String modelName,
    boolean sourceRegistered,
    boolean catalogAssetRegistered,
    boolean lineageReady,
    boolean runtimeGraphReady,
    boolean goldenChainManaged
) {
    public GoldenChainDbtAssetSnapshot {
        packageName = requireText(packageName, "dbt 包名不能为空");
        modelName = requireText(modelName, "dbt 模型名不能为空");
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
