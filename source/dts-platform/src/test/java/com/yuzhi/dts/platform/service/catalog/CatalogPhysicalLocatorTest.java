package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogPhysicalLocatorTest {

    @Test
    void derivesOneStableAssetIdFromSourceSchemaAndTable() {
        UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000001");

        CatalogPhysicalLocator first = new CatalogPhysicalLocator(
            sourceId,
            " Finance ",
            "FCT_PAYMENT"
        );
        CatalogPhysicalLocator same = new CatalogPhysicalLocator(
            sourceId,
            "finance",
            "fct_payment"
        );
        CatalogPhysicalLocator anotherSource = new CatalogPhysicalLocator(
            UUID.fromString("10000000-0000-0000-0000-000000000002"),
            "finance",
            "fct_payment"
        );

        assertThat(first).isEqualTo(same);
        assertThat(first.assetId()).isEqualTo(same.assetId());
        assertThat(first.assetId()).isNotEqualTo(anotherSource.assetId());
    }
}
