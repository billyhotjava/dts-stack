package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private UUID hiddenAssetId;
    private boolean hiddenEveryPageFirst;
    private UUID linkedOmAssetId;
    private CatalogDataset linkedLegacyDataset;

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
        lenient().when(mappingRepository.findByNormalizedFqnIn(any())).thenReturn(List.of());
        // canRead 在 extension 为 null 时直接判不可见，所以可见的 OpenMetadata 资产必须配一个启用的扩展。
        lenient()
            .when(extensionRepository.findByOmAssetIn(any()))
            .thenAnswer(invocation -> {
                List<OpenMetadataAssetCache> batch = invocation.getArgument(0);
                return batch.stream().map(this::extensionFor).toList();
            });
    }

    /** 首个资产用于验证可见性拒绝，其余一律启用。 */
    private CatalogAssetExtension extensionFor(OpenMetadataAssetCache asset) {
        CatalogAssetExtension extension = enabledExtension(asset);
        if (hiddenAssetId != null && hiddenAssetId.equals(asset.getId())) {
            extension.setEnabled(false);
        }
        if (linkedOmAssetId != null && linkedOmAssetId.equals(asset.getId()) && linkedLegacyDataset != null) {
            extension.setLegacyDatasetId(linkedLegacyDataset.getId());
        }
        if (hiddenEveryPageFirst) {
            int index = openMetadataAssets.indexOf(asset);
            if (index >= 0 && index % 200 == 0) {
                extension.setEnabled(false);
            }
        }
        return extension;
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

    @Test
    void truncatedIsFalseWhenRowsFitWithinScanCap() {
        givenOpenMetadataAssets(2500);
        givenLegacyAssets(0);

        CatalogAssetOverviewAggregator.AssetOverview overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        assertThat(overview.scanned()).isEqualTo(2500);
        assertThat(overview.truncated()).isFalse();
    }

    @Test
    void truncatedBecomesTrueAtScanCap() {
        givenOpenMetadataAssets(CatalogAssetPortalService.ASSET_STATS_SCAN_CAP + 500);
        givenLegacyAssets(0);

        CatalogAssetOverviewAggregator.AssetOverview overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        assertThat(overview.scanned()).isEqualTo(CatalogAssetPortalService.ASSET_STATS_SCAN_CAP);
        assertThat(overview.truncated()).isTrue();
    }

    @Test
    void listingLoadsMetadataInBatchesInsteadOfPerRow() {
        givenOpenMetadataAssets(50);
        givenLegacyAssets(0);

        service.listAssets(queryOf(0, 50), ACTIVE_DEPT);

        // 逐行 findFirstByOmAsset / findFirstByFqnIgnoreCase 会随行数线性增长；
        // 批量路径对每页各查一次，行数再多也不增加查询次数。
        verify(extensionRepository, never()).findFirstByOmAsset(any());
        verify(mappingRepository, never()).findFirstByFqnIgnoreCase(any());
        verify(extensionRepository, times(1)).findByOmAssetIn(any());
        verify(mappingRepository, times(1)).findByNormalizedFqnIn(any());
    }

    /** 按 pageable 切片装配 OpenMetadata 侧的分页返回。 */
    private void givenOpenMetadataAssets(int count) {
        if (count == 0) {
            lenient()
                .when(assetRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenAnswer(invocation -> slice(openMetadataAssets, invocation.getArgument(1)));
            return;
        }
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
        if (count == 0) {
            lenient()
                .when(datasetRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenAnswer(invocation -> slice(legacyAssets, invocation.getArgument(1)));
            return;
        }
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
        hiddenAssetId = openMetadataAssets.get(0).getId();
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

    @Test
    void pagingNeverServesALegacyRowTwiceWhenOpenMetadataRowsAreHidden() {
        givenOpenMetadataAssets(4);
        givenLegacyAssets(2);
        givenFirstOpenMetadataAssetUnreadable();

        List<String> keys = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            service.listAssets(queryOf(page, 2), ACTIVE_DEPT).content().forEach(row -> keys.add(row.assetKey()));
        }

        // 3 个可见 OM + 2 个 legacy，一个不多一个不少
        assertThat(keys).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void overviewCountsMatchLedgerEnumerationWhenRowsAreHidden() {
        // G-75-01：概览计数必须与台账逐页枚举出的可见行数一致。
        // 必须跨越 overview 内部的 200 条/页边界，否则只有一页、走不到分页组合的重复路径。
        givenOpenMetadataAssetsWithOneHiddenPerPage(250);
        givenLegacyAssets(3);

        CatalogAssetOverviewAggregator.AssetOverview overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        Set<String> ledgerKeys = new LinkedHashSet<>();
        for (int page = 0; page < 10; page++) {
            service.listAssets(queryOf(page, 200), ACTIVE_DEPT).content().forEach(row -> ledgerKeys.add(row.assetKey()));
        }

        assertThat(overview.scanned()).isEqualTo(ledgerKeys.size());
        assertThat(overview.total()).isEqualTo(ledgerKeys.size());
    }

    @Test
    void truncatedIsTrueWhenPageBudgetRunsOutBeforeDataDoes() {
        // 每页因可见性隐藏 1 行 → 25 页只能扫到 25*199 行，达不到 5000 却已耗尽预算
        givenOpenMetadataAssetsWithOneHiddenPerPage(6000);
        givenLegacyAssets(0);

        CatalogAssetOverviewAggregator.AssetOverview overview = service.overview(queryOf(0, 200), ACTIVE_DEPT);

        assertThat(overview.scanned()).isLessThan(CatalogAssetPortalService.ASSET_STATS_SCAN_CAP);
        assertThat(overview.truncated()).isTrue();
    }

    /** 造出「每页都有一行被隐藏」的数据集：每 200 条中的第一条不可见。 */
    private void givenOpenMetadataAssetsWithOneHiddenPerPage(int count) {
        givenOpenMetadataAssets(count);
        hiddenEveryPageFirst = true;
    }

    @Test
    void hiddenLegacyRowsDoNotMakeLaterRowsUnreachable() {
        givenOpenMetadataAssets(0);
        givenLegacyAssets(4);
        givenFirstLegacyAssetUnreadable();

        List<String> keys = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            service.listAssets(queryOf(page, 2), ACTIVE_DEPT).content().forEach(row -> keys.add(row.assetKey()));
        }

        // 4 条中 1 条不可见，其余 3 条必须都能被翻到，一条不漏一条不重
        assertThat(keys).hasSize(3).doesNotHaveDuplicates();
    }

    /** 让首个 legacy 资产在可见性校验中被拒。 */
    private void givenFirstLegacyAssetUnreadable() {
        CatalogDataset hidden = legacyAssets.get(0);
        lenient().when(accessChecker.canRead(hidden)).thenReturn(false);
    }

    @Test
    void aLegacyDatasetLinkedToAnOpenMetadataAssetIsNotEmittedTwice() {
        // 这条路径此前从未被测过：所有测试的 visibleLegacyIds 都是空的。
        // CatalogAssetContractMapper 让 OM 行沿用 legacy 的 assetKey，两行在统计口径上是同一资产。
        // SQL 侧的 NOT EXISTS 是主防线（单元测试无法验证，Specification 不会真正执行），
        // 本测试锁定的是应用层这道网：即便数据库没排除，同一页内也不得发两次。
        givenOpenMetadataAssets(2);
        givenLegacyAssets(2);
        givenFirstOpenMetadataAssetLinkedToFirstLegacyDataset();

        List<String> keys = new ArrayList<>();
        service.listAssets(queryOf(0, 10), ACTIVE_DEPT).content().forEach(row -> keys.add(row.assetKey()));

        assertThat(keys).doesNotHaveDuplicates();
        // 2 个 OM（其一即 legacy0 的另一副面孔）+ 1 个独立 legacy = 3 行
        assertThat(keys).hasSize(3);
    }

    /** 把首个 OM 资产关联到首个 legacy 数据集，制造「同一资产两副面孔」的局面。 */
    private void givenFirstOpenMetadataAssetLinkedToFirstLegacyDataset() {
        linkedOmAssetId = openMetadataAssets.get(0).getId();
        linkedLegacyDataset = legacyAssets.get(0);
        lenient()
            .when(datasetRepository.findAllById(any()))
            .thenAnswer(invocation -> {
                Iterable<UUID> ids = invocation.getArgument(0);
                List<CatalogDataset> found = new ArrayList<>();
                for (UUID id : ids) {
                    if (linkedLegacyDataset != null && linkedLegacyDataset.getId().equals(id)) {
                        found.add(linkedLegacyDataset);
                    }
                }
                return found;
            });
    }

    @Test
    void totalIsIdenticalOnEveryPageWhenSomeRowsAreHidden() {
        // 现场 bug：台账筛选后第 1 页显示 6 页、翻到第 4 页显示 5 页、再看又变 4 页，
        // 中间还夹着空页。根因是 total 逐页重算隐藏行，每页得到不同的值。
        givenOpenMetadataAssets(45);
        givenLegacyAssets(4);
        givenFirstOpenMetadataAssetUnreadable();

        Set<Long> totalsSeen = new LinkedHashSet<>();
        for (int page = 0; page < 6; page++) {
            totalsSeen.add(service.listAssets(queryOf(page, 10), ACTIVE_DEPT).total());
        }

        assertThat(totalsSeen).as("同一查询在任何页码下都必须给出同一个 total").hasSize(1);
    }

    @Test
    void everyRowIsReachableAcrossPagesWithoutEmptyGaps() {
        givenOpenMetadataAssets(45);
        givenLegacyAssets(4);
        givenFirstOpenMetadataAssetUnreadable();

        long total = service.listAssets(queryOf(0, 10), ACTIVE_DEPT).total();
        int pageCount = (int) Math.ceil(total / 10.0);
        Set<String> keys = new LinkedHashSet<>();
        int emptyPagesBeforeLast = 0;
        for (int page = 0; page < pageCount; page++) {
            var content = service.listAssets(queryOf(page, 10), ACTIVE_DEPT).content();
            if (content.isEmpty() && page < pageCount - 1) {
                emptyPagesBeforeLast++;
            }
            content.forEach(row -> keys.add(row.assetKey()));
        }

        assertThat(emptyPagesBeforeLast).as("分页器声称的页数之内不应出现空页").isZero();
        assertThat(keys).as("44 个可见 OM + 4 个 legacy 都必须能翻到").hasSize(48);
    }
}
