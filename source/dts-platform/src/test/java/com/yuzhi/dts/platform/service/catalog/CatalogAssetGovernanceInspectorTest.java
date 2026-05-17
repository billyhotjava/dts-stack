package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import org.junit.jupiter.api.Test;

class CatalogAssetGovernanceInspectorTest {

    @Test
    void missingGovernanceFieldsAreReported() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setEnabled(true);

        CatalogAssetGovernanceProfile profile = CatalogAssetGovernanceInspector.inspect(dataset);

        assertThat(profile.missingFields()).contains("owner", "classification", "warehouseLayer", "sourceSystem", "domain");
        assertThat(profile.consumable()).isFalse();
    }

    @Test
    void activeLifecycleAloneIsNotEnoughWithoutDomain() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setEnabled(true);
        dataset.setLifecycleStatus("ACTIVE");
        dataset.setOwner("owner");
        dataset.setClassification("INTERNAL");
        dataset.setWarehouseLayer("DWD");
        dataset.setHiveDatabase("public");

        CatalogAssetGovernanceProfile profile = CatalogAssetGovernanceInspector.inspect(dataset);

        assertThat(profile.lifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(profile.missingFields()).containsExactly("domain");
        assertThat(profile.consumable()).isFalse();
    }
}
