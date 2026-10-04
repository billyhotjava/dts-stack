package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentityResolutionAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetMappingReportService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetPortalService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.OpenMetadataAssetSyncService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogAssetPortalTagFilterResourceTest {

    private CatalogAssetPortalService assetPortalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetPortalService = mock(CatalogAssetPortalService.class);
        CatalogAssetPortalResource resource = new CatalogAssetPortalResource(
            assetPortalService,
            mock(CatalogAssetMappingReportService.class),
            mock(CatalogAssetIdentityResolutionAuditService.class),
            mock(OpenMetadataAssetSyncService.class),
            mock(AuditService.class),
            mock(CatalogResourceHelper.class),
            mock(CatalogAssetTagWriteGuard.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    }

    @Test
    void repeatedTagIdsBindWithoutCommaEncoding() throws Exception {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        when(assetPortalService.listAssets(any(), any()))
            .thenReturn(new CatalogAssetPortalService.AssetPage(List.of(), 0, 0, 20, 0, "dts-catalog"));

        mockMvc
            .perform(
                get("/api/catalog/assets-v2")
                    .param("tagIds", firstTagId.toString(), secondTagId.toString(), firstTagId.toString())
            )
            .andExpect(status().isOk());

        ArgumentCaptor<CatalogAssetPortalService.AssetQuery> query = ArgumentCaptor.forClass(
            CatalogAssetPortalService.AssetQuery.class
        );
        org.mockito.Mockito.verify(assetPortalService).listAssets(query.capture(), any());
        assertThat(query.getValue().tagIds()).containsExactly(firstTagId, secondTagId, firstTagId);
    }

    @Test
    void invalidTagIdReturnsBadRequestBeforeCallingTheService() throws Exception {
        mockMvc
            .perform(get("/api/catalog/assets-v2").param("tagIds", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }
}
