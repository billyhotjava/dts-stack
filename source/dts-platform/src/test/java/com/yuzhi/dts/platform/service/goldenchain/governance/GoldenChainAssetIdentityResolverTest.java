package com.yuzhi.dts.platform.service.goldenchain.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenChainAssetIdentityResolverTest {

    private final GoldenChainAssetIdentityResolver resolver = new GoldenChainAssetIdentityResolver();

    @Test
    void platformAssetIdWinsAcrossCatalogAndServiceViews() {
        GoldenChainAssetIdentityResolution resolution = resolver.resolve(
            List.of(
                new GoldenChainAssetIdentityCandidate(
                    "catalog",
                    "dbt_model",
                    "asset-123",
                    "om://dts.pm.biz_dwd_project_node_enriched",
                    "dbt:pm:biz_dwd_project_node_enriched",
                    "legacy-pm-dwd"
                ),
                new GoldenChainAssetIdentityCandidate(
                    "api-service",
                    "api_service",
                    "asset-123",
                    null,
                    "api:project-node",
                    "legacy-api-project-node"
                )
            )
        );

        assertThat(resolution.state()).isEqualTo(GoldenChainGovernanceState.READY);
        assertThat(resolution.assetKey()).isEqualTo("platform:asset-123");
        assertThat(resolution.diagnostics()).isEmpty();
    }

    @Test
    void unresolvedIdentityProducesPendingGovernanceDiagnostic() {
        GoldenChainAssetIdentityResolution resolution = resolver.resolve(
            List.of(new GoldenChainAssetIdentityCandidate("manual", "dbt_model", null, " ", null, null))
        );

        assertThat(resolution.state()).isEqualTo(GoldenChainGovernanceState.PENDING_GOVERNANCE);
        assertThat(resolution.assetKey()).isBlank();
        assertThat(resolution.diagnostics()).anyMatch(diagnostic -> diagnostic.contains("无法解析资产身份"));
    }

    @Test
    void conflictingAssetKeysAreNotMergedSilently() {
        GoldenChainAssetIdentityResolution resolution = resolver.resolve(
            List.of(
                new GoldenChainAssetIdentityCandidate("catalog", "dbt_model", "asset-123", null, null, null),
                new GoldenChainAssetIdentityCandidate("openmetadata", "dbt_model", null, "om://dts.pm.same_model", null, null)
            )
        );

        assertThat(resolution.state()).isEqualTo(GoldenChainGovernanceState.PENDING_GOVERNANCE);
        assertThat(resolution.diagnostics()).anyMatch(diagnostic -> diagnostic.contains("重复资产身份"));
    }
}
