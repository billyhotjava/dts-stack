package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.DimensionPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.MetricPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryDatasetPublishedModelContractTest {

    @Test
    void preservesPublishedModelDimensionsMetricsLayerAndClassification() {
        QueryDatasetContractSnapshotAssembler assembler = new QueryDatasetContractSnapshotAssembler(new ObjectMapper());
        UUID modelId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        QueryDatasetAsset asset = new QueryDatasetAsset();
        asset.setId(UUID.fromString("70000000-0000-0000-0000-000000000001"));
        asset.setSourceDatasourceId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(1);
        version.setSqlText("SELECT * FROM \"public\".\"biz_ads_budget_kpi_v2\"");
        CatalogDataset physical = new CatalogDataset();
        physical.setWarehouseLayer("ADS");
        ModelIdentity identity = new ModelIdentity(
            ModelIdentityType.SEMANTIC_MODEL,
            modelId,
            modelId,
            null,
            "预算执行指标",
            3,
            null
        );
        PublishPayload semantic = new PublishPayload(
            "default",
            asset.getSourceDatasourceId().toString(),
            "model_spec_" + modelId.toString().replace("-", ""),
            "biz_ads_budget_kpi_v2",
            "public",
            "预算执行指标",
            null,
            "INTERNAL",
            "project_code",
            "r3",
            true,
            List.of(new MetricPayload("budget_amount", "预算金额", "SUM", "budget_amount", "元", null, null, null, "INTERNAL", null)),
            List.of(new DimensionPayload("project_code", "项目编码", "dimension", null)),
            List.of()
        );
        UUID snapshotId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        DerivationResult classification = new DerivationResult(
            "REPORT",
            "bi-dataset:" + asset.getId(),
            "DATA_INTERNAL",
            snapshotId,
            1,
            List.of(new ResolvedSource("ASSET", identity.assetKey(), snapshotId, 1, "DATA_INTERNAL")),
            null,
            List.of()
        );

        var snapshot = assembler.assemblePublishedModel(asset, version, physical, identity, semantic, classification);

        assertThat(snapshot.status()).isEqualTo("READY");
        assertThat(snapshot.version()).isEqualTo("r3");
        assertThat(snapshot.contractJson())
            .contains("\"warehouseLayer\":\"ADS\"")
            .contains("\"code\":\"project_code\"")
            .contains("\"label\":\"项目编码\"")
            .contains("\"code\":\"budget_amount\"")
            .contains("\"aggregation\":\"SUM\"")
            .contains("\"expression\":\"budget_amount\"")
            .contains("\"classificationFloor\":\"DATA_INTERNAL\"");
        assertThat(snapshot.checksum()).hasSize(64);
    }
}
