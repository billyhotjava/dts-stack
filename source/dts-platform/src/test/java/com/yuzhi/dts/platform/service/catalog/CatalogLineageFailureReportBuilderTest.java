package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogLineageFailureReportBuilderTest {

    @Test
    void reportsLineageWarningsForReleaseGate() {
        CatalogAssetPortalService.GovernanceGapReport source = report(
            gapAsset("WARNING", List.of(), List.of("lineage"), "dts-catalog")
        );

        CatalogLineageFailureReport result = CatalogLineageFailureReportBuilder.fromGovernanceGapReport(source);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).severity()).isEqualTo("WARNING");
        assertThat(result.content().get(0).blocking()).isFalse();
        assertThat(result.content().get(0).reason()).isEqualTo("lineage-missing");
        assertThat(result.severityCounts()).containsEntry("WARNING", 1L);
        assertThat(result.reasonCounts()).containsEntry("lineage-missing", 1L);
    }

    @Test
    void marksBlockingAssetsWhenGovernanceBlocksPublishing() {
        CatalogAssetPortalService.GovernanceGapReport source = report(
            gapAsset("BLOCKING", List.of("classification", "lifecycleStatus"), List.of("lineage"), "openmetadata-cache")
        );

        CatalogLineageFailureReport result = CatalogLineageFailureReportBuilder.fromGovernanceGapReport(source);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).severity()).isEqualTo("BLOCKING");
        assertThat(result.content().get(0).blocking()).isTrue();
        assertThat(result.content().get(0).reason()).isEqualTo("asset-governance-blocking+lineage-missing");
        assertThat(result.severityCounts()).containsEntry("BLOCKING", 1L);
        assertThat(result.reasonCounts()).containsEntry("asset-governance-blocking+lineage-missing", 1L);
    }

    @Test
    void ignoresReadyAssetsWithoutLineageOrBlockingGaps() {
        CatalogAssetPortalService.GovernanceGapReport source = report(
            gapAsset("READY", List.of(), List.of("schemaContract"), "openmetadata-cache")
        );

        CatalogLineageFailureReport result = CatalogLineageFailureReportBuilder.fromGovernanceGapReport(source);

        assertThat(result.content()).isEmpty();
        assertThat(result.severityCounts()).isEmpty();
        assertThat(result.reasonCounts()).isEmpty();
    }

    private CatalogAssetPortalService.GovernanceGapReport report(CatalogAssetPortalService.GovernanceGapAsset asset) {
        return new CatalogAssetPortalService.GovernanceGapReport(
            List.of(asset),
            Map.of(asset.severity(), 1L),
            Map.of(),
            1,
            0,
            1,
            0,
            50,
            asset.metadataSource()
        );
    }

    private CatalogAssetPortalService.GovernanceGapAsset gapAsset(
        String severity,
        List<String> blockingGaps,
        List<String> warningGaps,
        String metadataSource
    ) {
        UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
        return new CatalogAssetPortalService.GovernanceGapAsset(
            id,
            "Orders",
            "dwd.orders",
            "source:unknown/schema:dwd/table:orders",
            "DATASET",
            id.toString(),
            "PENDING_GOVERNANCE",
            "PENDING_GOVERNANCE",
            severity,
            blockingGaps,
            warningGaps,
            metadataSource
        );
    }
}
