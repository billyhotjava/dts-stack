package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetDirectoryReadAdapter.OwnerAsset;
import com.yuzhi.dts.platform.service.catalog.CatalogUnifiedAssetDirectoryService.UnifiedAssetPage;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogUnifiedAssetDirectoryServiceTest {

    private CatalogAssetPortalService datasetService;
    private CatalogAssetDirectoryReadAdapter readAdapter;
    private CatalogAssetTagService tagService;
    private CatalogAssetDirectoryVisibilityPolicy visibilityPolicy;
    private CatalogUnifiedAssetDirectoryService service;

    @BeforeEach
    void setUp() {
        datasetService = mock(CatalogAssetPortalService.class);
        readAdapter = mock(CatalogAssetDirectoryReadAdapter.class);
        tagService = mock(CatalogAssetTagService.class);
        visibilityPolicy = mock(CatalogAssetDirectoryVisibilityPolicy.class);
        service = new CatalogUnifiedAssetDirectoryService(
            datasetService,
            readAdapter,
            tagService,
            visibilityPolicy
        );
        when(visibilityPolicy.canRead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        when(tagService.listAssetTags(anyList())).thenReturn(Map.of());
        when(readAdapter.loadRelationships(anyList())).thenReturn(Map.of());
    }

    @Test
    void combinesExistingOwnerAssetsUnderCanonicalIdentityWithoutMintingAnotherAssetId() {
        UUID datasetId = UUID.randomUUID();
        CatalogAssetPortalService.AssetSummary dataset = new CatalogAssetPortalService.AssetSummary(
            datasetId,
            null,
            "dwd.project_task",
            "POSTGRESQL",
            "project-db",
            "dwd",
            "dwd",
            "project_task",
            "项目任务明细",
            "INTERNAL",
            "DWD",
            "D01",
            "xiezm",
            null,
            "ACTIVE",
            "项目任务明细",
            "GOVERNED",
            "DTS_NATIVE",
            null,
            datasetId,
            null,
            12,
            "SYNCED",
            null,
            Instant.parse("2026-08-21T01:00:00Z"),
            "dts-catalog",
            "DATASET",
            "source:unknown/schema:dwd/table:project_task",
            List.of()
        );
        when(datasetService.listAssets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(
                new CatalogAssetPortalService.AssetPage(List.of(dataset), 1, 0, 200, 1, "dts-catalog"),
                new CatalogAssetPortalService.AssetPage(List.of(), 1, 1, 200, 0, "dts-catalog")
            );

        OwnerAsset model = new OwnerAsset(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:10000000-0000-0000-0000-000000000001",
            "项目主题模型",
            "项目主题模型",
            "FACT",
            "数据建模",
            null,
            "INTERNAL",
            "DWD",
            "xiezm",
            "D01",
            "PUBLISHED",
            "GOVERNED",
            "UNKNOWN",
            Instant.parse("2026-08-21T02:00:00Z"),
            "/modeling/models/10000000-0000-0000-0000-000000000001"
        );
        OwnerAsset indicator = new OwnerAsset(
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            CatalogAssetType.GOV_INDICATOR,
            "tenant:default/env:prod/dialect:generic/gov_indicator:project_completion_rate",
            "项目完成率",
            "已完成项目占比",
            "DERIVED",
            "指标管理",
            null,
            "INTERNAL",
            null,
            "xiezm",
            "D01",
            "PUBLISHED",
            "GOVERNED",
            "PASSED",
            Instant.parse("2026-08-21T03:00:00Z"),
            "/governance/indicators/dictionary"
        );
        when(readAdapter.load(Set.of(
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetType.GOV_INDICATOR,
            CatalogAssetType.BI_DATASET,
            CatalogAssetType.SCREEN,
            CatalogAssetType.DATA_PRODUCT,
            CatalogAssetType.API_SERVICE
        ))).thenReturn(List.of(model, indicator));

        UnifiedAssetPage page = service.list(
            CatalogAssetPortalService.AssetQuery.unscoped(),
            "ALL",
            null
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.content()).extracting(item -> item.assetType().name())
            .containsExactly("GOV_INDICATOR", "SEMANTIC_MODEL", "DATASET");
        assertThat(page.content()).allSatisfy(item -> {
            assertThat(item.assetKey()).isNotBlank();
            assertThat(item.catalogIdentity()).isEqualTo(item.assetType().name() + "\u0000" + item.assetKey());
        });
        assertThat(page.content().get(1).qualityStatus()).isEqualTo("UNKNOWN");
        assertThat(page.content().get(1).type()).isNull();
        assertThat(page.content().get(1).subtype()).isEqualTo("FACT");
        verify(tagService).listAssetTags(anyList());
        verify(readAdapter).loadRelationships(anyList());
    }

    @Test
    void familySpecificQueryDoesNotScanDatasets() {
        OwnerAsset model = new OwnerAsset(
            UUID.randomUUID(),
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:model-1",
            "模型一",
            null,
            "FACT",
            "数据建模",
            null,
            "INTERNAL",
            "DWD",
            null,
            null,
            "DRAFT",
            "INCOMPLETE",
            "UNKNOWN",
            Instant.now(),
            "/modeling/models/model-1"
        );
        when(readAdapter.load(Set.of(CatalogAssetType.SEMANTIC_MODEL))).thenReturn(List.of(model));

        UnifiedAssetPage page = service.list(
            CatalogAssetPortalService.AssetQuery.unscoped(),
            "SEMANTIC_MODEL",
            null
        );

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.content().getFirst().assetType()).isEqualTo(CatalogAssetType.SEMANTIC_MODEL);
        verify(datasetService, never()).listAssets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appliesBusinessTagsAgainstEachCanonicalAssetFamilyBeforePaging() {
        UUID tagId = UUID.randomUUID();
        OwnerAsset first = owner("模型一", "10000000-0000-0000-0000-000000000011");
        OwnerAsset tagged = owner("模型二", "10000000-0000-0000-0000-000000000012");
        when(readAdapter.load(Set.of(CatalogAssetType.SEMANTIC_MODEL))).thenReturn(List.of(first, tagged));
        when(
            tagService.findMatchingAssetKeysWithin(
                org.mockito.ArgumentMatchers.eq("SEMANTIC_MODEL"),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(List.of(tagId))
            )
        ).thenReturn(Set.of(tagged.assetKey()));

        UnifiedAssetPage page = service.list(queryWithTags(tagId), "SEMANTIC_MODEL", null);

        assertThat(page.content()).extracting(item -> item.displayName()).containsExactly("模型二");
    }

    @Test
    void rejectsAnUnboundedOwnerCandidateSetBeforeHydration() {
        List<OwnerAsset> candidates = java.util.stream.IntStream
            .range(0, 5_001)
            .mapToObj(index -> owner("模型" + index, UUID.nameUUIDFromBytes(("model-" + index).getBytes()).toString()))
            .toList();
        when(readAdapter.load(Set.of(CatalogAssetType.SEMANTIC_MODEL))).thenReturn(candidates);

        assertThatThrownBy(() -> service.list(CatalogAssetPortalService.AssetQuery.unscoped(), "SEMANTIC_MODEL", null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("候选超过 5000");

        verify(visibilityPolicy, never()).canRead(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
        verify(tagService, never()).listAssetTags(anyList());
    }

    private OwnerAsset owner(String name, String idValue) {
        UUID id = UUID.fromString(idValue);
        return new OwnerAsset(
            id,
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetKey.semanticModel(id.toString()),
            name,
            null,
            "FACT",
            "数据建模",
            null,
            "INTERNAL",
            "DWD",
            "xiezm",
            "D01",
            "PUBLISHED",
            "GOVERNED",
            "UNKNOWN",
            Instant.now(),
            "/modeling/models/" + id
        );
    }

    private CatalogAssetPortalService.AssetQuery queryWithTags(UUID tagId) {
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
            List.of(tagId),
            0,
            10,
            null,
            null,
            null,
            null,
            null
        );
    }
}
