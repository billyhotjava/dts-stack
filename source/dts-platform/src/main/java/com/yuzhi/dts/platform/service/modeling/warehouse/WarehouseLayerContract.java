package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Canonical contracts for the global custom warehouse-layer registry. */
public final class WarehouseLayerContract {

    private WarehouseLayerContract() {}

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
        String disabledReason
    ) {}

    public record ResolvedWarehouseLayer(String code, ModelSpecContract.Layer canonicalLayer, boolean builtin) {}

    public record StoredWarehouseLayer(
        UUID id,
        String code,
        String name,
        String systemLayerCode,
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
