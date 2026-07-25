package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetContract;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetContractMapper;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogAssetPortalResourceTest {

    @Test
    void listsResolutionFailuresForAssetPortal() {
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetMappingReportService mappingReportService = mock(CatalogAssetMappingReportService.class);
        CatalogAssetIdentityResolutionAuditService resolutionAuditService = mock(CatalogAssetIdentityResolutionAuditService.class);
        OpenMetadataAssetSyncService syncService = mock(OpenMetadataAssetSyncService.class);
        AuditService audit = mock(AuditService.class);
        CatalogResourceHelper helper = mock(CatalogResourceHelper.class);
        CatalogAssetTagWriteGuard assetTagWriteGuard = mock(CatalogAssetTagWriteGuard.class);
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
            helper,
            assetTagWriteGuard
        );

        List<CatalogAssetPortalResource.ResolutionFailureResponse> response = resource.resolutionFailures(since, 50).getData();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(id);
        assertThat(response.get(0).ref()).isEqualTo("gov_indicator:missing_metric");
        assertThat(response.get(0).reason()).isEqualTo("TYPE_REPOSITORY_MISS");
    }

    @Test
    void assetContractExposesAnExplicitFailClosedTagWriteCapability() throws Exception {
        CatalogAssetPortalService assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetTagWriteGuard assetTagWriteGuard = mock(CatalogAssetTagWriteGuard.class);
        UUID assetId = UUID.randomUUID();
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(assetId);
        dataset.setName("orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        CatalogAssetContract contract = CatalogAssetContractMapper.fromLegacy(dataset);
        String assetKey = contract.assetKey();
        when(assetPortalService.getAssetContract(assetId, null)).thenReturn(contract);
        when(assetTagWriteGuard.canTag(new AssetRef("DATASET", assetKey))).thenReturn(true);
        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            assetPortalService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            mock(CatalogResourceHelper.class),
            assetTagWriteGuard
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(resource).build();

        mockMvc
            .perform(get("/api/catalog/assets-v2/{id}/contract", assetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assetKey").value(assetKey))
            .andExpect(jsonPath("$.data.canTag").value(true));
    }
}
