package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagPermissionException;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagReadVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagWriteGuard.AuthorizedAssets;
import com.yuzhi.dts.platform.service.catalog.CatalogTagAuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagGovernanceGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogTagSeedService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagService;
import com.yuzhi.dts.platform.service.catalog.CatalogTagService.DeleteSnapshot;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.catalog.dto.AssetTagMutationResult;
import com.yuzhi.dts.platform.service.catalog.dto.AssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.BatchAssetTagResult;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagCategoryRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagPageDto;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagRequest;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagSeedReport;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
public class CatalogTagResource {

    private final CatalogTagService tagService;
    private final CatalogAssetTagService assetTagService;
    private final CatalogAssetTagReadVisibilityService assetTagReadVisibility;
    private final CatalogTagSeedService seedService;
    private final CatalogTagGovernanceGuard governanceGuard;
    private final CatalogAssetTagWriteGuard assetTagWriteGuard;
    private final CatalogTagAuditService tagAuditService;

    public CatalogTagResource(
        CatalogTagService tagService,
        CatalogAssetTagService assetTagService,
        CatalogAssetTagReadVisibilityService assetTagReadVisibility,
        CatalogTagSeedService seedService,
        CatalogTagGovernanceGuard governanceGuard,
        CatalogAssetTagWriteGuard assetTagWriteGuard,
        CatalogTagAuditService tagAuditService
    ) {
        this.tagService = tagService;
        this.assetTagService = assetTagService;
        this.assetTagReadVisibility = assetTagReadVisibility;
        this.seedService = seedService;
        this.governanceGuard = governanceGuard;
        this.assetTagWriteGuard = assetTagWriteGuard;
        this.tagAuditService = tagAuditService;
    }

    @GetMapping("/tag-categories")
    public ApiResponse<List<CatalogTagCategoryDto>> listCategories() {
        return ApiResponses.ok(tagService.listCategoryTree());
    }

    @PostMapping("/tag-categories")
    public ApiResponse<CatalogTagCategoryDto> createCategory(@Valid @RequestBody CatalogTagCategoryRequest request) {
        String resourceId = auditCode(request.code());
        CatalogTagCategoryDto result;
        try {
            governanceGuard.requireMaintainer();
            result = tagService.createCategory(request);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_CATEGORY_CREATE,
                resourceId,
                exception,
                Map.of("code", resourceId)
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_CATEGORY_CREATE,
            result.code(),
            categorySuccessPayload(result)
        );
        return ApiResponses.ok(result);
    }

