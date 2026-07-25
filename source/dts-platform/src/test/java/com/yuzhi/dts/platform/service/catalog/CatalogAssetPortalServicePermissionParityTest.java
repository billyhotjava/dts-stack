package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetExtensionRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataAssetCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataColumnCacheRepository;
import com.yuzhi.dts.platform.repository.catalog.OpenMetadataLineageCacheRepository;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CatalogAssetPortalServicePermissionParityTest {

    @Mock
    private OpenMetadataAssetCacheRepository assetRepository;
    @Mock
    private OpenMetadataColumnCacheRepository columnRepository;
    @Mock
    private OpenMetadataLineageCacheRepository lineageRepository;
    @Mock
    private CatalogAssetExtensionRepository extensionRepository;
    @Mock
    private CatalogAssetMappingRepository mappingRepository;
    @Mock
    private CatalogDatasetRepository datasetRepository;
    @Mock
    private CatalogTableSchemaRepository tableSchemaRepository;
    @Mock
    private CatalogColumnSchemaRepository catalogColumnSchemaRepository;
    @Mock
    private AccessChecker accessChecker;
    @Mock
    private CatalogAssetTagService assetTagService;

    private CatalogAssetPortalService service;

    @BeforeEach
    void setUp() {
        service =
            new CatalogAssetPortalService(
                assetRepository,
                columnRepository,
                lineageRepository,
                extensionRepository,
                mappingRepository,
                datasetRepository,
                tableSchemaRepository,
                catalogColumnSchemaRepository,
                accessChecker,
                assetTagService
            );
    }

    @Test
    void listAssets_shouldHideOpenMetadataSummaryWhenDetailWouldDeny() {
        OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
        asset.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        asset.setFqn("dwd.orders");
        asset.setTableName("orders");
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(asset), PageRequest.of(0, 20), 1));
        when(extensionRepository.findFirstByOmAsset(asset)).thenReturn(Optional.empty());
        when(mappingRepository.findFirstByFqnIgnoreCase("dwd.orders")).thenReturn(Optional.empty());
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        CatalogAssetPortalService.AssetPage result = service.listAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listAssets_shouldHideLegacySummaryWhenDetailWouldDeny() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        dataset.setName("Orders");
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable("orders");
        dataset.setEnabled(true);
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dataset), PageRequest.of(0, 20), 1));
        when(accessChecker.canRead(dataset)).thenReturn(false);

        CatalogAssetPortalService.AssetPage result = service.listAssets(query(), "D01");

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
    }

    private CatalogAssetPortalService.AssetQuery query() {
        return new CatalogAssetPortalService.AssetQuery(null, null, null, null, null, null, null, null, null, null, null, null, false, 0, 20);
    }
}
