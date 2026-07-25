package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagCategoryRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogTagService.DeleteSnapshot;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CatalogTagServiceTest {

    @Mock
    private CatalogTagCategoryRepository categoryRepository;

    @Mock
    private CatalogTagRepository tagRepository;

    @Mock
    private CatalogAssetTagRepository assetTagRepository;

    private CatalogTagService service;

    @BeforeEach
    void setUp() {
        service = new CatalogTagService(categoryRepository, tagRepository, assetTagRepository);
    }

    @Test
    void listCategoryTreeBuildsAllLevelsFromOneCategoryReadAndIncludesTagCounts() {
        CatalogTagCategory root = category("BUSINESS", "业务域", null, 10);
        CatalogTagCategory child = category("FINANCE", "财务", root.getId(), 20);
        CatalogTagCategory grandchild = category("ACCOUNTING", "会计", child.getId(), 30);
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(root, child, grandchild));
        when(tagRepository.countGroupedByCategoryId())
            .thenReturn(List.of(count(root.getId(), 2), count(child.getId(), 1), count(grandchild.getId(), 3)));

        var tree = service.listCategoryTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).code()).isEqualTo("BUSINESS");
        assertThat(tree.get(0).tagCount()).isEqualTo(2);
        assertThat(tree.get(0).children().get(0).code()).isEqualTo("FINANCE");
        assertThat(tree.get(0).children().get(0).children().get(0).tagCount()).isEqualTo(3);
        verify(categoryRepository).findAllByOrderBySortOrderAscNameAsc();
    }

    @Test
    void listCategoryTreeFailsClosedWhenHistoricalParentIsMissing() {
        CatalogTagCategory orphan = category("ORPHAN", "孤儿分类", UUID.randomUUID(), 10);
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(orphan));
        when(tagRepository.countGroupedByCategoryId()).thenReturn(List.of());

        assertThatThrownBy(service::listCategoryTree)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("父分类不存在");
    }

    @Test
    void listCategoryTreeFailsClosedWhenHistoricalCategoriesContainACycle() {
        CatalogTagCategory first = category("FIRST", "分类一", null, 10);
        CatalogTagCategory second = category("SECOND", "分类二", first.getId(), 20);
        first.setParentId(second.getId());
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(first, second));
        when(tagRepository.countGroupedByCategoryId()).thenReturn(List.of());

        assertThatThrownBy(service::listCategoryTree)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("循环");
    }

    @Test
    void updateCategoryRejectsMovingANodeBelowAnyDescendant() {
        CatalogTagCategory root = category("ROOT", "根分类", null, 10);
        CatalogTagCategory child = category("CHILD", "子分类", root.getId(), 20);
        CatalogTagCategory grandchild = category("GRANDCHILD", "孙分类", child.getId(), 30);
        when(categoryRepository.findById(root.getId())).thenReturn(Optional.of(root));
        when(categoryRepository.findById(child.getId())).thenReturn(Optional.of(child));
        when(categoryRepository.findById(grandchild.getId())).thenReturn(Optional.of(grandchild));

        assertThatThrownBy(() ->
                service.updateCategory(
                    root.getId(),
                    new CatalogTagCategoryRequest(
                        root.getCode(),
                        root.getName(),
                        grandchild.getId(),
                        root.getSortOrder(),
                        false,
                        true,
                        null
                    )
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("循环");
    }

    @Test
    void createCategoryRejectsNonAsciiAndDuplicateCodesWithReadableBadRequests() {
        assertThatThrownBy(() ->
                service.createCategory(new CatalogTagCategoryRequest("业务域", "业务域", null, 0, false, true, null))
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ASCII");

        when(categoryRepository.existsByCode("BUSINESS")).thenReturn(true);
        assertThatThrownBy(() ->
                service.createCategory(new CatalogTagCategoryRequest("BUSINESS", "业务域", null, 0, false, true, null))
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("编码已存在");
    }

    @Test
    void createCategoryMapsConcurrentUniqueConstraintToReadableBadRequest() {
        when(categoryRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(CatalogTagCategory.class)))
            .thenThrow(
                new DataIntegrityViolationException(
                    "duplicate",
                    new IllegalStateException(
                        "duplicate key violates unique constraint \"uk_catalog_tag_category_code\""
                    )
                )
            );

        assertThatThrownBy(() ->
                service.createCategory(
                    new CatalogTagCategoryRequest(
                        "BUSINESS",
                        "业务域",
                        null,
                        0,
                        false,
                        true,
                        null
                    )
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("编码已存在：BUSINESS");
    }

    @Test
    void updateCategoryMapsConcurrentUniqueConstraintToReadableBadRequest() {
        CatalogTagCategory category = category(
            "OLD-CATEGORY",
            "旧分类",
            null,
            0
        );
        when(categoryRepository.findById(category.getId()))
            .thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(category))
            .thenThrow(
                new DataIntegrityViolationException(
                    "duplicate",
                    new IllegalStateException(
                        "duplicate key violates unique constraint \"uk_catalog_tag_category_code\""
                    )
                )
            );

        assertThatThrownBy(() ->
                service.updateCategory(
                    category.getId(),
                    new CatalogTagCategoryRequest(
                        "NEW-CATEGORY",
                        "新分类",
                        null,
                        0,
                        false,
                        true,
                        null
                    )
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("编码已存在：NEW-CATEGORY");
    }

    @Test
    void categoryServiceDefensivelyRejectsNullBody() {
        assertThatThrownBy(() -> service.createCategory(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("请求体");
    }

    @Test
    void categoryServiceDefensivelyRejectsNameOverflow() {
        assertThatThrownBy(() ->
                service.createCategory(
                    new CatalogTagCategoryRequest("LONG-NAME", "名".repeat(129), null, 0, false, true, null)
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("128");
    }

    @Test
    void categoryServiceDefensivelyRejectsDescriptionOverflow() {
        assertThatThrownBy(() ->
                service.createCategory(
                    new CatalogTagCategoryRequest("LONG-DESCRIPTION", "分类", null, 0, false, true, "说".repeat(513))
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("512");
    }

    @Test
    void tagServiceDefensivelyRejectsNullBody() {
        assertThatThrownBy(() -> service.createTag(null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("请求体");
    }

    @Test
    void createTagMapsConcurrentUniqueConstraintToReadableBadRequest() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId))
            .thenReturn(
                Optional.of(category("CATEGORY", "分类", null, 0))
            );
        when(tagRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(CatalogTag.class)))
            .thenThrow(
                new DataIntegrityViolationException(
                    "duplicate",
                    new IllegalStateException(
                        "duplicate key violates unique constraint \"uk_catalog_tag_code\""
                    )
                )
            );

        assertThatThrownBy(() ->
                service.createTag(
                    new CatalogTagRequest(
                        categoryId,
                        "QUALITY",
                        "质量标签",
                        null,
                        false,
                        true,
                        null
                    )
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("编码已存在：QUALITY");
    }

    @Test
    void updateTagMapsConcurrentUniqueConstraintToReadableBadRequest() {
        CatalogTag tag = tag("OLD-TAG", "旧标签", false, true);
        when(tagRepository.findById(tag.getId()))
            .thenReturn(Optional.of(tag));
        when(tagRepository.saveAndFlush(tag))
            .thenThrow(
                new DataIntegrityViolationException(
                    "duplicate",
                    new IllegalStateException(
                        "duplicate key violates unique constraint \"uk_catalog_tag_code\""
                    )
                )
            );

        assertThatThrownBy(() ->
                service.updateTag(
                    tag.getId(),
                    new CatalogTagRequest(
                        tag.getCategoryId(),
                        "NEW-TAG",
                        "新标签",
                        null,
                        false,
                        true,
                        null
                    )
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("编码已存在：NEW-TAG");
    }

    @Test
    void tagServiceDefensivelyRejectsNameOverflow() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category("CATEGORY", "分类", null, 0)));

        assertThatThrownBy(() ->
                service.createTag(new CatalogTagRequest(categoryId, "LONG-NAME", "名".repeat(129), null, false, true, null))
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("128");
    }

    @Test
    void tagServiceDefensivelyRejectsDescriptionOverflow() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category("CATEGORY", "分类", null, 0)));

        assertThatThrownBy(() ->
                service.createTag(
                    new CatalogTagRequest(categoryId, "LONG-DESCRIPTION", "标签", null, false, true, "说".repeat(513))
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("512");
    }

    @Test
    void tagServiceDefensivelyRejectsColorOverflow() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category("CATEGORY", "分类", null, 0)));

        assertThatThrownBy(() ->
                service.createTag(
                    new CatalogTagRequest(categoryId, "LONG-COLOR", "标签", "#1234567890123456", false, true, null)
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("16");
    }

    @Test
    void tagServiceDefensivelyRejectsPageSizesAboveOneHundred() {
        assertThatThrownBy(() -> service.listTags(null, null, null, PageRequest.of(0, 101)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("100");
    }

    @Test
    void listTagsBindsAnEmptyKeywordAsTextForPostgres() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(tagRepository.search(null, "", null, pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listTags(null, "  ", null, pageable);

        verify(tagRepository).search(null, "", null, pageable);
    }

    @Test
    void listTagsLoadsUsageCountsForTheCurrentPageInOneGroupedQuery() {
        CatalogTag unused = tag("UNUSED", "未使用", false, true);
        CatalogTag popular = tag("POPULAR", "常用", false, true);
        PageRequest pageable = PageRequest.of(0, 10);
        when(tagRepository.search(null, "", null, pageable))
            .thenReturn(new PageImpl<>(List.of(unused, popular), pageable, 2));
        when(assetTagRepository.countGroupedByTagId(List.of(unused.getId(), popular.getId())))
            .thenReturn(List.of(usageCount(popular.getId(), 7)));

        var result = service.listTags(null, null, null, pageable);

        assertThat(result.getContent())
            .extracting(dto -> Map.entry(dto.code(), dto.usageCount()))
            .containsExactly(Map.entry("UNUSED", 0L), Map.entry("POPULAR", 7L));
        verify(assetTagRepository).countGroupedByTagId(List.of(unused.getId(), popular.getId()));
    }

    @Test
    void deleteSnapshotsExposeStableBusinessCodesBeforeMutation() {
        CatalogTagCategory category = category("CUSTOM", "客户分类", null, 0);
        CatalogTag tag = tag("CUSTOM-TAG", "客户标签", false, true);
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));

        assertThat(service.getCategoryDeleteSnapshot(category.getId()))
            .isEqualTo(new DeleteSnapshot(category.getId(), "CUSTOM"));
        assertThat(service.getTagDeleteSnapshot(tag.getId()))
            .isEqualTo(new DeleteSnapshot(tag.getId(), "CUSTOM-TAG"));
    }

    @Test
    void deleteCategoryRejectsBuiltinAndNonEmptyCategories() {
        CatalogTagCategory builtin = category("BUSINESS", "业务域", null, 0);
        builtin.setBuiltin(true);
        when(categoryRepository.findById(builtin.getId())).thenReturn(Optional.of(builtin));

        assertThatThrownBy(() -> service.deleteCategory(builtin.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("预置分类");

        CatalogTagCategory nonEmpty = category("CUSTOM", "客户分类", null, 0);
        when(categoryRepository.findById(nonEmpty.getId())).thenReturn(Optional.of(nonEmpty));
        when(tagRepository.existsByCategoryId(nonEmpty.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory(nonEmpty.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("存在标签");
    }

    @Test
    void deleteTaggedTagRequiresForceAndForceRemovesAssignmentsFirst() {
        CatalogTag tag = tag("QUALITY-HIGH", "高可信", false, true);
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));
        when(assetTagRepository.existsByTagId(tag.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.deleteTag(tag.getId(), false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("已用于资产");
        verify(tagRepository, never()).delete(tag);

        service.deleteTag(tag.getId(), true);

        var inOrder = org.mockito.Mockito.inOrder(assetTagRepository, tagRepository);
        inOrder.verify(assetTagRepository).deleteByTagId(tag.getId());
        inOrder.verify(tagRepository).delete(tag);
    }

    @Test
    void builtinTagCannotBeRenamedButCanBeDisabledAndRecolored() {
        CatalogTag tag = tag("QUALITY-HIGH", "高可信", true, true);
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));
        when(tagRepository.saveAndFlush(tag)).thenReturn(tag);

        assertThatThrownBy(() ->
                service.updateTag(
                    tag.getId(),
                    new CatalogTagRequest(tag.getCategoryId(), tag.getCode(), "客户改名", "#112233", true, false, "说明")
                )
            )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("预置标签不可改名");

        var updated = service.updateTag(
            tag.getId(),
            new CatalogTagRequest(tag.getCategoryId(), tag.getCode(), "高可信", "#112233", true, false, "说明")
        );

        assertThat(updated.enabled()).isFalse();
        assertThat(updated.color()).isEqualTo("#112233");
    }

    private CatalogTagCategory category(String code, String name, UUID parentId, int sortOrder) {
        CatalogTagCategory category = new CatalogTagCategory();
        category.setId(UUID.randomUUID());
        category.setCode(code);
        category.setName(name);
        category.setParentId(parentId);
        category.setSortOrder(sortOrder);
        category.setBuiltin(false);
        category.setEnabled(true);
        return category;
    }

    private CatalogTag tag(String code, String name, boolean builtin, boolean enabled) {
        CatalogTag tag = new CatalogTag();
        tag.setId(UUID.randomUUID());
        tag.setCategoryId(UUID.randomUUID());
        tag.setCode(code);
        tag.setName(name);
        tag.setBuiltin(builtin);
        tag.setEnabled(enabled);
        return tag;
    }

    private CatalogTagRepository.CategoryTagCount count(UUID categoryId, long count) {
        return new CatalogTagRepository.CategoryTagCount() {
            @Override
            public UUID getCategoryId() {
                return categoryId;
            }

            @Override
            public long getTagCount() {
                return count;
            }
        };
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
}
