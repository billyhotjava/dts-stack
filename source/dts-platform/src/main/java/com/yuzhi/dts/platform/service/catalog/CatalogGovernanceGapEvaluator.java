package com.yuzhi.dts.platform.service.catalog;

import java.util.ArrayList;
import java.util.List;

public final class CatalogGovernanceGapEvaluator {

    private CatalogGovernanceGapEvaluator() {}

    public static CatalogGovernanceGapEvaluation evaluate(
        CatalogAssetContract asset,
        CatalogAssetSchemaContract schema,
        boolean hasLineageEvidence
    ) {
        List<String> blocking = new ArrayList<>();
        List<String> warning = new ArrayList<>();
        if (asset == null) {
            blocking.add("asset");
            return new CatalogGovernanceGapEvaluation("BLOCKING", blocking, warning);
        }

        blocking.addAll(asset.missingGovernanceFields());
        if (!CatalogAssetLifecycleStatus.ACTIVE.name().equalsIgnoreCase(asset.lifecycleStatus())) {
            blocking.add("lifecycleStatus");
        }
        if (!asset.consumable() && blocking.isEmpty()) {
            blocking.add("consumable");
        }

        if (schema == null || schema.columnCount() <= 0) {
            warning.add("schemaContract");
        }
        if (!hasLineageEvidence) {
            warning.add("lineage");
        }
        if (asset.syncStatus() == null || (!"SYNCED".equalsIgnoreCase(asset.syncStatus()) && !"ACTIVE".equalsIgnoreCase(asset.syncStatus()))) {
            warning.add("freshness");
        }

        String severity = !blocking.isEmpty() ? "BLOCKING" : (!warning.isEmpty() ? "WARNING" : "READY");
        return new CatalogGovernanceGapEvaluation(severity, List.copyOf(blocking), List.copyOf(warning));
    }
}
