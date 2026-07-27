package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension;
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
import java.util.ArrayList;
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

/**
 * 资产列表的分页组合与统计口径回归。
 *
 * <p>资产列表由两个数据源拼成一个逻辑列表：OpenMetadata 缓存在前，legacy 目录在后。
 * 历史实现让 legacy 复用 OpenMetadata 的页码，导致 OpenMetadata 占满首页时
 * legacy 行永远取不到；本测试锁定修复后的可达性、无重无漏与总数口径。
 */
@ExtendWith(MockitoExtension.class)
class CatalogAssetPortalStatsTest {

    private static final String ACTIVE_DEPT = "D01";

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

    private final List<OpenMetadataAssetCache> openMetadataAssets = new ArrayList<>();
    private final List<CatalogDataset> legacyAssets = new ArrayList<>();

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
        lenient().when(accessChecker.canRead(any())).thenReturn(true);
        lenient().when(accessChecker.departmentAllowed(any(), any())).thenReturn(true);
        lenient().when(mappingRepository.findFirstByFqnIgnoreCase(any())).thenReturn(Optional.empty());
        // canRead 在 extension 为 null 时直接判不可见，所以可见的 OpenMetadata 资产必须配一个启用的扩展。
        lenient()
            .when(extensionRepository.findFirstByOmAsset(any()))
            .thenAnswer(invocation -> Optional.of(enabledExtension(invocation.getArgument(0))));
    }

    private CatalogAssetExtension enabledExtension(OpenMetadataAssetCache asset) {
        CatalogAssetExtension extension = new CatalogAssetExtension();
        extension.setOmAsset(asset);
        extension.setEnabled(true);
        extension.setClassification("DATA_INTERNAL");
        return extension;
    }

    @Test
    void legacyAssetsAreReachableWhenOpenMetadataFillsFirstPage() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        CatalogAssetPortalService.AssetPage third = service.listAssets(queryOf(2, 2), ACTIVE_DEPT);

        assertThat(third.content()).hasSize(2);
        assertThat(third.content()).allSatisfy(row -> assertThat(row.metadataSource()).isEqualTo("dts-catalog"));
    }

    @Test
    void pagingCoversEveryRowExactlyOnce() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        List<String> keys = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            service.listAssets(queryOf(page, 2), ACTIVE_DEPT).content().forEach(row -> keys.add(row.assetKey()));
        }

        assertThat(keys).hasSize(6).doesNotHaveDuplicates();
    }

    @Test
    void totalStaysStableAcrossPages() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);

        assertThat(service.listAssets(queryOf(0, 2), ACTIVE_DEPT).total()).isEqualTo(6);
        assertThat(service.listAssets(queryOf(1, 2), ACTIVE_DEPT).total()).isEqualTo(6);
        assertThat(service.listAssets(queryOf(2, 2), ACTIVE_DEPT).total()).isEqualTo(6);
    }

    @Test
    void totalExcludesRowsHiddenByVisibility() {
        givenOpenMetadataAssets(3);
        givenLegacyAssets(2);
        givenFirstOpenMetadataAssetUnreadable();

        CatalogAssetPortalService.AssetPage page = service.listAssets(queryOf(0, 10), ACTIVE_DEPT);

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.content()).hasSize(4);
    }

    /** 按 pageable 切片装配 OpenMetadata 侧的分页返回。 */
    private void givenOpenMetadataAssets(int count) {
        for (int i = 0; i < count; i++) {
            OpenMetadataAssetCache asset = new OpenMetadataAssetCache();
            asset.setId(UUID.nameUUIDFromBytes(("om-" + i).getBytes()));
            asset.setFqn("dwd.om_" + i);
            asset.setTableName("om_" + i);
            openMetadataAssets.add(asset);
        }
        when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenAnswer(invocation -> slice(openMetadataAssets, invocation.getArgument(1)));
    }

    /** 按 pageable 切片装配 legacy 侧的分页返回。 */
    private void givenLegacyAssets(int count) {
        for (int i = 0; i < count; i++) {
            CatalogDataset dataset = new CatalogDataset();
            dataset.setId(UUID.nameUUIDFromBytes(("legacy-" + i).getBytes()));
            dataset.setName("Legacy " + i);
            dataset.setHiveDatabase("dwd");
            dataset.setHiveTable("legacy_" + i);
            dataset.setEnabled(true);
            legacyAssets.add(dataset);
        }
        when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
            .thenAnswer(invocation -> slice(legacyAssets, invocation.getArgument(1)));
    }

    /** 让首个 OpenMetadata 资产在可见性校验中被拒：扩展存在但已停用。 */
    private void givenFirstOpenMetadataAssetUnreadable() {
        OpenMetadataAssetCache hidden = openMetadataAssets.get(0);
        lenient()
            .when(extensionRepository.findFirstByOmAsset(hidden))
            .thenAnswer(invocation -> {
                CatalogAssetExtension disabled = enabledExtension(hidden);
                disabled.setEnabled(false);
                return Optional.of(disabled);
            });
    }

    private <T> PageImpl<T> slice(List<T> source, PageRequest pageable) {
        int from = Math.min((int) pageable.getOffset(), source.size());
        int to = Math.min(from + pageable.getPageSize(), source.size());
        return new PageImpl<>(source.subList(from, to), pageable, source.size());
    }

    private CatalogAssetPortalService.AssetQuery queryOf(int page, int size) {
        return new CatalogAssetPortalService.AssetQuery(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            page,
            size
        );
    }
}
