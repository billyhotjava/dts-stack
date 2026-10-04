package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CatalogSearchResourceTagFilterTest {

    @Mock
    private CatalogDatasetRepository datasetRepo;

    @Mock
    private CatalogTableSchemaRepository tableRepo;

    @Mock
    private CatalogColumnSchemaRepository columnRepo;

    @Mock
    private CatalogAssetTagRepository assetTagRepository;

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private AuditService audit;

    @Mock
    private CatalogAssetTagService assetTagService;

    private CatalogSearchResource resource;

    @BeforeEach
    void setUp() {
        resource = new CatalogSearchResource(
            datasetRepo,
            tableRepo,
            columnRepo,
            assetTagRepository,
            accessChecker,
            audit,
            assetTagService
        );
        lenient()
            .when(
                assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                    any(),
                    anyLong(),
                    anyString(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                    any(Pageable.class)
                )
            )
            .thenReturn(new SliceImpl<>(List.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tagOnlySearchFiltersDatasetScopeBeforeVisibilityAndPropagatesStructuredTags() {
        UUID tagId = UUID.randomUUID();
        CatalogDataset matched = dataset("orders", "legacy-dataset-tag");
        CatalogDataset unmatched = dataset("customers", "legacy-unmatched-tag");
        CatalogDataset denied = dataset("payroll", "legacy-denied-tag");
        String matchedKey = CatalogAssetKey.dataset(matched);
        String deniedKey = CatalogAssetKey.dataset(denied);
        CatalogTagDto structuredTag = tag(tagId);
        CatalogTableSchema table = table(matched, "orders_table", "legacy-table-tag");
        CatalogColumnSchema column = column(table, "order_id", "legacy-column-tag");

        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 200)
            )
        )
            .thenReturn(new SliceImpl<>(List.of(candidate(matched, matched.getId()), candidate(denied, denied.getId()))));
        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "TABLE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 200)
            )
        )
            .thenReturn(new SliceImpl<>(List.of(candidate(matched, table.getId()))));
        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "COLUMN",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 200)
            )
        )
            .thenReturn(new SliceImpl<>(List.of(candidate(matched, column.getId()))));
        when(datasetRepo.findAllById(any())).thenReturn(List.of(matched, denied));
        when(tableRepo.findAllById(any())).thenReturn(List.of(table));
        when(columnRepo.findAllById(any())).thenReturn(List.of(column));
        when(accessChecker.canRead(matched)).thenReturn(true);
        when(accessChecker.canRead(denied)).thenReturn(false);
        AssetRef matchedRef = new AssetRef("DATASET", matchedKey);
        when(assetTagService.listAssetTags(List.of(matchedRef))).thenReturn(Map.of(matchedRef, List.of(structuredTag)));

        ApiResponse<Map<String, Object>> response = search(null, List.of(tagId, tagId));

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) response.getData().get("datasets");
        List<Map<String, Object>> tables = (List<Map<String, Object>>) response.getData().get("tables");
        List<Map<String, Object>> columns = (List<Map<String, Object>>) response.getData().get("columns");
        assertThat(datasets).singleElement().satisfies(dto -> {
            assertThat(dto).containsEntry("tags", "legacy-dataset-tag");
            assertThat(dto).containsEntry("assetType", "DATASET");
            assertThat(dto).containsEntry("assetKey", matchedKey);
            assertThat(dto.get("assetTags")).isEqualTo(List.of(structuredTag));
        });
        assertThat(tables).singleElement().satisfies(dto -> {
            assertThat(dto).containsEntry("tags", "legacy-table-tag");
            assertThat(dto).containsEntry("datasetAssetKey", matchedKey);
            assertThat(dto.get("datasetAssetTags")).isEqualTo(List.of(structuredTag));
        });
        assertThat(columns).singleElement().satisfies(dto -> {
            assertThat(dto).containsEntry("tags", "legacy-column-tag");
            assertThat(dto).containsEntry("datasetAssetKey", matchedKey);
            assertThat(dto.get("datasetAssetTags")).isEqualTo(List.of(structuredTag));
        });
        verify(accessChecker, never()).canRead(unmatched);
    }

    @Test
    void tagOnlySearchDoesNotMaterializeAllTagKeysOrDatasetScope() {
        UUID tagId = UUID.randomUUID();
        CatalogDataset dataset = dataset("orders", "legacy-dataset-tag");
        String assetKey = CatalogAssetKey.dataset(dataset);
        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 200)
            )
        )
            .thenReturn(new SliceImpl<>(List.of(candidate(dataset, dataset.getId()))));
        when(datasetRepo.findAllById(any())).thenReturn(List.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        AssetRef ref = new AssetRef("DATASET", assetKey);
        when(assetTagService.listAssetTags(List.of(ref))).thenReturn(Map.of(ref, List.of()));

        search(null, List.of(tagId));

        verify(assetTagService, never())
            .findMatchingAssetKeysWithin(any(), any(), any());
        verify(datasetRepo, never()).findAll(any(Specification.class), any(Sort.class));
        verify(tableRepo, never()).findAll();
        verify(columnRepo, never()).findAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tagOnlySearchContinuesPastInvisibleCandidatePageUntilLimitIsFilled() {
        UUID tagId = UUID.randomUUID();
        CatalogDataset denied = dataset("payroll", "restricted");
        CatalogDataset firstVisible = dataset("orders", "trusted");
        CatalogDataset secondVisible = dataset("customers", "trusted");
        PageRequest firstPage = PageRequest.of(0, 200);
        PageRequest secondPage = PageRequest.of(1, 200);
        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                firstPage
            )
        )
            .thenReturn(new SliceImpl<>(List.of(candidate(denied, denied.getId())), firstPage, true));
        when(
            assetTagRepository.findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                secondPage
            )
        )
            .thenReturn(
                new SliceImpl<>(
                    List.of(candidate(firstVisible, firstVisible.getId()), candidate(secondVisible, secondVisible.getId())),
                    secondPage,
                    false
                )
            );
        when(datasetRepo.findAllById(List.of(denied.getId()))).thenReturn(List.of(denied));
        when(datasetRepo.findAllById(List.of(firstVisible.getId(), secondVisible.getId())))
            .thenReturn(List.of(firstVisible, secondVisible));
        when(accessChecker.canRead(denied)).thenReturn(false);
        when(accessChecker.canRead(firstVisible)).thenReturn(true);
        when(accessChecker.canRead(secondVisible)).thenReturn(true);
        AssetRef firstRef = new AssetRef("DATASET", CatalogAssetKey.dataset(firstVisible));
        AssetRef secondRef = new AssetRef("DATASET", CatalogAssetKey.dataset(secondVisible));
        when(assetTagService.listAssetTags(List.of(firstRef, secondRef))).thenReturn(Map.of(firstRef, List.of(), secondRef, List.of()));

        ApiResponse<Map<String, Object>> response = resource.search(
            null,
            "DATASET",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            List.of(tagId),
            2,
            null
        );

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) response.getData().get("datasets");
        assertThat(datasets).extracting(row -> row.get("name")).containsExactly("orders", "customers");
        verify(assetTagRepository)
            .findCatalogSearchCandidatesHavingAllTags(
                List.of(tagId),
                1,
                "DATASET",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                secondPage
            );
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyTagsRemainPartOfKeywordSearchWhenExactTagFilterIsEmpty() {
        CatalogDataset dataset = dataset("orders", "legacy-risk-label");
        String assetKey = CatalogAssetKey.dataset(dataset);
        AssetRef ref = new AssetRef("DATASET", assetKey);
        when(datasetRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(tableRepo.findAll()).thenReturn(List.of());
        when(columnRepo.findAll()).thenReturn(List.of());
        when(assetTagService.listAssetTags(List.of(ref))).thenReturn(Map.of(ref, List.of()));

        ApiResponse<Map<String, Object>> response = search("risk-label", List.of());

        List<Map<String, Object>> datasets = (List<Map<String, Object>>) response.getData().get("datasets");
        assertThat(datasets).singleElement().satisfies(dto -> assertThat(dto).containsEntry("tags", "legacy-risk-label"));
        verify(assetTagService, never())
            .findMatchingAssetKeysWithin(any(), any(), any());
    }

    @Test
    void repeatedTagIdsBindAndBlankKeywordSupportsTagOnlySearch() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(resource).build();

        mockMvc
            .perform(get("/api/catalog/search").param("tagIds", first.toString(), second.toString(), first.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void invalidTagIdReturnsBadRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(resource).build();

        mockMvc.perform(get("/api/catalog/search").param("tagIds", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    private ApiResponse<Map<String, Object>> search(String keyword, List<UUID> tagIds) {
        return resource.search(
            keyword,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true,
            tagIds,
            50,
            null
        );
    }

    private CatalogDataset dataset(String table, String tags) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSourceId(UUID.randomUUID());
        dataset.setName(table);
        dataset.setHiveDatabase("dwd");
        dataset.setHiveTable(table);
        dataset.setTags(tags);
        dataset.setEnabled(true);
        return dataset;
    }

    private CatalogTableSchema table(CatalogDataset dataset, String name, String tags) {
        CatalogTableSchema table = new CatalogTableSchema();
        table.setId(UUID.randomUUID());
        table.setDataset(dataset);
        table.setName(name);
        table.setTags(tags);
        return table;
    }

    private CatalogColumnSchema column(CatalogTableSchema table, String name, String tags) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setId(UUID.randomUUID());
        column.setTable(table);
        column.setName(name);
        column.setDataType("bigint");
        column.setTags(tags);
        return column;
    }

    private CatalogTagDto tag(UUID id) {
        return new CatalogTagDto(id, UUID.randomUUID(), "QUALITY", "高可信", "#1677ff", false, true, null, 3);
    }

    private CatalogAssetTagRepository.CatalogSearchCandidateProjection candidate(CatalogDataset dataset, UUID entityId) {
        return new SearchCandidate(dataset.getId(), entityId, CatalogAssetKey.dataset(dataset));
    }

    private record SearchCandidate(UUID datasetId, UUID entityId, String assetKey)
        implements CatalogAssetTagRepository.CatalogSearchCandidateProjection {
        @Override
        public UUID getDatasetId() {
            return datasetId;
        }

        @Override
        public UUID getEntityId() {
            return entityId;
        }

        @Override
        public String getAssetKey() {
            return assetKey;
        }
    }
}
