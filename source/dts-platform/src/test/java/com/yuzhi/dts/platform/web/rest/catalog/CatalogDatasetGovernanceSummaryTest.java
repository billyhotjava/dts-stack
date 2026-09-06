package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CatalogDatasetGovernanceSummaryTest {
    @Test
    void formatsSharedVersionEtagForPatchAndFullPutConcurrency() {
        CatalogDataset dataset = dataset();
        assertThat(CatalogDatasetResource.datasetEtag(dataset)).isEqualTo("\"catalog-dataset:" + dataset.getId() + ":0\"");
    }

    @Test
    void rejectsMissingAndStaleIfMatchBeforeOwnerDescriptionMutation() {
        CatalogDataset dataset = dataset();
        assertThatThrownBy(() -> CatalogDatasetResource.requireDatasetEtag(dataset, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CatalogDatasetResource.requireDatasetEtag(dataset, "\"catalog-dataset:" + dataset.getId() + ":9\"")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void versionChangesTheSharedEtagUsedByPatchAndFullPut() {
        CatalogDataset dataset = dataset();
        dataset.setVersion(1L);
        assertThat(CatalogDatasetResource.datasetEtag(dataset)).endsWith(":1\"");
    }

    private static CatalogDataset dataset() { CatalogDataset dataset = new CatalogDataset(); dataset.setId(UUID.randomUUID()); dataset.setName("asset"); return dataset; }
}
