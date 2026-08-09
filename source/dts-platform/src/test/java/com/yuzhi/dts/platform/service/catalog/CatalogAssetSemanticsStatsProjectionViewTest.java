package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.Freshness.STALE;
import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness.GOVERNED;
import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness.INCOMPLETE;
import static com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.GovernanceReadiness.UNASSIGNED;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsBucket;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetSemanticsStatsProjectionViewTest {

    @Test
    void keepsTheLegacyTreeShapeAndAddsExplicitProjectionEvidence() {
        UUID finance = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID project = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant asOf = Instant.parse("2026-08-10T08:00:00Z");
        StatsSnapshot snapshot = new StatsSnapshot(
            List.of(
                new StatsBucket(finance, "DWD", CatalogAssetType.DATASET, GOVERNED, 7),
                new StatsBucket(finance, "DWS", CatalogAssetType.DATASET, INCOMPLETE, 2),
                new StatsBucket(project, "ADS", CatalogAssetType.DATASET, GOVERNED, 3),
                new StatsBucket(null, null, CatalogAssetType.DATASET, UNASSIGNED, 5)
            ),
            17,
            asOf,
            STALE,
            true,
            "FRESH"
        );

        CatalogAssetStatsProjectionView view = CatalogAssetStatsProjectionView.from(snapshot);

        assertThat(view.all()).isEqualTo(new CatalogAssetStatsProjectionView.DomainStats(17, 7));
        assertThat(view.unassigned()).isEqualTo(new CatalogAssetStatsProjectionView.DomainStats(5, 5));
        assertThat(view.byDomain().get(finance.toString()))
            .isEqualTo(new CatalogAssetStatsProjectionView.DomainStats(9, 2));
        assertThat(view.byDomain().get(project.toString()))
            .isEqualTo(new CatalogAssetStatsProjectionView.DomainStats(3, 0));
        assertThat(view.asOf()).isEqualTo(asOf);
        assertThat(view.freshness()).isEqualTo(STALE);
        assertThat(view.approximate()).isTrue();
        assertThat(view.projectionState()).isEqualTo("FRESH");
        assertThat(view.scanned()).isZero();
        assertThat(view.truncated()).isFalse();
    }
}
