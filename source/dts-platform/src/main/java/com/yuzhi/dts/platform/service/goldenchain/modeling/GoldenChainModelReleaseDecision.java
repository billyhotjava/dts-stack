package com.yuzhi.dts.platform.service.goldenchain.modeling;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;
import java.util.Objects;

public record GoldenChainModelReleaseDecision(
    boolean publishable,
    String modelName,
    GoldenChainModelLayer layer,
    GoldenChainReleaseEnvironment environment,
    List<String> blockers,
    List<String> warnings,
    GoldenChainStageSnapshot stageSnapshot
) {
    public GoldenChainModelReleaseDecision {
        modelName = requireText(modelName, "模型名称不能为空");
        layer = Objects.requireNonNull(layer, "layer must not be null");
        environment = Objects.requireNonNull(environment, "environment must not be null");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
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
