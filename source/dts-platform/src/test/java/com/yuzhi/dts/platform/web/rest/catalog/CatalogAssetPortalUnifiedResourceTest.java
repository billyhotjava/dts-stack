package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogUnifiedAssetDirectoryService;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogAssetPortalUnifiedResourceTest {

    @Test
    void allFamilyUsesUnifiedOwnerAggregationWithoutChangingTheLegacyDefault() throws Exception {
        CatalogAssetPortalService legacyService = mock(CatalogAssetPortalService.class);
        CatalogUnifiedAssetDirectoryService unifiedService = mock(CatalogUnifiedAssetDirectoryService.class);
        when(unifiedService.list(any(), eq("ALL"), isNull())).thenReturn(
            new CatalogUnifiedAssetDirectoryService.UnifiedAssetPage(
                List.of(),
                0,
                0,
                10,
                0,
                "dts-owner-aggregation"
            )
        );
        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            legacyService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            mock(CatalogResourceHelper.class),
            mock(CatalogAssetTagWriteGuard.class),
            unifiedService,
            mock(CatalogDatasetRepository.class)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(resource).build();

        mockMvc
            .perform(get("/api/catalog/assets-v2").param("assetFamily", "ALL").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.metadataSource").value("dts-owner-aggregation"));

        verify(unifiedService).list(any(), eq("ALL"), isNull());
        verify(legacyService, never()).listAssets(any(), any());
    }
}
