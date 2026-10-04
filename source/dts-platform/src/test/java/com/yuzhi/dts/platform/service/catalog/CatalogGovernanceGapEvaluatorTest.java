package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogGovernanceGapEvaluatorTest {

    @Test
    void missingGovernanceFieldsAreBlocking() {
        CatalogAssetContract asset = assetContract(List.of("owner", "classification"), "ACTIVE", false);
        CatalogAssetSchemaContract schema = new CatalogAssetSchemaContract(asset, List.of(), 0, "dts-catalog");

        CatalogGovernanceGapEvaluation result = CatalogGovernanceGapEvaluator.evaluate(asset, schema, false);

        assertThat(result.severity()).isEqualTo("BLOCKING");
        assertThat(result.blockingGaps()).contains("owner", "classification");
        assertThat(result.warningGaps()).contains("schemaContract", "lineage");
    }

    @Test
    void completeActiveAssetWithSchemaAndLineageIsReady() {
        CatalogAssetContract asset = assetContract(List.of(), "ACTIVE", true);
        CatalogAssetColumnContract column = new CatalogAssetColumnContract(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "id",
            "varchar",
            false,
            1,
            null,
            null,
            null,
            null,
            null,
            "ACTIVE",
            "dts-catalog",
            null
        );
        CatalogAssetSchemaContract schema = new CatalogAssetSchemaContract(asset, List.of(column), 1, "dts-catalog");

        CatalogGovernanceGapEvaluation result = CatalogGovernanceGapEvaluator.evaluate(asset, schema, true);

        assertThat(result.severity()).isEqualTo("READY");
        assertThat(result.blockingGaps()).isEmpty();
        assertThat(result.warningGaps()).isEmpty();
    }

    private CatalogAssetContract assetContract(List<String> missingFields, String lifecycleStatus, boolean consumable) {
        return new CatalogAssetContract(
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            "DATASET",
            "source:unknown/schema:dwd/table:orders",
            "DATASET",
            "99999999-9999-9999-9999-999999999999",
            "dts-catalog:99999999-9999-9999-9999-999999999999",
            null,
            "dwd.orders",
            "Orders",
            null,
            "dwd",
            "dwd",
            "orders",
            "INTERNAL",
            "DWD",
            "D01",
            "owner",
            null,
            lifecycleStatus,
            missingFields.isEmpty() ? "GOVERNED" : "PENDING_GOVERNANCE",
            missingFields,
            consumable,
            "DTS_NATIVE",
            null,
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            "SYNCED",
            "dts-catalog"
        );
    }
}
