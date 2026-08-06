package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Canonical contracts for the global custom warehouse-layer registry. */
public final class WarehouseLayerContract {

    private WarehouseLayerContract() {}

    /** DataWorks-aligned layer group; derived from the immutable system layer code. */
    public enum LayerGroup {
        STAGING,
        COMMON,
        APPLICATION
    }

    private static final Map<String, LayerGroup> SYSTEM_TO_GROUP = Map.of(
        "ODS_RAW", LayerGroup.STAGING,
        "ODS_STANDARDIZED", LayerGroup.STAGING,
        "STG", LayerGroup.STAGING,
        "DWD", LayerGroup.COMMON,
        "DWS", LayerGroup.COMMON,
        "ADS", LayerGroup.APPLICATION
    );

    private static final Map<String, List<ModelSpecContract.ModelType>> SYSTEM_TO_MODEL_TYPES = Map.of(
        "ODS_RAW", List.of(),
        "ODS_STANDARDIZED", List.of(),
        "STG", List.of(),
        "DWD", List.of(ModelSpecContract.ModelType.DIMENSION, ModelSpecContract.ModelType.FACT),
        "DWS", List.of(ModelSpecContract.ModelType.SUMMARY),
        "ADS", List.of(ModelSpecContract.ModelType.APPLICATION, ModelSpecContract.ModelType.DIMENSION)
    );

    public static LayerGroup groupOf(String systemLayerCode) {
        LayerGroup group = systemLayerCode == null
            ? null
            : SYSTEM_TO_GROUP.get(systemLayerCode.trim().toUpperCase(Locale.ROOT));
        if (group == null) {
            throw new IllegalArgumentException("unknown warehouse system layer code: " + systemLayerCode);
        }
        return group;
    }

    public static List<ModelSpecContract.ModelType> modelTypesOf(String systemLayerCode) {
        List<ModelSpecContract.ModelType> modelTypes = systemLayerCode == null
            ? null
            : SYSTEM_TO_MODEL_TYPES.get(systemLayerCode.trim().toUpperCase(Locale.ROOT));
        if (modelTypes == null) {
            throw new IllegalArgumentException("unknown warehouse system layer code: " + systemLayerCode);
        }
        return modelTypes;
    }

    public record CreateWarehouseLayerCommand(
        String code,
        String name,
        String systemLayerCode,
        String description,
        String namingPrefix
    ) {}

    public record WarehouseLayerView(
        String code,
        String name,
        String systemLayerCode,
        String kind,
        String responsibility,
        List<String> namingPrefixes,
        boolean optional,
        boolean builtin,
        boolean deletable,
        String disabledReason,
        LayerGroup layerGroup,
        List<ModelSpecContract.ModelType> modelTypes
    ) {}

    public record ResolvedWarehouseLayer(String code, ModelSpecContract.Layer canonicalLayer, boolean builtin) {}

    public record StoredWarehouseLayer(
        UUID id,
        String code,
        String name,
        String systemLayerCode,
        LayerGroup layerGroup,
        String description,
        String namingPrefix,
        String status,
        int version,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
    ) {}
}
