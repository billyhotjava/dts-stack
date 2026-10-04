package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetContractMapperTest {

    @Test
    void openMetadataContractUsesLegacyGrantIdentityWhenMapped() {
        UUID legacyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID domainId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        asset.setOmEntityId("om-table-id");
        asset.setFqn("hive.default.dwd.project_detail");
        asset.setServiceName("hive");
        asset.setDatabaseName("default");
        asset.setSchemaName("dwd");
        asset.setTableName("project_detail");
        asset.setDisplayName("Project Detail");

        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setLegacyDatasetId(legacyId);
        extension.setDomainId(domainId);
        extension.setOwnerDept("D01");
        extension.setClassification("INTERNAL");
        extension.setWarehouseLayer("DWD");
        extension.setLifecycleStatus("ACTIVE");
        extension.setEnabled(true);

        CatalogDataset legacy = new CatalogDataset();
        legacy.setId(legacyId);
        legacy.setName("Project Detail");
        legacy.setHiveDatabase("dwd");
        legacy.setHiveTable("project_detail");
        legacy.setEnabled(true);

        CatalogAssetContract contract = CatalogAssetContractMapper.fromOpenMetadata(asset, extension, null, legacy);

        assertThat(contract.assetType()).isEqualTo("DATASET");
        assertThat(contract.assetKey()).isEqualTo("source:unknown/schema:dwd/table:project_detail");
        assertThat(contract.grantAssetId()).isEqualTo(legacyId.toString());
        assertThat(contract.domainId()).isEqualTo(domainId);
        assertThat(contract.missingGovernanceFields()).isEmpty();
        assertThat(contract.consumable()).isTrue();
    }

    @Test
    void legacyContractReportsMissingGovernanceFields() {
        UUID datasetId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setName("Raw Orders");
        dataset.setEnabled(true);

        CatalogAssetContract contract = CatalogAssetContractMapper.fromLegacy(dataset);

        assertThat(contract.grantAssetType()).isEqualTo("DATASET");
        assertThat(contract.grantAssetId()).isEqualTo(datasetId.toString());
        assertThat(contract.missingGovernanceFields()).contains("owner", "classification", "warehouseLayer", "sourceSystem", "domain");
        assertThat(contract.consumable()).isFalse();
    }
}
