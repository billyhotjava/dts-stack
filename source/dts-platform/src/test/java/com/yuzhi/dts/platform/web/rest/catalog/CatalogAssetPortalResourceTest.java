package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogAssetPortalResourceTest {

    @Test
    void listsResolutionFailuresForAssetPortal() {
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetMappingReportService mappingReportService = mock(CatalogAssetMappingReportService.class);
        CatalogAssetIdentityResolutionAuditService resolutionAuditService = mock(CatalogAssetIdentityResolutionAuditService.class);
        OpenMetadataAssetSyncService syncService = mock(OpenMetadataAssetSyncService.class);
        AuditService audit = mock(AuditService.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        Instant since = Instant.parse("2026-05-17T00:00:00Z");
        CatalogAssetResolutionFailure failure = new CatalogAssetResolutionFailure();
        UUID id = UUID.randomUUID();
        failure.setId(id);
        failure.setRef("gov_indicator:missing_metric");
        failure.setRequestedAt(since);
        failure.setCaller("MetricPackPreview");
        failure.setTypeHintGuess("gov_indicator");
        failure.setReason("TYPE_REPOSITORY_MISS");
        when(resolutionAuditService.recentFailures(since, 50)).thenReturn(List.of(failure));

        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            assetPortalService,
            mappingReportService,
            resolutionAuditService,
            syncService,
            audit,
            helper
        );

        List<CatalogAssetPortalResource.ResolutionFailureResponse> response = resource.resolutionFailures(since, 50).getData();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(id);
        assertThat(response.get(0).ref()).isEqualTo("gov_indicator:missing_metric");
        assertThat(response.get(0).reason()).isEqualTo("TYPE_REPOSITORY_MISS");
    }
}
