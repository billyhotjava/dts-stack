package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetKeyTest {

    @Test
    void datasetKeyUsesSourceSchemaAndTable() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        String key = CatalogAssetKey.dataset(sourceId, "warehouse", "DWD", "Project Detail", null);

        assertThat(key).isEqualTo("source:11111111-1111-1111-1111-111111111111/schema:dwd/table:project_detail");
    }

    @Test
    void datasetEntityFallsBackToNameWhenTableIsMissing() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setHiveDatabase("public");
        dataset.setName("ADS Project Dashboard");

        String key = CatalogAssetKey.dataset(dataset);

        assertThat(key).isEqualTo("source:unknown/schema:public/table:ads_project_dashboard");
    }

    @Test
    void explicitAssetTypesNormalizeHyphenatedNames() {
        assertThat(CatalogAssetType.from("bi-dataset")).isEqualTo(CatalogAssetType.BI_DATASET);
        assertThat(CatalogAssetType.from("modeling-sql-model")).isEqualTo(CatalogAssetType.MODELING_SQL_MODEL);
        assertThat(CatalogAssetType.from("api-service")).isEqualTo(CatalogAssetType.API_SERVICE);
    }

    @Test
    void metricKeyRequiresMetricCode() {
        assertThatThrownBy(() -> CatalogAssetKey.metric("flower-rental", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("metric code is required");
    }

    @Test
    void scopedMetricKeyIncludesTenantAndPackIdentity() {
        assertThat(
            CatalogAssetKey.metric(
                "Acme North",
                "Order Summary",
                "Gross Amount"
            )
        )
            .isEqualTo(
                "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:gross_amount"
            );
    }

    @Test
    void identityUsesAssetIdForGrantWhenPresent() {
        CatalogAssetIdentity identity = new CatalogAssetIdentity(
            CatalogAssetType.DATASET,
            "source:unknown/schema:public/table:demo",
            "dataset-id",
            "test"
        );

        assertThat(identity.grantAssetType()).isEqualTo("DATASET");
        assertThat(identity.grantAssetId()).isEqualTo("dataset-id");
    }

    @Test
    void sourceReferencePrefersOpenMetadataEntityId() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setOmEntityId("om-entity-id");
        asset.setFqn("service.database.public.table");
        asset.setServiceName("hive");

        CatalogAssetSourceReference sourceRef = CatalogAssetSourceReference.openMetadata(asset, "fqn");

        assertThat(sourceRef.stableRef()).isEqualTo("openmetadata:om-entity-id");
        assertThat(sourceRef.fqn()).isEqualTo("service.database.public.table");
    }

    @Test
    void scopedDatasetKeyIncludesTenantEnvironmentAndDialect() {
        String key = CatalogAssetKey.scopedDataset("flowerbiz", "uat", "dm", "ptr-mysql", "DWD", "Project Detail");

        assertThat(key).isEqualTo("tenant:flowerbiz/env:uat/dialect:dm/source:ptr-mysql/schema:dwd/table:project_detail");
    }

    @Test
    void scopedDatasetRequiresTenantNamespace() {
        assertThatThrownBy(() -> CatalogAssetKey.scopedDataset(null, "uat", "dm", "ptr-mysql", "DWD", "Project Detail"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenant namespace is required");
    }

    @Test
    void codeAssetKeySupportsExistingModelingAndGovernanceDomains() {
        String key = CatalogAssetKey.codeAsset(CatalogAssetType.GOV_INDICATOR, "flowerbiz", "collection-rate");

        assertThat(key).isEqualTo("tenant:flowerbiz/env:prod/dialect:generic/gov_indicator:collection-rate");
    }

    @Test
    void codeAssetRequiresAssetType() {
        assertThatThrownBy(() -> CatalogAssetKey.codeAsset(null, "flowerbiz", "collection-rate"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("asset type is required");
    }
}
