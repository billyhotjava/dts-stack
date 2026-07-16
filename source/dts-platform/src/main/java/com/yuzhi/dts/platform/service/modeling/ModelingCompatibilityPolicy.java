package com.yuzhi.dts.platform.service.modeling;

import java.util.Objects;

/** Compatibility boundary for importing old dbt/semantic assets into Modeling vNext. */
public final class ModelingCompatibilityPolicy {

    public static final String LEGACY_SEMANTIC_MODELS_ROUTE = "/api/semantic/models";

    private ModelingCompatibilityPolicy() {}

    public enum LegacyStatus {
        LEGACY_READONLY,
    }

    public record LegacyAsset(String modelId, String dbtUniqueId, String path, LegacyStatus status, String apiRoute) {}

    public static LegacyAsset registerLegacyDbtModel(String modelId, String dbtUniqueId, String path) {
        return new LegacyAsset(
            required(modelId, "modelId"),
            required(dbtUniqueId, "dbtUniqueId"),
            required(path, "path"),
            LegacyStatus.LEGACY_READONLY,
            LEGACY_SEMANTIC_MODELS_ROUTE
        );
    }

    public static boolean canEditLegacyAsset(LegacyAsset asset) {
        return asset != null && asset.status() != LegacyStatus.LEGACY_READONLY;
    }

    public static boolean canRunLegacyAsset(LegacyAsset asset) {
        return asset != null;
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
