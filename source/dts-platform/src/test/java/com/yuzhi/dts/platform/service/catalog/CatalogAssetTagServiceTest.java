package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRefPage;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogAssetTagServiceTest {

    @Mock
    private CatalogTagRepository tagRepository;

    @Mock
    private CatalogAssetTagRepository assetTagRepository;

    @Mock
    private CatalogAssetTagBatchWriter assetTagBatchWriter;

    private CatalogAssetTagService service;

    @BeforeEach
    void setUp() {
        service = new CatalogAssetTagService(tagRepository, assetTagRepository, assetTagBatchWriter);
    }

    @Test
    void tagsMetricAssetAndRepeatingTheRequestIsIdempotent() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(enabledTag(tagId)));
        when(
            assetTagRepository.insertIgnore(
                any(),
                eq(tagId),
                eq("METRIC"),
                eq("metric:core/revenue"),
                eq("alice"),
                any(),
                isNull(),
                any()
            )
        )
            .thenReturn(1, 0);

        var first = service.tagAsset("metric", "metric:core/revenue", List.of(tagId), "alice");
        var second = service.tagAsset("METRIC", "metric:core/revenue", List.of(tagId), "alice");

        assertThat(first.created()).isEqualTo(1);
        assertThat(first.skipped()).isZero();
        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        verify(assetTagRepository, times(2))
            .insertIgnore(
                any(),
                eq(tagId),
                eq("METRIC"),
                eq("metric:core/revenue"),
                eq("alice"),
                any(),
                isNull(),
                any()
            );
    }

    @Test
    void concurrentUniqueCollisionIsReportedAsSkippedInsteadOfEscapingAsServerError() {
        UUID tagId = UUID.randomUUID();
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(enabledTag(tagId)));
        when(
            assetTagRepository.insertIgnore(
                any(),
                eq(tagId),
                eq("METRIC"),
                eq("metric:core/revenue"),
                eq("alice"),
                any(),
                isNull(),
                any()
            )
        )
            .thenReturn(0);

        var result = service.tagAsset("METRIC", "metric:core/revenue", List.of(tagId), "alice");

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void rejectsDisabledTagWithoutWritingAnAssignment() {
        UUID tagId = UUID.randomUUID();
        CatalogTag tag = enabledTag(tagId);
        tag.setEnabled(false);
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(tag));

        assertThatThrownBy(() -> service.tagAsset("DATA_PRODUCT", "tenant:default/data-product:orders", List.of(tagId), "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("已停用");
        verify(assetTagRepository, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidAssetTypeAsBadRequest() {
        assertThatThrownBy(() -> service.tagAsset("not-an-asset", "asset:key", List.of(UUID.randomUUID()), "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("资产类型");
    }

    @Test
    void singleAssetRejectsMoreThanOneHundredTagIdsBeforeRepositoryAccess() {
        List<UUID> tagIds = rawTagIdsOverLimit();

        assertThatThrownBy(() -> service.tagAsset("METRIC", "metric:core/revenue", tagIds, "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("100");
        verifyNoInteractions(tagRepository, assetTagRepository);
    }

    @Test
    void singleAssetUntagRejectsMoreThanOneHundredTagIdsBeforeRepositoryAccess() {
        List<UUID> tagIds = rawTagIdsOverLimit();

        assertThatThrownBy(() -> service.untagAsset("METRIC", "metric:core/revenue", tagIds))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("100");
        verifyNoInteractions(tagRepository, assetTagRepository);
    }

    @Test
    void batchRejectsMoreThanOneHundredTagIdsBeforeRepositoryAccess() {
        List<UUID> tagIds = rawTagIdsOverLimit();
        BatchAssetTagRequest request = new BatchAssetTagRequest(
            List.of(new AssetRef("METRIC", "metric:core/revenue")),
            tagIds
        );

        assertThatThrownBy(() -> service.batchTag(request, "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("100");
        verifyNoInteractions(tagRepository, assetTagRepository);
    }

    @Test
    void tagPreflightDeduplicatesAndLoadsAllDefinitionsOnce() {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        when(tagRepository.findAllById(List.of(firstTagId, secondTagId)))
            .thenReturn(List.of(enabledTag(secondTagId), enabledTag(firstTagId)));

        var result = service.tagAsset(
            "METRIC",
            "metric:core/revenue",
            List.of(firstTagId, secondTagId, firstTagId),
            "alice"
        );

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        verify(tagRepository, times(1)).findAllById(List.of(firstTagId, secondTagId));
        verify(tagRepository, never()).findById(any());
        var assignments = inOrder(assetTagRepository);
        assignments
            .verify(assetTagRepository)
            .insertIgnore(any(), eq(firstTagId), any(), any(), any(), any(), any(), any());
        assignments
            .verify(assetTagRepository)
            .insertIgnore(any(), eq(secondTagId), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingTagStillReturnsNotFoundBeforeWritingAnyAssignment() {
        UUID missingTagId = UUID.randomUUID();
        UUID existingTagId = UUID.randomUUID();
        when(tagRepository.findAllById(List.of(missingTagId, existingTagId)))
            .thenReturn(List.of(enabledTag(existingTagId)));

        assertThatThrownBy(() ->
                service.tagAsset(
                    "METRIC",
                    "metric:core/revenue",
                    List.of(missingTagId, existingTagId),
                    "alice"
                )
            )
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getReason()).contains("标签不存在", missingTagId.toString());
            });
        verify(assetTagRepository, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchPreflightRejectsAnyInvalidAssetBeforeWritingAndCapsAtFiveHundred() {
        UUID tagId = UUID.randomUUID();
        BatchAssetTagRequest invalid = new BatchAssetTagRequest(
            List.of(new AssetRef("METRIC", "metric:core/revenue"), new AssetRef("invalid", "bad:key")),
            List.of(tagId)
        );

        assertThatThrownBy(() -> service.batchTag(invalid, "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("资产类型");
        verify(assetTagRepository, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any(), any());

        List<AssetRef> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(new AssetRef("METRIC", "metric:core/m" + i));
        }
        assertThatThrownBy(() -> service.batchTag(new BatchAssetTagRequest(tooMany, List.of(tagId)), "alice"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("500");
    }

    @Test
    void batchAtMaximumBoundsDelegatesFiftyThousandAssignmentsToOneChunkedWriterCall() {
        List<UUID> tagIds = java.util.stream.IntStream
            .range(0, 100)
            .mapToObj(index -> UUID.randomUUID())
            .toList();
        List<CatalogTag> tags = tagIds.stream().map(this::enabledTag).toList();
        List<AssetRef> assets = java.util.stream.IntStream
            .range(0, 500)
            .mapToObj(index -> new AssetRef("METRIC", "metric:core/m" + index))
            .toList();
        when(tagRepository.findAllById(tagIds)).thenReturn(tags);

        var result = service.batchTag(new BatchAssetTagRequest(assets, tagIds), "alice");

        assertThat(result.assetCount()).isEqualTo(500);
        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(50_000);
        verify(tagRepository, times(1)).findAllById(tagIds);
        verify(assetTagBatchWriter)
            .insertIgnore(
                org.mockito.ArgumentMatchers.argThat(assignments -> assignments.size() == 50_000),
                eq("alice"),
                any()
            );
        verify(assetTagRepository, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchResultKeepsInputOrderAndReportsCreatedAndSkippedPerAsset() {
        UUID tagId = UUID.randomUUID();
        AssetRef first = new AssetRef("METRIC", "metric:core/revenue");
        AssetRef second = new AssetRef("DATA_PRODUCT", "tenant:default/data-product:orders");
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(enabledTag(tagId)));
        when(
            assetTagBatchWriter.insertIgnore(
                any(),
                eq("alice"),
                any()
            )
        )
            .thenReturn(
                Set.of(new CatalogAssetTagBatchWriter.AssignmentKey(second.assetType(), second.assetKey(), tagId))
            );

        var result = service.batchTag(new BatchAssetTagRequest(List.of(first, second), List.of(tagId)), "alice");
        JsonNode json = new ObjectMapper().valueToTree(result);

        assertThat(json.path("assetCount").asInt()).isEqualTo(2);
        assertThat(json.path("created").asInt()).isEqualTo(1);
        assertThat(json.path("skipped").asInt()).isEqualTo(1);
        assertThat(json.path("results")).hasSize(2);
        assertThat(json.path("results").get(0).path("assetType").asText()).isEqualTo("METRIC");
        assertThat(json.path("results").get(0).path("assetKey").asText()).isEqualTo(first.assetKey());
        assertThat(json.path("results").get(0).path("created").asInt()).isZero();
        assertThat(json.path("results").get(0).path("skipped").asInt()).isEqualTo(1);
        assertThat(json.path("results").get(1).path("assetType").asText()).isEqualTo("DATA_PRODUCT");
        assertThat(json.path("results").get(1).path("assetKey").asText()).isEqualTo(second.assetKey());
        assertThat(json.path("results").get(1).path("created").asInt()).isEqualTo(1);
        assertThat(json.path("results").get(1).path("skipped").asInt()).isZero();
        verify(assetTagBatchWriter)
            .insertIgnore(
                eq(
                    List.of(
                        new CatalogAssetTagBatchWriter.AssignmentKey(first.assetType(), first.assetKey(), tagId),
                        new CatalogAssetTagBatchWriter.AssignmentKey(second.assetType(), second.assetKey(), tagId)
                    )
                ),
                eq("alice"),
                any()
            );
        verify(assetTagRepository, never()).insertIgnore(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void batchDuplicateAssetsAttributesCreatedAssignmentsToTheFirstOccurrenceOnly() {
        UUID tagId = UUID.randomUUID();
        AssetRef asset = new AssetRef("METRIC", "metric:core/revenue");
        CatalogAssetTagBatchWriter.AssignmentKey assignment = new CatalogAssetTagBatchWriter.AssignmentKey(
            asset.assetType(),
            asset.assetKey(),
            tagId
        );
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(enabledTag(tagId)));
        when(
            assetTagBatchWriter.insertIgnore(
                eq(List.of(assignment)),
                eq("alice"),
                org.mockito.ArgumentMatchers.any()
            )
        )
            .thenReturn(Set.of(assignment));

        var result = service.batchTag(new BatchAssetTagRequest(List.of(asset, asset), List.of(tagId)), "alice");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.results())
            .extracting(BatchAssetTagResult.AssetResult::created, BatchAssetTagResult.AssetResult::skipped)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 0), org.assertj.core.groups.Tuple.tuple(0, 1));
        verify(assetTagBatchWriter)
            .insertIgnore(eq(List.of(assignment)), eq("alice"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listAssetTagsLoadsTagsOnceAndRestoresAssignmentOrder() {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        UUID missingTagId = UUID.randomUUID();
        CatalogTag firstTag = enabledTag(firstTagId);
        firstTag.setCode("FIRST");
        CatalogTag secondTag = enabledTag(secondTagId);
        secondTag.setCode("SECOND");
        when(
            assetTagRepository.findByAssetTypeAndAssetKeyInOrderByTaggedAtAsc(
                "METRIC",
                List.of("metric:core/revenue")
            )
        )
            .thenReturn(
                List.of(
                    assignment(secondTagId),
                    assignment(missingTagId),
                    assignment(firstTagId)
                )
            );
        when(tagRepository.findAllById(List.of(secondTagId, missingTagId, firstTagId)))
            .thenReturn(List.of(firstTag, secondTag));

        var result = service.listAssetTags("metric", "metric:core/revenue");

        assertThat(result).extracting(tag -> tag.code()).containsExactly("SECOND", "FIRST");
        verify(tagRepository, times(1)).findAllById(List.of(secondTagId, missingTagId, firstTagId));
        verify(tagRepository, never()).findById(any());
    }

    @Test
    void bulkHydrationKeepsReferenceAndAssignmentOrderAndLoadsDefinitionsAndUsageOnce() {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        AssetRef metric = new AssetRef("METRIC", "metric:core/revenue");
        AssetRef dataset = new AssetRef("DATASET", "dataset:orders");
        CatalogTag firstTag = enabledTag(firstTagId);
        firstTag.setCode("FIRST");
        CatalogTag secondTag = enabledTag(secondTagId);
        secondTag.setCode("SECOND");
        when(
            assetTagRepository.findByAssetTypeAndAssetKeyInOrderByTaggedAtAsc(
                "METRIC",
                List.of(metric.assetKey())
            )
        )
            .thenReturn(List.of(assignment(metric, secondTagId), assignment(metric, firstTagId)));
        when(
            assetTagRepository.findByAssetTypeAndAssetKeyInOrderByTaggedAtAsc(
                "DATASET",
                List.of(dataset.assetKey())
            )
        )
            .thenReturn(List.of(assignment(dataset, firstTagId)));
        when(tagRepository.findAllById(List.of(secondTagId, firstTagId))).thenReturn(List.of(firstTag, secondTag));
        when(assetTagRepository.countGroupedByTagId(List.of(secondTagId, firstTagId)))
            .thenReturn(List.of(usageCount(firstTagId, 9), usageCount(secondTagId, 2)));

        Map<AssetRef, List<com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto>> result = service.listAssetTags(
            List.of(metric, dataset, metric)
        );

        assertThat(result.keySet()).containsExactly(metric, dataset);
        assertThat(result.get(metric))
            .extracting(tag -> Map.entry(tag.code(), tag.usageCount()))
            .containsExactly(Map.entry("SECOND", 2L), Map.entry("FIRST", 9L));
        assertThat(result.get(dataset))
            .extracting(tag -> tag.code())
            .containsExactly("FIRST");
        verify(tagRepository).findAllById(List.of(secondTagId, firstTagId));
        verify(assetTagRepository).countGroupedByTagId(List.of(secondTagId, firstTagId));
    }

    @Test
    void exactSearchDeduplicatesTagsAndPaginatesStableCrossTypeResults() {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        when(
            assetTagRepository.findAssetRefsHavingAllTags(
                List.of(firstTagId, secondTagId),
                2,
                null,
                PageRequest.of(0, 500)
            )
        )
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        projection("DATASET", "dataset:customers"),
                        projection("DATASET", "dataset:orders"),
                        projection("METRIC", "metric:core/revenue")
                    ),
                    PageRequest.of(0, 500),
                    3
                )
            );

        AssetRefPage result = service.searchAssets(
            List.of(firstTagId, secondTagId, firstTagId),
            null,
            1,
            1,
            java.util.function.Function.identity()
        );

        assertThat(result.content()).containsExactly(new AssetRef("DATASET", "dataset:orders"));
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);

        AssetRefPage beyondLastPage = service.searchAssets(
            List.of(firstTagId, secondTagId),
            null,
            Integer.MAX_VALUE,
            100,
            java.util.function.Function.identity()
        );
        assertThat(beyondLastPage.content()).isEmpty();
        assertThat(beyondLastPage.total()).isEqualTo(3);
    }

    @Test
    void exactSearchRejectsMoreThanFiftyDistinctTagsBeforeQuerying() {
        List<UUID> tagIds = java.util.stream.IntStream
            .range(0, 51)
            .mapToObj(index -> UUID.randomUUID())
            .toList();

        assertThatThrownBy(() -> service.searchAssets(tagIds, null, 0, 10, java.util.function.Function.identity()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("50");
        verify(assetTagRepository, never()).findAssetRefsHavingAllTags(any(), any(Long.class), any(), any());
    }

    @Test
    void datasetMatchingKeysUseAndSemanticsWithDistinctTagCount() {
        UUID tagId = UUID.randomUUID();
        List<String> candidates = List.of(
            "dataset:customers",
            "dataset:orders"
        );
        when(
            assetTagRepository.findAssetKeysHavingAllTagsWithin(
                "DATASET",
                candidates,
                List.of(tagId),
                1
            )
        )
            .thenReturn(List.of("dataset:orders"));

        assertThat(
            service.findMatchingAssetKeysWithin(
                "dataset",
                candidates,
                List.of(tagId, tagId)
            )
        )
            .containsExactly("dataset:orders");
    }

    @Test
    void datasetMatchingKeysRejectsMoreThanFiveThousandCandidateKeys() {
        UUID tagId = UUID.randomUUID();
        List<String> candidates = java.util.stream.IntStream
            .range(0, 5_001)
            .mapToObj(index -> "dataset:item-" + index)
            .toList();

        assertThatThrownBy(() ->
                service.findMatchingAssetKeysWithin(
                    "DATASET",
                    candidates,
                    List.of(tagId)
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("5000");
        verify(assetTagRepository, never())
            .findAssetKeysHavingAllTagsWithin(any(), any(), any(), any(Long.class));
    }

    private CatalogAssetTag assignment(UUID tagId) {
        CatalogAssetTag assignment = new CatalogAssetTag();
        assignment.setTagId(tagId);
        assignment.setAssetType("METRIC");
        assignment.setAssetKey("metric:core/revenue");
        return assignment;
    }

    private CatalogAssetTag assignment(AssetRef ref, UUID tagId) {
        CatalogAssetTag assignment = new CatalogAssetTag();
        assignment.setTagId(tagId);
        assignment.setAssetType(ref.assetType());
        assignment.setAssetKey(ref.assetKey());
        return assignment;
    }

    private CatalogAssetTagRepository.TagUsageCount usageCount(UUID tagId, long count) {
        return new CatalogAssetTagRepository.TagUsageCount() {
            @Override
            public UUID getTagId() {
                return tagId;
            }

            @Override
            public long getUsageCount() {
                return count;
            }
        };
    }

    private CatalogAssetTagRepository.AssetRefProjection projection(String assetType, String assetKey) {
        return new CatalogAssetTagRepository.AssetRefProjection() {
            @Override
            public String getAssetType() {
                return assetType;
            }

            @Override
            public String getAssetKey() {
                return assetKey;
            }
        };
    }

    private List<UUID> rawTagIdsOverLimit() {
        return java.util.stream.IntStream
            .range(0, 101)
            .mapToObj(index -> new UUID(0, (index % 100) + 1L))
            .toList();
    }

    private CatalogTag enabledTag(UUID id) {
        CatalogTag tag = new CatalogTag();
        tag.setId(id);
        tag.setCategoryId(UUID.randomUUID());
        tag.setCode("QUALITY-HIGH");
        tag.setName("高可信");
        tag.setEnabled(true);
        tag.setBuiltin(false);
        return tag;
    }
}
