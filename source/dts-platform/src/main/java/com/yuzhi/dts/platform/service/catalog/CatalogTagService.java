package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagCategoryRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CatalogTagService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Pattern ASCII_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]*");

    private final CatalogTagCategoryRepository categoryRepository;
    private final CatalogTagRepository tagRepository;
    private final CatalogAssetTagRepository assetTagRepository;

    public CatalogTagService(
        CatalogTagCategoryRepository categoryRepository,
        CatalogTagRepository tagRepository,
        CatalogAssetTagRepository assetTagRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.assetTagRepository = assetTagRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogTagCategoryDto> listCategoryTree() {
        List<CatalogTagCategory> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc();
        Map<UUID, Long> tagCounts = new LinkedHashMap<>();
        for (CatalogTagRepository.CategoryTagCount count : tagRepository.countGroupedByCategoryId()) {
            tagCounts.put(count.getCategoryId(), count.getTagCount());
        }
        Map<UUID, CategoryNode> nodes = new LinkedHashMap<>();
        for (CatalogTagCategory category : categories) {
            nodes.put(category.getId(), new CategoryNode(category, tagCounts.getOrDefault(category.getId(), 0L)));
        }
        validateCategoryGraph(nodes);
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : nodes.values()) {
            CategoryNode parent = node.category.getParentId() == null ? null : nodes.get(node.category.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots.stream().map(this::toTreeDto).toList();
    }

    public CatalogTagCategoryDto createCategory(CatalogTagCategoryRequest request) {
        request = requireCategoryRequest(request);
        String code = requireNewCode(request.code(), categoryRepository::existsByCode);
        String name = requireName(request.name());
        requireParent(request.parentId(), null);
        CatalogTagCategory category = new CatalogTagCategory();
        category.setCode(code);
        category.setName(name);
        category.setParentId(request.parentId());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setBuiltin(false);
        category.setEnabled(request.enabled() == null || request.enabled());
        category.setDescription(requireOptionalLength(request.description(), 512, "分类说明"));
        try {
            return toCategoryDto(
                categoryRepository.saveAndFlush(category),
                0,
                List.of()
            );
        } catch (DataIntegrityViolationException exception) {
            throw translateCodeConstraint(
                exception,
                "uk_catalog_tag_category_code",
                code
            );
        }
    }

    public CatalogTagCategoryDto updateCategory(UUID id, CatalogTagCategoryRequest request) {
        request = requireCategoryRequest(request);
        CatalogTagCategory category = requireCategory(id);
        String code = normalizeCode(request.code());
        if (!code.equals(category.getCode()) && categoryRepository.existsByCode(code)) {
            throw badRequest("分类编码已存在：" + code);
        }
        String name = requireName(request.name());
        requireParent(request.parentId(), id);
        if (category.isBuiltin() && (!category.getCode().equals(code) || !category.getName().equals(name))) {
            throw badRequest("预置分类不可改名或修改编码，仅允许停用");
        }
        if (!category.isBuiltin()) {
            category.setCode(code);
            category.setName(name);
            category.setParentId(request.parentId());
            category.setSortOrder(request.sortOrder() == null ? category.getSortOrder() : request.sortOrder());
            category.setDescription(requireOptionalLength(request.description(), 512, "分类说明"));
        }
        if (request.enabled() != null) {
            category.setEnabled(request.enabled());
        }
        try {
            return toCategoryDto(
                categoryRepository.saveAndFlush(category),
                0,
                List.of()
            );
        } catch (DataIntegrityViolationException exception) {
            throw translateCodeConstraint(
                exception,
                "uk_catalog_tag_category_code",
                code
            );
        }
    }

    @Transactional(readOnly = true)
    public DeleteSnapshot getCategoryDeleteSnapshot(UUID id) {
        CatalogTagCategory category = requireCategory(id);
        return new DeleteSnapshot(category.getId(), category.getCode());
    }

    public void deleteCategory(UUID id) {
        CatalogTagCategory category = requireCategory(id);
        if (category.isBuiltin()) {
            throw badRequest("预置分类不可删除，可选择停用");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw badRequest("分类下存在子分类，不能删除");
        }
        if (tagRepository.existsByCategoryId(id)) {
            throw badRequest("分类下存在标签，不能删除");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Page<CatalogTagDto> listTags(UUID categoryId, String keyword, Boolean enabled, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            throw badRequest("分页参数不能为空");
        }
        if (pageable.getPageNumber() < 0) {
            throw badRequest("页码 page 不能小于 0");
        }
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw badRequest("每页数量 size 必须在 1 到 100 之间");
        }
        String normalizedKeyword = trimToNull(keyword);
        Page<CatalogTag> tags = tagRepository.search(
            categoryId,
            normalizedKeyword == null ? "" : normalizedKeyword,
            enabled,
            pageable
        );
        Map<UUID, Long> usageCounts = usageCounts(tags.getContent().stream().map(CatalogTag::getId).toList());
        return tags.map(tag -> toTagDto(tag, usageCounts.getOrDefault(tag.getId(), 0L)));
    }

    public CatalogTagDto createTag(CatalogTagRequest request) {
        request = requireTagRequest(request);
        String code = requireNewCode(request.code(), tagRepository::existsByCode);
        requireCategory(request.categoryId());
        CatalogTag tag = new CatalogTag();
        tag.setCategoryId(request.categoryId());
        tag.setCode(code);
        tag.setName(requireName(request.name()));
        tag.setColor(requireOptionalLength(request.color(), 16, "标签颜色"));
        tag.setBuiltin(false);
        tag.setEnabled(request.enabled() == null || request.enabled());
        tag.setDescription(requireOptionalLength(request.description(), 512, "标签说明"));
        CatalogTag saved;
        try {
            saved = tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException exception) {
            throw translateCodeConstraint(
                exception,
                "uk_catalog_tag_code",
                code
            );
        }
        return toTagDto(saved, assetTagRepository.countByTagId(saved.getId()));
    }

    public CatalogTagDto updateTag(UUID id, CatalogTagRequest request) {
        request = requireTagRequest(request);
        CatalogTag tag = requireTag(id);
        String code = normalizeCode(request.code());
        String name = requireName(request.name());
        if (request.categoryId() == null) {
            throw badRequest("标签分类不能为空");
        }
        if (!request.categoryId().equals(tag.getCategoryId())) {
            requireCategory(request.categoryId());
        }
        if (!code.equals(tag.getCode()) && tagRepository.existsByCode(code)) {
            throw badRequest("标签编码已存在：" + code);
        }
        if (
            tag.isBuiltin() &&
            (!tag.getCode().equals(code) || !tag.getName().equals(name) || !tag.getCategoryId().equals(request.categoryId()))
        ) {
            throw badRequest("预置标签不可改名、修改编码或分类，仅允许停用与改色");
        }
        if (!tag.isBuiltin()) {
            tag.setCategoryId(request.categoryId());
            tag.setCode(code);
            tag.setName(name);
            tag.setDescription(requireOptionalLength(request.description(), 512, "标签说明"));
        }
        tag.setColor(requireOptionalLength(request.color(), 16, "标签颜色"));
        if (request.enabled() != null) {
            tag.setEnabled(request.enabled());
        }
        CatalogTag saved;
        try {
            saved = tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException exception) {
            throw translateCodeConstraint(
                exception,
                "uk_catalog_tag_code",
                code
            );
        }
        return toTagDto(saved, assetTagRepository.countByTagId(saved.getId()));
    }

    @Transactional(readOnly = true)
    public DeleteSnapshot getTagDeleteSnapshot(UUID id) {
        CatalogTag tag = requireTag(id);
        return new DeleteSnapshot(tag.getId(), tag.getCode());
    }

    public void deleteTag(UUID id, boolean force) {
        CatalogTag tag = requireTag(id);
        if (tag.isBuiltin()) {
            throw badRequest("预置标签不可删除，可选择停用");
        }
        if (assetTagRepository.existsByTagId(id)) {
            if (!force) {
                throw badRequest("标签已用于资产，删除前请取消打标或使用 force=true");
            }
            assetTagRepository.deleteByTagId(id);
        }
        tagRepository.delete(tag);
    }

    private CatalogTagCategory requireCategory(UUID id) {
        if (id == null) {
            throw badRequest("标签分类不能为空");
        }
        return categoryRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "标签分类不存在"));
    }

    private CatalogTag requireTag(UUID id) {
        return tagRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在"));
    }

    private void requireParent(UUID parentId, UUID currentId) {
        if (parentId == null) {
            return;
        }
        Set<UUID> visited = new LinkedHashSet<>();
        UUID cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(currentId)) {
                throw badRequest("分类父链不能形成循环");
            }
            if (!visited.add(cursor)) {
                throw badRequest("分类父链存在历史循环，拒绝更新");
            }
            CatalogTagCategory parent = categoryRepository
                .findById(cursor)
                .orElseThrow(() -> badRequest("父分类不存在"));
            cursor = parent.getParentId();
        }
    }

    private String requireNewCode(String value, java.util.function.Predicate<String> exists) {
        String code = normalizeCode(value);
        if (exists.test(code)) {
            throw badRequest("编码已存在：" + code);
        }
        return code;
    }

    private ResponseStatusException translateCodeConstraint(
        DataIntegrityViolationException exception,
        String duplicateConstraint,
        String code
    ) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? "" : String.valueOf(cause.getMessage());
        if (message.contains(duplicateConstraint)) {
            return badRequest("编码已存在：" + code);
        }
        return new ResponseStatusException(
            HttpStatus.CONFLICT,
            "标签目录写入与数据库约束冲突，请刷新后重试",
            exception
        );
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw badRequest("编码不能为空，且必须使用 ASCII 标识");
        }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.length() > 64 || !ASCII_CODE.matcher(code).matches()) {
            throw badRequest("编码必须是非空 ASCII 标识，仅允许字母、数字、下划线和连字符");
        }
        return code;
    }

    private String requireName(String value) {
        String name = trimToNull(value);
        if (name == null) {
            throw badRequest("名称不能为空");
        }
        if (name.length() > 128) {
            throw badRequest("名称长度不能超过 128");
        }
        return name;
    }

    private CatalogTagCategoryRequest requireCategoryRequest(CatalogTagCategoryRequest request) {
        if (request == null) {
            throw badRequest("分类请求体不能为空");
        }
        return request;
    }

    private CatalogTagRequest requireTagRequest(CatalogTagRequest request) {
        if (request == null) {
            throw badRequest("标签请求体不能为空");
        }
        return request;
    }

    private String requireOptionalLength(String value, int maxLength, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw badRequest(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void validateCategoryGraph(Map<UUID, CategoryNode> nodes) {
        for (CategoryNode start : nodes.values()) {
            Set<UUID> path = new HashSet<>();
            UUID cursor = start.category.getId();
            while (cursor != null) {
                if (!path.add(cursor)) {
                    throw badRequest("标签分类结构异常：父链存在循环");
                }
                CategoryNode node = nodes.get(cursor);
                if (node == null) {
                    throw badRequest("标签分类结构异常：父分类不存在");
                }
                cursor = node.category.getParentId();
            }
        }
    }

    private CatalogTagCategoryDto toTreeDto(CategoryNode node) {
        return toCategoryDto(node.category, node.tagCount, node.children.stream().map(this::toTreeDto).toList());
    }

    private CatalogTagCategoryDto toCategoryDto(CatalogTagCategory category, long tagCount, List<CatalogTagCategoryDto> children) {
        return new CatalogTagCategoryDto(
            category.getId(),
            category.getCode(),
            category.getName(),
            category.getParentId(),
            category.getSortOrder(),
            category.isBuiltin(),
            category.isEnabled(),
            category.getDescription(),
            tagCount,
            children
        );
    }

    private Map<UUID, Long> usageCounts(List<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (CatalogAssetTagRepository.TagUsageCount count : assetTagRepository.countGroupedByTagId(tagIds)) {
            counts.put(count.getTagId(), count.getUsageCount());
        }
        return counts;
    }

    private CatalogTagDto toTagDto(CatalogTag tag, long usageCount) {
        return new CatalogTagDto(
            tag.getId(),
            tag.getCategoryId(),
            tag.getCode(),
            tag.getName(),
            tag.getColor(),
            tag.isBuiltin(),
            tag.isEnabled(),
            tag.getDescription(),
            usageCount
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record DeleteSnapshot(UUID id, String code) {}

    private static final class CategoryNode {

        private final CatalogTagCategory category;
        private final long tagCount;
        private final List<CategoryNode> children = new ArrayList<>();

        private CategoryNode(CatalogTagCategory category, long tagCount) {
            this.category = category;
            this.tagCount = tagCount;
        }
    }
}
