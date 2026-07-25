package com.yuzhi.dts.platform.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogTag;
import com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagCategoryRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagInstallLockRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagSeedReport;
import com.yuzhi.dts.platform.service.catalog.dto.CatalogTagSeedReport.Conflict;
import com.yuzhi.dts.platform.service.modeling.StandardPackageApplyService;
import com.yuzhi.dts.platform.service.modeling.StandardPackageContractException;
import com.yuzhi.dts.platform.service.modeling.StandardPackageManifestContract;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CatalogTagSeedService {

    private static final String PACKAGE_ROOT = "catalog-tags/dts-common-catalog-tags/";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String CONTENT_FILE = "catalog.json";
    private static final String SOURCE_BUILTIN = "CATALOG_TAG_BUILTIN";

    private final CatalogTagCategoryRepository categoryRepository;
    private final CatalogTagRepository tagRepository;
    private final CatalogTagInstallLockRepository installLockRepository;
    private final StandardPackageImportRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final StandardPackageManifestContract manifestContract;

    public CatalogTagSeedService(
        CatalogTagCategoryRepository categoryRepository,
        CatalogTagRepository tagRepository,
        CatalogTagInstallLockRepository installLockRepository,
        StandardPackageImportRunRepository runRepository,
        ObjectMapper objectMapper,
        StandardPackageManifestContract manifestContract
    ) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.installLockRepository = installLockRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.manifestContract = manifestContract;
    }

    public CatalogTagSeedReport install() {
        byte[] manifestBytes = readBytes(MANIFEST_FILE);
        byte[] contentBytes = readBytes(CONTENT_FILE);
        StandardPackageManifestContract.Manifest manifest = manifestContract.parsePackage(manifestBytes);
        if (!manifest.files().keySet().equals(java.util.Set.of(CONTENT_FILE))) {
            throw new StandardPackageContractException(
                "MANIFEST_FILE_SET_INVALID",
                "内置标签包只能声明 catalog.json"
            );
        }
        manifestContract.verifyFiles(
            manifest,
            Map.of(MANIFEST_FILE, manifestBytes, CONTENT_FILE, contentBytes)
        );
        PackageContent content = readContent(contentBytes);

        installLockRepository.acquirePackageLock(manifest.packageCode());
        Map<String, StandardPackageManifestContract.InstalledPackage> installed = installedPackages();
        manifestContract.requireInstallable(manifest, installed);
        StandardPackageManifestContract.InstalledPackage current = installed.get(manifest.packageCode());
        if (
            current != null &&
            manifestContract.compareVersions(manifest.packageVersion(), current.packageVersion()) == 0 &&
            !StringUtils.hasText(current.contentChecksum())
        ) {
            throw new StandardPackageContractException(
                "MANIFEST_INSTALLED_CHECKSUM_MISSING",
                "已安装同版本标签包缺少内容摘要，无法确认内容一致性"
            );
        }

        PreflightPlan preflight = preflight(content);
        if (!preflight.conflicts().isEmpty()) {
            return new CatalogTagSeedReport(
                manifest.packageCode(),
                manifest.packageVersion(),
                false,
                0,
                0,
                0,
                0,
                List.of(),
                preflight.conflicts()
            );
        }

        Map<String, UUID> categoryIds = new LinkedHashMap<>(preflight.categoryIds());
        for (CategoryContent item : preflight.categoriesToCreate()) {
            CatalogTagCategory category = new CatalogTagCategory();
            category.setCode(item.code());
            category.setName(item.name());
            category.setParentId(resolveParentId(item, categoryIds));
            category.setSortOrder(item.sortOrder());
            category.setBuiltin(true);
            category.setEnabled(true);
            category.setDescription(item.description());
            CatalogTagCategory saved = categoryRepository.save(category);
            categoryIds.put(item.code(), saved.getId());
        }
        for (TagContent item : preflight.tagsToCreate()) {
            UUID categoryId = categoryIds.get(item.categoryCode());
            if (categoryId == null) {
                throw new IllegalStateException("预置标签引用了不存在的分类：" + item.categoryCode());
            }
            CatalogTag tag = new CatalogTag();
            tag.setCategoryId(categoryId);
            tag.setCode(item.code());
            tag.setName(item.name());
            tag.setColor(item.color());
            tag.setBuiltin(true);
            tag.setEnabled(true);
            tag.setDescription(item.description());
            tagRepository.save(tag);
        }
        List<String> installedCodes = installedCodes(content);
        CatalogTagSeedReport report = new CatalogTagSeedReport(
            manifest.packageCode(),
            manifest.packageVersion(),
            true,
            preflight.categoriesToCreate().size(),
            preflight.tagsToCreate().size(),
            preflight.categoriesSkipped(),
            preflight.tagsSkipped(),
            installedCodes,
            List.of()
        );
        if (!isSameRevision(manifest, current)) {
            persistInstallRun(manifest, report);
        }
        return report;
    }

    private PreflightPlan preflight(PackageContent content) {
        List<CategoryContent> categoriesToCreate = new ArrayList<>();
        List<TagContent> tagsToCreate = new ArrayList<>();
        Map<String, UUID> categoryIds = new LinkedHashMap<>();
        Map<UUID, String> categoryCodesById = new LinkedHashMap<>();
        List<Conflict> conflicts = new ArrayList<>();
        int categoriesSkipped = 0;
        int tagsSkipped = 0;

        for (CategoryContent item : content.categories()) {
            CatalogTagCategory existing = categoryRepository.findByCode(item.code()).orElse(null);
            if (existing == null) {
                categoriesToCreate.add(item);
                continue;
            }
            categoryCodesById.put(existing.getId(), existing.getCode());
            if (!existing.isBuiltin()) {
                conflicts.add(
                    new Conflict(
                        "CATEGORY",
                        item.code(),
                        "CUSTOM_CATEGORY_CODE_CONFLICT",
                        null,
                        null
                    )
                );
                continue;
            }
            categoryIds.put(item.code(), existing.getId());
            categoriesSkipped++;
        }

        for (TagContent item : content.tags()) {
            CatalogTag existing = tagRepository.findByCode(item.code()).orElse(null);
            if (existing == null) {
                tagsToCreate.add(item);
                continue;
            }
            String actualCategoryCode = resolveCategoryCode(
                existing.getCategoryId(),
                categoryCodesById
            );
            if (!existing.isBuiltin()) {
                conflicts.add(
                    new Conflict(
                        "TAG",
                        item.code(),
                        "CUSTOM_TAG_CODE_CONFLICT",
                        item.categoryCode(),
                        actualCategoryCode
                    )
                );
                continue;
            }
            if (!Objects.equals(item.categoryCode(), actualCategoryCode)) {
                conflicts.add(
                    new Conflict(
                        "TAG",
                        item.code(),
                        "BUILTIN_TAG_CATEGORY_MISMATCH",
                        item.categoryCode(),
                        actualCategoryCode
                    )
                );
                continue;
            }
            tagsSkipped++;
        }

        return new PreflightPlan(
            categoriesToCreate,
            tagsToCreate,
            categoryIds,
            categoriesSkipped,
            tagsSkipped,
            conflicts
        );
    }

    private String resolveCategoryCode(UUID categoryId, Map<UUID, String> categoryCodesById) {
        if (categoryId == null) {
            return null;
        }
        if (categoryCodesById.containsKey(categoryId)) {
            return categoryCodesById.get(categoryId);
        }
        String code = categoryRepository
            .findById(categoryId)
            .map(CatalogTagCategory::getCode)
            .orElse(null);
        categoryCodesById.put(categoryId, code);
        return code;
    }

    private UUID resolveParentId(CategoryContent item, Map<String, UUID> categoryIds) {
        if (item.parentCode() == null) {
            return null;
        }
        UUID parentId = categoryIds.get(item.parentCode());
        if (parentId == null) {
            throw new IllegalStateException("预置标签分类引用了不存在的父分类：" + item.parentCode());
        }
        return parentId;
    }

    private List<String> installedCodes(PackageContent content) {
        List<String> installedCodes = new ArrayList<>(
            content.categories().size() + content.tags().size()
        );
        content.categories().forEach(item -> installedCodes.add(item.code()));
        content.tags().forEach(item -> installedCodes.add(item.code()));
        return List.copyOf(installedCodes);
    }

    private Map<String, StandardPackageManifestContract.InstalledPackage> installedPackages() {
        Map<String, StandardPackageManifestContract.InstalledPackage> installed = new LinkedHashMap<>();
        for (
            StandardPackageImportRun run : runRepository.findByStatusOrderByCreatedDateDesc(
                StandardPackageApplyService.STATUS_APPLIED
            )
        ) {
            if (!SOURCE_BUILTIN.equals(run.getSource())) {
                continue;
            }
            if (!StringUtils.hasText(run.getPreviewJson())) {
                throw new IllegalStateException("已安装内置标签包缺少版本摘要：" + run.getId());
            }
            try {
                JsonNode preview = objectMapper.readTree(run.getPreviewJson());
                String packageCode = requiredText(preview, "packageCode", run);
                String packageVersion = requiredText(preview, "packageVersion", run);
                String contentChecksum = requiredText(preview, "contentChecksum", run);
                installed.putIfAbsent(
                    packageCode,
                    new StandardPackageManifestContract.InstalledPackage(
                        packageCode,
                        packageVersion,
                        contentChecksum
                    )
                );
            } catch (StandardPackageContractException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("已安装内置标签包状态解析失败：" + run.getId(), exception);
            }
        }
        return Map.copyOf(installed);
    }

    private boolean isSameRevision(
        StandardPackageManifestContract.Manifest manifest,
        StandardPackageManifestContract.InstalledPackage current
    ) {
        return (
            current != null &&
            manifestContract.compareVersions(manifest.packageVersion(), current.packageVersion()) == 0 &&
            manifest.contentChecksum().equals(current.contentChecksum())
        );
    }

    private void persistInstallRun(
        StandardPackageManifestContract.Manifest manifest,
        CatalogTagSeedReport report
    ) {
        try {
            Map<String, Object> preview = new LinkedHashMap<>(manifestContract.summary(manifest));
            preview.put("categoriesCreated", report.categoriesCreated());
            preview.put("tagsCreated", report.tagsCreated());
            preview.put("categoriesSkipped", report.categoriesSkipped());
            preview.put("tagsSkipped", report.tagsSkipped());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("manifest", manifestContract.summary(manifest));
            payload.put("installedCodes", report.installedCodes());

            StandardPackageImportRun run = new StandardPackageImportRun();
            run.setPackageName(manifest.packageCode());
            run.setSource(SOURCE_BUILTIN);
            run.setStatus(StandardPackageApplyService.STATUS_APPLIED);
            run.setSummary(
                "内置标签包已安装：" +
                manifest.packageCode() +
                "@" +
                manifest.packageVersion() +
                "，摘要 " +
                manifest.contentChecksum()
            );
            run.setPreviewJson(objectMapper.writeValueAsString(preview));
            run.setPayloadJson(objectMapper.writeValueAsString(payload));
            Instant now = Instant.now();
            run.setCreatedBy("system");
            run.setCreatedDate(now);
            run.setLastModifiedBy("system");
            run.setLastModifiedDate(now);
            runRepository.save(run);
        } catch (Exception exception) {
            throw new IllegalStateException("内置标签包安装记录写入失败", exception);
        }
    }

    private String requiredText(JsonNode node, String field, StandardPackageImportRun run) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new StandardPackageContractException(
                "MANIFEST_INSTALLED_STATE_INVALID",
                "已安装内置标签包摘要缺少 " + field + "：" + run.getId()
            );
        }
        return value.asText();
    }

    private byte[] readBytes(String fileName) {
        try (InputStream input = new ClassPathResource(PACKAGE_ROOT + fileName).getInputStream()) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("预置标签内容包读取失败：" + fileName, exception);
        }
    }

    private PackageContent readContent(byte[] content) {
        try {
            return objectMapper.readValue(content, PackageContent.class);
        } catch (Exception exception) {
            throw new IllegalStateException("预置标签内容包读取失败：" + CONTENT_FILE, exception);
        }
    }

    private record PackageContent(List<CategoryContent> categories, List<TagContent> tags) {}

    private record CategoryContent(
        String code,
        String name,
        String parentCode,
        int sortOrder,
        String description
    ) {}

    private record TagContent(String categoryCode, String code, String name, String color, String description) {}

    private record PreflightPlan(
        List<CategoryContent> categoriesToCreate,
        List<TagContent> tagsToCreate,
        Map<String, UUID> categoryIds,
        int categoriesSkipped,
        int tagsSkipped,
        List<Conflict> conflicts
    ) {
        private PreflightPlan {
            categoriesToCreate = List.copyOf(categoriesToCreate);
            tagsToCreate = List.copyOf(tagsToCreate);
            categoryIds = Map.copyOf(categoryIds);
            conflicts = List.copyOf(conflicts);
        }
    }
}
