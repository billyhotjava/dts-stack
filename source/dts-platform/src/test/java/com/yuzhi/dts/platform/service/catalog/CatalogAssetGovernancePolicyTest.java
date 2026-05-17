package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import org.junit.jupiter.api.Test;

class CatalogAssetGovernancePolicyTest {

    @Test
    void discoveredAssetStartsPendingGovernance() {
        assertThat(CatalogAssetGovernancePolicy.lifecycleForDiscoveredAsset()).isEqualTo("PENDING_GOVERNANCE");
    }

    @Test
    void datasetMissingAllGovernanceFieldsIsPendingGovernance() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setEnabled(true);

        assertThat(CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset)).isEqualTo("PENDING_GOVERNANCE");
    }

    @Test
    void datasetMissingClassificationKeepsSpecificReason() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setEnabled(true);
        dataset.setOwner("owner");

        assertThat(CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset)).isEqualTo("PENDING_CLASSIFICATION");
    }

    @Test
    void disabledDatasetIsDisabled() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setEnabled(false);

        assertThat(CatalogAssetGovernancePolicy.resolveGovernanceStatus(dataset)).isEqualTo("DISABLED");
    }
}