    @PutMapping("/tag-categories/{id}")
    public ApiResponse<CatalogTagCategoryDto> updateCategory(
        @PathVariable UUID id,
        @Valid @RequestBody CatalogTagCategoryRequest request
    ) {
        String resourceId = auditCode(request.code());
        CatalogTagCategoryDto result;
        try {
            governanceGuard.requireMaintainer();
            result = tagService.updateCategory(id, request);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_CATEGORY_UPDATE,
                resourceId,
                exception,
                Map.of("id", id.toString(), "code", resourceId)
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_CATEGORY_UPDATE,
            result.code(),
            categorySuccessPayload(result)
        );
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/tag-categories/{id}")
    public ApiResponse<Boolean> deleteCategory(@PathVariable UUID id) {
        String resourceId = id.toString();
        Map<String, Object> auditPayload = deletePayload(id, resourceId, null);
        try {
            governanceGuard.requireMaintainer();
            DeleteSnapshot snapshot = tagService.getCategoryDeleteSnapshot(id);
            resourceId = auditCode(snapshot.code());
            auditPayload = deletePayload(id, resourceId, null);
            tagService.deleteCategory(id);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_CATEGORY_DELETE,
                resourceId,
                exception,
                auditPayload
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_CATEGORY_DELETE,
            resourceId,
            auditPayload
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/tags")
    public ApiResponse<CatalogTagPageDto> listTags(
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码 page 不能小于 0");
        }
        if (size < 1 || size > CatalogTagService.MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每页数量 size 必须在 1 到 100 之间");
        }
        Page<CatalogTagDto> result = tagService.listTags(
            categoryId,
            keyword,
            enabled,
            PageRequest.of(page, size, Sort.by(Sort.Order.asc("code"), Sort.Order.asc("id")))
        );
        return ApiResponses.ok(new CatalogTagPageDto(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @PostMapping("/tags")
    public ApiResponse<CatalogTagDto> createTag(@Valid @RequestBody CatalogTagRequest request) {
        String resourceId = auditCode(request.code());
        CatalogTagDto result;
        try {
            governanceGuard.requireMaintainer();
            result = tagService.createTag(request);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_CREATE,
                resourceId,
                exception,
                tagDefinitionPayload(request, null)
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_CREATE,
            result.code(),
            tagSuccessPayload(result)
        );
        return ApiResponses.ok(result);
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<CatalogTagDto> updateTag(@PathVariable UUID id, @Valid @RequestBody CatalogTagRequest request) {
        String resourceId = auditCode(request.code());
        CatalogTagDto result;
        try {
            governanceGuard.requireMaintainer();
            result = tagService.updateTag(id, request);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_UPDATE,
                resourceId,
                exception,
                tagDefinitionPayload(request, id)
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_UPDATE,
            result.code(),
            tagSuccessPayload(result)
        );
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Boolean> deleteTag(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        String resourceId = id.toString();
        Map<String, Object> auditPayload = deletePayload(id, resourceId, force);
        try {
            governanceGuard.requireMaintainer();
            DeleteSnapshot snapshot = tagService.getTagDeleteSnapshot(id);
            resourceId = auditCode(snapshot.code());
            auditPayload = deletePayload(id, resourceId, force);
            tagService.deleteTag(id, force);
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.TAG_DELETE,
                resourceId,
                exception,
                auditPayload
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.TAG_DELETE,
            resourceId,
            auditPayload
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/asset-tags")
    public ApiResponse<List<CatalogTagDto>> listAssetTags(
        @RequestParam String assetType,
        @RequestParam String assetKey,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<AssetRef> readable = assetTagReadVisibility.filterReadable(
            List.of(new AssetRef(assetType, assetKey)),
            activeDept
        );
        if (readable.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资产不存在或无权访问");
        }
        AssetRef asset = readable.getFirst();
        return ApiResponses.ok(assetTagService.listAssetTags(asset.assetType(), asset.assetKey()));
    }

    @GetMapping("/asset-tags/capability")
    public ApiResponse<Map<String, Boolean>> getAssetTagCapability(
        @RequestParam String assetType,
        @RequestParam String assetKey
    ) {
        boolean canTag = assetTagWriteGuard.canTag(new AssetRef(assetType, assetKey));
        return ApiResponses.ok(Map.of("canTag", canTag));
    }

    @PostMapping("/asset-tags")
    public ApiResponse<AssetTagMutationResult> tagAsset(@Valid @RequestBody AssetTagRequest request) {
        String resourceId = assetResourceId(request.assetType(), request.assetKey());
        AssetTagMutationResult result;
        AssetRef asset;
        try {
            AuthorizedAssets authorized = assetTagWriteGuard.authorizeAll(
                List.of(new AssetRef(request.assetType(), request.assetKey()))
            );
            asset = requireSingleAuthorizedAsset(authorized);
            result = assetTagService.tagAsset(
                asset.assetType(),
                asset.assetKey(),
                request.tagIds(),
                authorized.actor()
            );
        } catch (RuntimeException exception) {
            tagAuditService.assetFailure(
                CatalogTagAuditService.ASSET_TAG_CREATE,
                resourceId,
                request.tagIds(),
                exception,
                assetFailurePayload(1, exception)
            );
            throw exception;
        }
        resourceId = assetResourceId(asset.assetType(), asset.assetKey());
        tagAuditService.assetSuccess(
            CatalogTagAuditService.ASSET_TAG_CREATE,
            resourceId,
            request.tagIds(),
            Map.of(
                "assetType",
                asset.assetType(),
                "assetKey",
                asset.assetKey(),
                "created",
                result.created(),
                "skipped",
                result.skipped(),
                "removed",
                result.removed()
            )
        );
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/asset-tags")
    public ApiResponse<AssetTagMutationResult> untagAsset(@Valid @RequestBody AssetTagRequest request) {
        String resourceId = assetResourceId(request.assetType(), request.assetKey());
        AssetTagMutationResult result;
        AssetRef asset;
        try {
            AuthorizedAssets authorized = assetTagWriteGuard.authorizeAll(
                List.of(new AssetRef(request.assetType(), request.assetKey()))
            );
            asset = requireSingleAuthorizedAsset(authorized);
            result = assetTagService.untagAsset(asset.assetType(), asset.assetKey(), request.tagIds());
        } catch (RuntimeException exception) {
            tagAuditService.assetFailure(
                CatalogTagAuditService.ASSET_TAG_DELETE,
                resourceId,
                request.tagIds(),
                exception,
                assetFailurePayload(1, exception)
            );
            throw exception;
        }
        resourceId = assetResourceId(asset.assetType(), asset.assetKey());
        tagAuditService.assetSuccess(
            CatalogTagAuditService.ASSET_TAG_DELETE,
            resourceId,
            request.tagIds(),
            Map.of(
                "assetType",
                asset.assetType(),
                "assetKey",
                asset.assetKey(),
                "created",
                result.created(),
                "skipped",
                result.skipped(),
                "removed",
                result.removed()
            )
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/asset-tags/batch")
    public ApiResponse<BatchAssetTagResult> batchTag(@Valid @RequestBody BatchAssetTagRequest request) {
        BatchAssetTagResult result;
        try {
            AuthorizedAssets authorized = assetTagWriteGuard.authorizeAll(request.assets());
            BatchAssetTagRequest authorizedRequest = new BatchAssetTagRequest(
                authorized.assets(),
                request.tagIds()
            );
            result = assetTagService.batchTag(authorizedRequest, authorized.actor());
        } catch (RuntimeException exception) {
            tagAuditService.assetFailure(
                CatalogTagAuditService.ASSET_TAG_BATCH_CREATE,
                "BATCH",
                request.tagIds(),
                exception,
                assetFailurePayload(distinctAssetCount(request.assets()), exception)
            );
            throw exception;
        }
        tagAuditService.assetSuccess(
            CatalogTagAuditService.ASSET_TAG_BATCH_CREATE,
            "BATCH",
            request.tagIds(),
            Map.of(
                "assetCount",
                result.assetCount(),
                "created",
                result.created(),
                "skipped",
                result.skipped()
            )
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/tags/builtin/install")
    public ApiResponse<CatalogTagSeedReport> installBuiltinTags() {
        CatalogTagSeedReport result;
        try {
            governanceGuard.requireMaintainer();
            result = seedService.install();
        } catch (RuntimeException exception) {
            tagAuditService.failure(
                CatalogTagAuditService.BUILTIN_INSTALL,
                "BUILTIN",
                exception,
                Map.of("package", "BUILTIN")
            );
            throw exception;
        }
        tagAuditService.success(
            CatalogTagAuditService.BUILTIN_INSTALL,
            result.packageCode(),
            Map.of(
                "packageCode",
                result.packageCode(),
                "packageVersion",
                result.packageVersion(),
                "applied",
                result.applied(),
                "categoriesCreated",
                result.categoriesCreated(),
                "tagsCreated",
                result.tagsCreated(),
                "categoriesSkipped",
                result.categoriesSkipped(),
                "tagsSkipped",
                result.tagsSkipped()
            )
        );
        return ApiResponses.ok(result);
    }

    private AssetRef requireSingleAuthorizedAsset(AuthorizedAssets authorized) {
        if (authorized == null || authorized.assets().size() != 1) {
            throw new IllegalStateException("资产写权限预检未返回唯一授权资产");
        }
        return authorized.assets().getFirst();
    }

    private Map<String, Object> tagDefinitionPayload(CatalogTagRequest request, UUID id) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (id != null) {
            payload.put("id", id.toString());
        }
        payload.put("code", auditCode(request.code()));
        if (request.categoryId() != null) {
            payload.put("categoryId", request.categoryId().toString());
        }
        return payload;
    }

    private Map<String, Object> deletePayload(UUID id, String code, Boolean force) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id.toString());
        payload.put("code", code);
        if (force != null) {
            payload.put("force", force);
        }
        return payload;
    }

    private Map<String, Object> assetFailurePayload(int assetCount, RuntimeException exception) {
        return Map.of(
            "assetCount",
            assetCount,
            "deniedCount",
            exception instanceof CatalogAssetTagPermissionException permissionException
                ? permissionException.deniedCount()
                : 0
        );
    }

    private Map<String, Object> categorySuccessPayload(CatalogTagCategoryDto category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", category.id().toString());
        payload.put("code", category.code());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("id", category.id().toString());
        after.put("code", category.code());
        after.put("name", category.name());
        after.put("sortOrder", category.sortOrder());
        after.put("builtin", category.builtin());
        after.put("enabled", category.enabled());
        if (category.parentId() != null) {
            after.put("parentId", category.parentId().toString());
        }
        payload.put("after", after);
        return payload;
    }

    private Map<String, Object> tagSuccessPayload(CatalogTagDto tag) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", tag.id().toString());
        payload.put("code", tag.code());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("id", tag.id().toString());
        after.put("categoryId", tag.categoryId().toString());
        after.put("code", tag.code());
        after.put("name", tag.name());
        after.put("builtin", tag.builtin());
        after.put("enabled", tag.enabled());
        payload.put("after", after);
        return payload;
    }

    private int distinctAssetCount(List<AssetRef> assets) {
        if (assets == null || assets.isEmpty()) {
            return 0;
        }
        return (int) assets
            .stream()
            .filter(java.util.Objects::nonNull)
            .map(asset -> assetResourceId(asset.assetType(), asset.assetKey()))
            .distinct()
            .count();
    }

    private String assetResourceId(String assetType, String assetKey) {
        String normalizedType = StringUtils.hasText(assetType)
            ? assetType.trim().toUpperCase(Locale.ROOT).replace('-', '_')
            : "UNKNOWN";
        String normalizedKey = StringUtils.hasText(assetKey) ? assetKey.trim() : "unknown";
        return normalizedType + ":" + normalizedKey;
    }

    private String auditCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}
