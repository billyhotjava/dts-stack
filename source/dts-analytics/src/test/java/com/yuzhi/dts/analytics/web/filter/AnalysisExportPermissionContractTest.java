package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalysisExportPermissionContractTest {

    @Test
    void analysisDownloadsResolveToTheCardAssetAndExportAction() {
        for (String format : new String[] { "csv", "xlsx" }) {
            String path = "/api/analysis/42/query/" + format;
            PlatformPermissionFilter.AssetRef asset = PlatformPermissionFilter.resolveAsset(path);

            assertThat(asset).isNotNull();
            assertThat(asset.type()).isEqualTo("CARD");
            assertThat(asset.id()).isEqualTo("42");
            assertThat(PlatformPermissionFilter.actionFor("POST", path)).isEqualTo("EXPORT");
        }
    }
}
