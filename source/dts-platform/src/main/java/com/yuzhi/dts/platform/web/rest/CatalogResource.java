package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
import java.lang.reflect.Array;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/catalog")
@Transactional
public class CatalogResource {

    private static final String TYPE_INCEPTOR = "INCEPTOR";
    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogDomainRepository domainRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final CatalogClassificationMappingRepository mappingRepo;
    private final AuditService audit;
    private final ClassificationUtils classificationUtils;
    private final com.yuzhi.dts.platform.service.security.AccessChecker accessChecker;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository tableRepo;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository columnRepo;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository rowFilterRepo;
    private final CatalogDatasetGrantRepository grantRepo;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final CatalogFeatureProperties catalogFeatures;

    public CatalogResource(
        CatalogDomainRepository domainRepo,
        CatalogDatasetRepository datasetRepo,
        CatalogMaskingRuleRepository maskingRepo,
        CatalogClassificationMappingRepository mappingRepo,
        AuditService audit,
        ClassificationUtils classificationUtils,
        com.yuzhi.dts.platform.service.security.AccessChecker accessChecker,
        com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository tableRepo,
        com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository columnRepo,
        com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository rowFilterRepo,
        CatalogDatasetGrantRepository grantRepo,
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogFeatureProperties catalogFeatures
    ) {
        this.domainRepo = domainRepo;
        this.datasetRepo = datasetRepo;
        this.maskingRepo = maskingRepo;
        this.mappingRepo = mappingRepo;
        this.audit = audit;
        this.classificationUtils = classificationUtils;
        this.accessChecker = accessChecker;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.rowFilterRepo = rowFilterRepo;
        this.grantRepo = grantRepo;
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.catalogFeatures = catalogFeatures;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("multiSourceEnabled", catalogFeatures.isMultiSourceEnabled());
        payload.put("defaultSourceType", defaultSourceType());
        payload.put("hasPrimarySource", hasPrimarySourceConfigured());
        payload.put("primarySourceType", defaultSourceType());
        audit.recordAuxiliary(
            "READ",
            "catalog.config",
            "catalog.config",
            "config",
            "SUCCESS",
            Map.of("summary", "获取数据目录配置")
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domains", domainRepo.count());
        map.put("datasets", datasetRepo.count());
        map.put("maskingRules", maskingRepo.count());
        map.put("classificationMappings", mappingRepo.count());
        audit.recordAuxiliary(
            "READ",
            "catalog.summary",
            "catalog.summary",
            "summary",
            "SUCCESS",
            Map.of("summary", "获取数据目录概览")
        );
        return ApiResponses.ok(map);
    }

    // Domains CRUD
    @GetMapping("/domains")
    public ApiResponse<Map<String, Object>> listDomains(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<CatalogDomain> p = domainRepo.findAll(pageable);
        Map<String, Object> data = Map.of("content", p.getContent(), "total", p.getTotalElements());
        audit.audit("READ", "catalog.domain", "page=" + page);
        return ApiResponses.ok(data);
    }

    @PostMapping("/domains")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> createDomain(@Valid @RequestBody CatalogDomain domain) {
        // support parentId mapping if provided
        if (domain.getParent() != null && domain.getParent().getId() != null) {
            UUID pid = domain.getParent().getId();
            domain.setParent(domainRepo.findById(pid).orElse(null));
        }
        CatalogDomain saved = domainRepo.save(domain);
        audit.audit("CREATE", "catalog.domain", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/domains/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> updateDomain(@PathVariable UUID id, @Valid @RequestBody CatalogDomain patch) {
        CatalogDomain existing = domainRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setCode(patch.getCode());
        existing.setOwner(patch.getOwner());
        existing.setDescription(patch.getDescription());
        if (patch.getParent() != null && patch.getParent().getId() != null) {
            UUID pid = patch.getParent().getId();
            existing.setParent(domainRepo.findById(pid).orElse(null));
        } else {
            existing.setParent(null);
        }
        CatalogDomain saved = domainRepo.save(existing);
        audit.audit("UPDATE", "catalog.domain", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/domains/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDomain(@PathVariable UUID id) {
        domainRepo.deleteById(id);
        audit.audit("DELETE", "catalog.domain", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/domains/tree")
    public ApiResponse<List<Map<String, Object>>> getDomainTree() {
        List<CatalogDomain> all = domainRepo.findAll();
        Map<UUID, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (CatalogDomain d : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("code", d.getCode());
            m.put("owner", d.getOwner());
            m.put("description", d.getDescription());
            m.put("parentId", d.getParent() != null ? d.getParent().getId() : null);
            m.put("children", new java.util.ArrayList<>());
            nodeMap.put(d.getId(), m);
        }
        List<Map<String, Object>> roots = new java.util.ArrayList<>();
        for (Map<String, Object> m : nodeMap.values()) {
            UUID parentId = (UUID) m.get("parentId");
            if (parentId != null && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> children = (java.util.List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(m);
            } else {
                roots.add(m);
            }
        }
        audit.audit("READ", "catalog.domain.tree", "tree");
        return ApiResponses.ok(roots);
    }

    @PostMapping("/domains/{id}/move")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> moveDomain(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        CatalogDomain d = domainRepo.findById(id).orElseThrow();
        Object newParentId = body.get("newParentId");
        if (newParentId == null || String.valueOf(newParentId).isBlank()) {
            d.setParent(null);
        } else {
            try {
                UUID pid = UUID.fromString(String.valueOf(newParentId));
                d.setParent(domainRepo.findById(pid).orElse(null));
            } catch (Exception ignored) {
                d.setParent(null);
            }
        }
        CatalogDomain saved = domainRepo.save(d);
        audit.audit("UPDATE", "catalog.domain.move", id.toString());
        return ApiResponses.ok(saved);
    }

    // Datasets CRUD with classification filtering
    @GetMapping("/datasets")
    public ApiResponse<Map<String, Object>> listDatasets(
        @RequestParam(required = false) UUID domainId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String classification,
        @RequestParam(required = false) String ownerDept,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String exposedBy,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        @RequestParam(value = "auditPurpose", required = false) String auditPurpose
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<CatalogDataset> p = datasetRepo.findAll(pageable);
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        List<Map<String, Object>> filtered = p
            .getContent()
            .stream()
            .filter(ds -> domainId == null || (ds.getDomain() != null && domainId.equals(ds.getDomain().getId())))
            .filter(ds -> keyword == null || keyword.isBlank() ||
                (ds.getName() != null && ds.getName().toLowerCase().contains(keyword.toLowerCase())) ||
                (ds.getOwner() != null && ds.getOwner().toLowerCase().contains(keyword.toLowerCase())) ||
                (ds.getTags() != null && ds.getTags().toLowerCase().contains(keyword.toLowerCase()))
            )
            .filter(ds -> classification == null || (ds.getClassification() != null && ds.getClassification().equalsIgnoreCase(classification)))
            .filter(ds -> type == null || (ds.getType() != null && ds.getType().equalsIgnoreCase(type)))
            .filter(ds -> ownerDept == null || (ds.getOwnerDept() != null && ds.getOwnerDept().equalsIgnoreCase(ownerDept)))
            .filter(ds -> exposedBy == null || (ds.getExposedBy() != null && ds.getExposedBy().equalsIgnoreCase(exposedBy)))
            .filter(ds -> owner == null || (ds.getOwner() != null && ds.getOwner().toLowerCase().contains(owner.toLowerCase())))
            .filter(ds -> tag == null || (ds.getTags() != null && ds.getTags().toLowerCase().contains(tag.toLowerCase())))
            // RBAC/level gate
            .filter(accessChecker::canRead)
            // Department gate using active context (headers injected by frontend)
            .filter(ds -> accessChecker.departmentAllowed(ds, effDept))
            .map(this::toDatasetDto)
            .toList();
        // Align with other list endpoints: total reflects the filtered list size
        long totalElements = p.getTotalElements();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", filtered);
        data.put("total", totalElements);
        data.put("page", p.getNumber());
        data.put("size", p.getSize());
        data.put("returned", filtered.size());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        String purpose = trimToNull(auditPurpose);
        String summary;
        String actionCode;
        if ("explore.workbench".equalsIgnoreCase(purpose)) {
            summary = "进入SQL查询工作台";
            actionCode = "EXPLORE_SQL_OPEN";
            auditPayload.put("datasetCount", filtered.size());
        } else if ("explore.preview".equalsIgnoreCase(purpose)) {
            summary = "进入查询结果预览";
            actionCode = "EXPLORE_RESULTSET_VIEW";
            auditPayload.put("datasetCount", filtered.size());
        } else {
            summary = "查看数据资产列表";
            actionCode = "CATALOG_ASSET_LIST";
            auditPayload.put("page", page);
            auditPayload.put("size", size);
            auditPayload.put("returned", filtered.size());
        }
        auditPayload.put("summary", summary);
        if (purpose != null) {
            auditPayload.put("purpose", purpose);
        }
        if (domainId != null) {
            auditPayload.put("domainId", domainId.toString());
        }
        putIfHasText(auditPayload, "keyword", keyword);
        putIfHasText(auditPayload, "classification", classification);
        putIfHasText(auditPayload, "ownerDept", ownerDept);
        putIfHasText(auditPayload, "type", type);
        putIfHasText(auditPayload, "exposedBy", exposedBy);
        putIfHasText(auditPayload, "owner", owner);
        putIfHasText(auditPayload, "tag", tag);
        String resourceRef = "CATALOG_ASSET_LIST".equals(actionCode) ? "page=" + page : null;
        audit.auditAction(actionCode, AuditStage.SUCCESS, resourceRef, auditPayload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/datasets/{id}")
    public ApiResponse<Map<String, Object>> getDataset(@PathVariable UUID id) {
        Map<String, Object> ds = datasetRepo
            .findById(id)
            .filter(accessChecker::canRead)
            .map(dataset -> toDatasetDto(dataset, true))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        String datasetName = safeText(ds.get("name"));
        auditPayload.put("summary", datasetName == null ? "查看数据资产详情" : "查看数据资产：" + datasetName);
        putIfHasText(auditPayload, "targetName", datasetName);
        auditPayload.put("datasetId", id.toString());
        putIfHasText(auditPayload, "classification", safeText(ds.get("classification")));
        putIfHasText(auditPayload, "ownerDept", safeText(ds.get("ownerDept")));
        putIfHasText(auditPayload, "owner", safeText(ds.get("owner")));
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(ds);
    }
    private Map<String, Object> toDatasetDto(CatalogDataset d) {
        return toDatasetDto(d, false);
    }

    private Map<String, Object> toDatasetDto(CatalogDataset d, boolean includeMetadata) {
        UUID domainId = d.getDomain() != null ? d.getDomain().getId() : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("domainId", domainId);
        m.put("type", d.getType());
        m.put("classification", d.getClassification());
        // ABAC fields (optional for backward compatibility)
        m.put("ownerDept", d.getOwnerDept());
        m.put("owner", d.getOwner());
        m.put("hiveDatabase", d.getHiveDatabase());
        m.put("hiveTable", d.getHiveTable());
        m.put("trinoCatalog", d.getTrinoCatalog());
        m.put("tags", d.getTags());
        m.put("exposedBy", d.getExposedBy());
        m.put("editable", canEditDataset(d));
        if (includeMetadata) {
            List<Map<String, Object>> tables = new ArrayList<>();
            tableRepo
                .findByDataset(d)
                .stream()
                .sorted(Comparator.comparing(table -> table.getName() != null ? table.getName().toLowerCase(Locale.ROOT) : ""))
                .forEach(table -> {
                    Map<String, Object> tableDto = new LinkedHashMap<>();
                    tableDto.put("id", table.getId());
                    tableDto.put("name", table.getName());
                    tableDto.put("tableName", table.getName());
                    List<Map<String, Object>> columnDtos = new ArrayList<>();
                    List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
                    columns
                        .stream()
                        .sorted(Comparator.comparing(col -> col.getName() != null ? col.getName().toLowerCase(Locale.ROOT) : ""))
                        .forEach(col -> {
                            Map<String, Object> colDto = new LinkedHashMap<>();
                            colDto.put("id", col.getId());
                            colDto.put("name", col.getName());
                            colDto.put("dataType", col.getDataType());
                            colDto.put("nullable", col.getNullable());
                            colDto.put("tags", col.getTags());
                            colDto.put("sensitiveTags", col.getSensitiveTags());
                            String columnComment = StringUtils.hasText(col.getComment()) ? col.getComment().trim() : null;
                            colDto.put("comment", columnComment);
                            colDto.put("description", columnComment);
                            if (columnComment != null) {
                                colDto.put("displayName", columnComment);
                            }
                            columnDtos.add(colDto);
                        });
                    tableDto.put("columns", columnDtos);
                    tables.add(tableDto);
                });
            m.put("tables", tables);
        }
        return m;
    }

    @PostMapping("/datasets")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDataset> createDataset(@Valid @RequestBody CatalogDataset dataset) {
        applySourcePolicy(dataset);
        normalizeClassification(dataset);
        applyOwnerDepartmentPolicy(dataset, null, false);
        ensurePrimarySourceIfRequired(dataset);
        ensureDatasetEditPermission(dataset);
        Map<String, Object> before = java.util.Collections.emptyMap();
        Map<String, Object> attempted = datasetSnapshot(dataset);
        try {
            CatalogDataset saved = datasetRepo.save(dataset);
            Map<String, Object> after = datasetSnapshot(saved);
            audit.auditAction(
                "CATALOG_ASSET_CREATE",
                AuditStage.SUCCESS,
                saved.getId().toString(),
                datasetChangePayload("新增数据资产：" + displayName(saved), before, after)
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            Map<String, Object> failureAfter = new LinkedHashMap<>(attempted);
            failureAfter.put("error", sanitize(ex.getMessage()));
            audit.auditAction(
                "CATALOG_ASSET_CREATE",
                AuditStage.FAIL,
                attempted.containsKey("id") ? String.valueOf(attempted.get("id")) : "",
                datasetChangePayload("新增数据资产失败：" + attempted.getOrDefault("name", ""), before, failureAfter)
            );
            throw ex;
        }
    }

    @PostMapping("/datasets/import")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importDatasets(@RequestBody List<CatalogDataset> items) {
        List<CatalogDataset> prepared = new ArrayList<>(items.size());
        for (CatalogDataset item : items) {
            applySourcePolicy(item);
            normalizeClassification(item);
            applyOwnerDepartmentPolicy(item, null, false);
            ensurePrimarySourceIfRequired(item);
            ensureDatasetEditPermission(item);
            prepared.add(item);
        }
        List<CatalogDataset> saved = datasetRepo.saveAll(prepared);
        audit.audit("CREATE", "catalog.dataset.import", "count=" + saved.size());
        return ApiResponses.ok(Map.of("imported", saved.size()));
    }

    @PutMapping("/datasets/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDataset> updateDataset(@PathVariable UUID id, @Valid @RequestBody CatalogDataset patch) {
        CatalogDataset existing = datasetRepo.findById(id).orElseThrow();
        ensureDatasetEditPermission(existing);
        Map<String, Object> before = datasetSnapshot(existing);
        try {
            String previousOwnerDept = existing.getOwnerDept();
            existing.setName(patch.getName());
            existing.setType(patch.getType());
            // Keep dataset type normalization, but do not hard-require primary source when updating
            // so that metadata changes（如 ownerDept）可以在缺少 Hive/Inceptor 的环境下保存。
            // Connectivity will still be enforced at execution time
            // (e.g., preview/sync operations).
            applySourcePolicy(existing);
            existing.setClassification(patch.getClassification());
            normalizeClassification(existing);
            existing.setOwnerDept(patch.getOwnerDept());
            applyOwnerDepartmentPolicy(existing, previousOwnerDept, true);
            existing.setOwner(patch.getOwner());
            existing.setDomain(patch.getDomain());
            existing.setHiveDatabase(patch.getHiveDatabase());
            existing.setHiveTable(patch.getHiveTable());
            existing.setTrinoCatalog(patch.getTrinoCatalog());
            existing.setTags(patch.getTags());
            existing.setExposedBy(patch.getExposedBy());
            CatalogDataset saved = datasetRepo.save(existing);
            Map<String, Object> after = datasetSnapshot(saved);
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.SUCCESS,
                id.toString(),
                datasetChangePayload("修改数据资产：" + displayName(saved), before, after)
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.FAIL,
                id.toString(),
                datasetChangePayload("修改数据资产失败：" + displayName(existing), before, Map.of("error", sanitize(ex.getMessage())))
            );
            throw ex;
        }
    }

    private Map<String, Object> datasetChangePayload(String summary, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(summary)) {
            payload.put("summary", summary);
        }
        payload.put("resourceType", "CATALOG_DATASET");
        if (before != null && !before.isEmpty()) {
            payload.put("before", before);
        }
        if (after != null && !after.isEmpty()) {
            payload.put("after", after);
        }
        Object targetId = after != null && after.containsKey("id")
            ? after.get("id")
            : before != null ? before.get("id") : null;
        if (targetId != null) {
            payload.put("targetId", targetId);
        }
        String resourceName = null;
        if (after != null && after.get("name") instanceof String afterName && StringUtils.hasText(afterName)) {
            resourceName = afterName.trim();
        } else if (before != null && before.get("name") instanceof String beforeName && StringUtils.hasText(beforeName)) {
            resourceName = beforeName.trim();
        }
        if (StringUtils.hasText(resourceName)) {
            payload.put("resourceName", resourceName);
            payload.put("targetName", resourceName);
        }
        return payload;
    }

    private Map<String, Object> datasetSnapshot(CatalogDataset dataset) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (dataset == null) {
            return snapshot;
        }
        if (dataset.getId() != null) {
            snapshot.put("id", dataset.getId().toString());
        }
        if (StringUtils.hasText(dataset.getName())) {
            snapshot.put("name", dataset.getName());
        }
        if (dataset.getDomain() != null) {
            CatalogDomain domain = dataset.getDomain();
            if (domain.getId() != null) {
                snapshot.put("domainId", domain.getId().toString());
            }
            if (StringUtils.hasText(domain.getName())) {
                snapshot.put("domainName", domain.getName());
            }
        }
        if (StringUtils.hasText(dataset.getType())) {
            snapshot.put("type", dataset.getType());
        }
        if (StringUtils.hasText(dataset.getClassification())) {
            snapshot.put("classification", dataset.getClassification());
        }
        if (StringUtils.hasText(dataset.getOwnerDept())) {
            snapshot.put("ownerDept", dataset.getOwnerDept());
        }
        if (StringUtils.hasText(dataset.getOwner())) {
            snapshot.put("owner", dataset.getOwner());
        }
        if (StringUtils.hasText(dataset.getHiveDatabase())) {
            snapshot.put("hiveDatabase", dataset.getHiveDatabase());
        }
        if (StringUtils.hasText(dataset.getHiveTable())) {
            snapshot.put("hiveTable", dataset.getHiveTable());
        }
        if (StringUtils.hasText(dataset.getTags())) {
            snapshot.put("tags", dataset.getTags());
        }
        if (StringUtils.hasText(dataset.getExposedBy())) {
            snapshot.put("exposedBy", dataset.getExposedBy());
        }
        if (StringUtils.hasText(dataset.getTrinoCatalog())) {
            snapshot.put("trinoCatalog", dataset.getTrinoCatalog());
        }
        return snapshot;
    }

    private String displayName(CatalogDataset dataset) {
        if (dataset == null) {
            return "";
        }
        if (StringUtils.hasText(dataset.getName())) {
            return dataset.getName();
        }
        return dataset.getId() != null ? dataset.getId().toString() : "";
    }

    private void applyOwnerDepartmentPolicy(CatalogDataset dataset, String previousOwnerDept, boolean enforceNoChangeForNonOp) {
        String trimmedPrevious = Optional.ofNullable(previousOwnerDept).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        String requested = Optional.ofNullable(dataset.getOwnerDept()).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);

        if (SecurityUtils.isOpAdminAccount()) {
            dataset.setOwnerDept(requested);
            return;
        }

        if (enforceNoChangeForNonOp && !Objects.equals(requested, trimmedPrevious)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅运维管理员可以调整数据资产归属部门");
        }

        dataset.setOwnerDept(trimmedPrevious);
    }

    @DeleteMapping("/datasets/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDataset(@PathVariable UUID id) {
        CatalogDataset existing = datasetRepo.findById(id).orElseThrow();
        ensureDatasetEditPermission(existing);
        Map<String, Object> before = datasetSnapshot(existing);
        try {
            datasetRepo.delete(existing);
            audit.auditAction(
                "CATALOG_ASSET_DELETE",
                AuditStage.SUCCESS,
                id.toString(),
                datasetChangePayload("删除数据资产：" + displayName(existing), before, Map.of("deleted", true))
            );
            return ApiResponses.ok(Boolean.TRUE);
        } catch (RuntimeException ex) {
            audit.auditAction(
                "CATALOG_ASSET_DELETE",
                AuditStage.FAIL,
                id.toString(),
                datasetChangePayload("删除数据资产失败：" + displayName(existing), before, Map.of("error", sanitize(ex.getMessage())))
            );
            throw ex;
        }
    }

    private void applySourcePolicy(CatalogDataset dataset) {
        String requested = Optional.ofNullable(dataset.getType()).map(String::trim).orElse("");
        String defaultSource = defaultSourceType();
        boolean multiSourceEnabled = catalogFeatures.isMultiSourceEnabled();

        if (!multiSourceEnabled) {
            if (!requested.isBlank() && !requested.equalsIgnoreCase(defaultSource)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "多数据源能力尚未解锁，请联系管理员升级");
            }
            dataset.setType(defaultSource);
            return;
        }

        if (requested.isBlank()) {
            dataset.setType(defaultSource);
        } else {
            dataset.setType(requested.toUpperCase(Locale.ROOT));
        }
    }

    private void normalizeClassification(CatalogDataset dataset) {
        String value = dataset.getClassification();
        DataLevel normalized = DataLevel.normalize(value);
        if (normalized != null) {
            dataset.setClassification(normalized.classification());
            return;
        }
        if (value != null && !value.isBlank()) {
            dataset.setClassification(value.trim().toUpperCase(Locale.ROOT));
        } else {
            dataset.setClassification("INTERNAL");
        }
    }

    private void ensurePrimarySourceIfRequired(CatalogDataset dataset) {
        if (isDefaultSource(dataset) && !hasPrimarySourceConfigured()) {
            throw new ResponseStatusException(
                HttpStatus.PRECONDITION_FAILED,
                "未检测到 Hive 数据源，请联系系统管理员！"
            );
        }
    }

    private boolean hasPrimarySourceConfigured() {
        String defaultSource = defaultSourceType();
        if (hasActiveDataSource(defaultSource)) {
            return true;
        }
        if (TYPE_INCEPTOR.equals(defaultSource)) {
            return hasActiveDataSource("HIVE");
        }
        return false;
    }

    private boolean isDefaultSource(CatalogDataset dataset) {
        return Optional.ofNullable(dataset.getType()).map(String::trim).map(s -> s.equalsIgnoreCase(defaultSourceType())).orElse(false);
    }

    private String defaultSourceType() {
        String configured = Optional
            .ofNullable(catalogFeatures.getDefaultSourceType())
            .map(String::trim)
            .map(String::toUpperCase)
            .filter(s -> !s.isBlank())
            .orElse(TYPE_INCEPTOR);
        if ("HIVE".equals(configured) || TYPE_INCEPTOR.equals(configured)) {
            if (hasActiveDataSource(TYPE_INCEPTOR) || hasActiveDataSource("HIVE")) {
                return TYPE_INCEPTOR;
            }
            if (hasActiveDataSource("POSTGRES")) {
                return "POSTGRES";
            }
            return TYPE_INCEPTOR;
        }
        if (hasActiveDataSource(configured)) {
            return configured;
        }
        if (hasActiveDataSource("POSTGRES")) {
            return "POSTGRES";
        }
        return configured;
    }

    private boolean hasActiveDataSource(String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }
        return infraDataSourceRepository
            .findFirstByTypeIgnoreCaseAndStatusIgnoreCase(type, "ACTIVE")
            .isPresent();
    }

    @GetMapping("/datasets/{id}/grants")
    public ApiResponse<List<Map<String, Object>>> listDatasetGrants(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
        ensureDatasetEditPermission(dataset);
        List<Map<String, Object>> list = grantRepo
            .findByDatasetIdOrderByCreatedDateAsc(id)
            .stream()
            .map(this::toGrantDto)
            .toList();
        audit.audit("READ", "catalog.dataset.grant", id.toString());
        return ApiResponses.ok(list);
    }

    @PostMapping("/datasets/{id}/grants")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createDatasetGrant(
        @PathVariable UUID id,
        @RequestBody(required = false) DatasetGrantRequest body
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow();
        ensureDatasetEditPermission(dataset);
        if (!canManageGrants()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅研究所数据管理员可分配访问权限");
        }
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String rawUsername = body.username();
        if (!StringUtils.hasText(rawUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        final String username = rawUsername.trim();
        String userId = StringUtils.hasText(body.userId()) ? body.userId().trim() : null;
        final String normalizedUserId = userId;
        final String normalizedUsername = username;
        String displayName = StringUtils.hasText(body.displayName()) ? body.displayName().trim() : null;
        String deptCode = StringUtils.hasText(body.deptCode()) ? body.deptCode().trim() : null;
        if (grantRepo.existsForDatasetAndUser(id, normalizedUserId, normalizedUsername)) {
            CatalogDatasetGrant existing = grantRepo
                .findByDatasetIdOrderByCreatedDateAsc(id)
                .stream()
                .filter(g ->
                    (normalizedUserId != null && normalizedUserId.equals(g.getGranteeId())) ||
                    normalizedUsername.equalsIgnoreCase(g.getGranteeUsername())
                )
                .findFirst()
                .orElse(null);
            return ApiResponses.ok(existing != null ? toGrantDto(existing) : Map.of("username", normalizedUsername, "duplicate", Boolean.TRUE));
        }
        CatalogDatasetGrant grant = new CatalogDatasetGrant();
        grant.setDataset(dataset);
        grant.setGranteeId(normalizedUserId);
        grant.setGranteeUsername(username);
        grant.setGranteeName(displayName);
        grant.setGranteeDept(deptCode);
        CatalogDatasetGrant saved = grantRepo.save(grant);
        audit.audit("CREATE", "catalog.dataset.grant", saved.getId().toString());
        return ApiResponses.ok(toGrantDto(saved));
    }

    @DeleteMapping("/datasets/{datasetId}/grants/{grantId}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDatasetGrant(@PathVariable UUID datasetId, @PathVariable UUID grantId) {
        CatalogDatasetGrant grant = grantRepo.findById(grantId).orElseThrow();
        CatalogDataset dataset = grant.getDataset();
        if (dataset == null || dataset.getId() == null || !datasetId.equals(dataset.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "授权记录与数据集不匹配");
        }
        ensureDatasetEditPermission(dataset);
        if (!canManageGrants()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅研究所数据管理员可修改访问权限");
        }
        grantRepo.deleteById(grantId);
        audit.audit("DELETE", "catalog.dataset.grant", grantId.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Masking rules CRUD
    @GetMapping("/masking-rules")
    public ApiResponse<List<CatalogMaskingRule>> listMaskingRules() {
        List<CatalogMaskingRule> list = maskingRepo.findAll();
        audit.audit("READ", "catalog.masking", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/masking-rules")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogMaskingRule> createMasking(@Valid @RequestBody CatalogMaskingRule rule) {
        CatalogMaskingRule saved = maskingRepo.save(rule);
        audit.audit("CREATE", "catalog.masking", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/masking-rules/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogMaskingRule> updateMasking(@PathVariable UUID id, @Valid @RequestBody CatalogMaskingRule patch) {
        CatalogMaskingRule existing = maskingRepo.findById(id).orElseThrow();
        existing.setColumn(patch.getColumn());
        existing.setFunction(patch.getFunction());
        existing.setArgs(patch.getArgs());
        CatalogMaskingRule saved = maskingRepo.save(existing);
        audit.audit("UPDATE", "catalog.masking", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/masking-rules/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteMasking(@PathVariable UUID id) {
        maskingRepo.deleteById(id);
        audit.audit("DELETE", "catalog.masking", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/masking-rules/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        String function = Objects.toString(body.get("function"), "");
        String value = Objects.toString(body.get("value"), "");
        String result = switch (function) {
            case "hash" -> Integer.toHexString(Objects.hashCode(value));
            case "mask_email" -> value.replaceAll("(^.).*(@.*$)", "$1***$2");
            case "mask_phone" -> value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
            default -> value;
        };
        Map<String, Object> resp = Map.of("input", value, "function", function, "output", result);
        audit.audit("EXECUTE", "catalog.masking.preview", function);
        return ApiResponses.ok(resp);
    }

    // Classification mapping
    @GetMapping("/classification-mapping")
    public ApiResponse<List<CatalogClassificationMapping>> getMapping() {
        List<CatalogClassificationMapping> list = mappingRepo.findAll();
        audit.audit("READ", "catalog.classificationMapping", "list");
        return ApiResponses.ok(list);
    }

    @PutMapping("/classification-mapping")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<CatalogClassificationMapping>> replaceMapping(
        @RequestBody List<CatalogClassificationMapping> items
    ) {
        mappingRepo.deleteAll();
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(items);
        audit.audit("UPDATE", "catalog.classificationMapping", "replace:" + saved.size());
        return ApiResponses.ok(saved);
    }

    @PostMapping("/classification-mapping/import")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importMapping(@RequestBody List<CatalogClassificationMapping> items) {
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(items);
        audit.audit("CREATE", "catalog.classificationMapping", "import:" + saved.size());
        return ApiResponses.ok(Map.of("imported", saved.size()));
    }

    @GetMapping("/classification-mapping/export")
    public ApiResponse<List<CatalogClassificationMapping>> exportMapping() {
        List<CatalogClassificationMapping> list = mappingRepo.findAll();
        audit.audit("READ", "catalog.classificationMapping", "export");
        return ApiResponses.ok(list);
    }

    // Table schema CRUD, filter and bulk import
    @GetMapping("/tables")
    public ApiResponse<Map<String, Object>> listTables(@RequestParam UUID datasetId, @RequestParam(required = false) String keyword) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        var list = tableRepo.findByDataset(ds);
        var filtered = list
            .stream()
            .filter(t -> keyword == null || keyword.isBlank() ||
                (t.getName() != null && t.getName().toLowerCase().contains(keyword.toLowerCase())) ||
                (t.getTags() != null && t.getTags().toLowerCase().contains(keyword.toLowerCase()))
            )
            .toList();
        audit.audit("READ", "catalog.table", String.valueOf(datasetId));
        return ApiResponses.ok(Map.of("content", filtered, "total", filtered.size()));
    }

    @PostMapping("/tables")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema> createTable(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema table
    ) {
        var saved = tableRepo.save(table);
        audit.audit("CREATE", "catalog.table", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema> updateTable(
        @PathVariable UUID id,
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema patch
    ) {
        var existing = tableRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setOwner(patch.getOwner());
        existing.setClassification(patch.getClassification());
        existing.setBizDomain(patch.getBizDomain());
        existing.setTags(patch.getTags());
        var saved = tableRepo.save(existing);
        audit.audit("UPDATE", "catalog.table", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/tables/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteTable(@PathVariable UUID id) {
        tableRepo.deleteById(id);
        audit.audit("DELETE", "catalog.table", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/tables/import")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importTables(@RequestBody List<Map<String, Object>> payload) {
        int importedTables = 0;
        int importedColumns = 0;
        for (Map<String, Object> t : payload) {
            Object dsId = t.get("datasetId");
            if (dsId == null) continue;
            java.util.UUID datasetId = java.util.UUID.fromString(String.valueOf(dsId));
            var ds = datasetRepo.findById(datasetId).orElse(null);
            if (ds == null) continue;
            var table = new com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema();
            table.setDataset(ds);
            table.setName(Objects.toString(t.get("name"), null));
            table.setOwner(Objects.toString(t.get("owner"), null));
            table.setClassification(Objects.toString(t.get("classification"), null));
            table.setBizDomain(Objects.toString(t.get("bizDomain"), null));
            table.setTags(Objects.toString(t.get("tags"), null));
            var savedTable = tableRepo.save(table);
            importedTables++;
            Object cols = t.get("columns");
            if (cols instanceof java.util.List<?> list) {
                for (Object c : list) {
                    if (!(c instanceof Map)) continue;
                    Map<?, ?> cm = (Map<?, ?>) c;
                    var col = new com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema();
                    col.setTable(savedTable);
                    col.setName(Objects.toString(cm.get("name"), null));
                    col.setDataType(Objects.toString(cm.get("dataType"), null));
                    Object nullable = cm.get("nullable");
                    col.setNullable(nullable == null || Boolean.parseBoolean(String.valueOf(nullable)));
                    col.setTags(Objects.toString(cm.get("tags"), null));
                    String comment = Objects.toString(cm.get("displayName"), null);
                    if (!StringUtils.hasText(comment)) {
                        comment = Objects.toString(cm.get("comment"), null);
                    }
                    if (!StringUtils.hasText(comment)) {
                        comment = Objects.toString(cm.get("description"), null);
                    }
                    col.setComment(StringUtils.hasText(comment) ? comment : null);
                    col.setSensitiveTags(Objects.toString(cm.get("sensitiveTags"), null));
                    columnRepo.save(col);
                    importedColumns++;
                }
            }
        }
        audit.audit("CREATE", "catalog.table.import", "tables=" + importedTables + ", cols=" + importedColumns);
        return ApiResponses.ok(Map.of("tables", importedTables, "columns", importedColumns));
    }

    // Column schema CRUD
    @GetMapping("/columns")
    public ApiResponse<Map<String, Object>> listColumns(@RequestParam UUID tableId, @RequestParam(required = false) String keyword) {
        var table = tableRepo.findById(tableId).orElseThrow();
        var list = columnRepo.findByTable(table);
        var filtered = list
            .stream()
            .filter(c -> keyword == null || keyword.isBlank() ||
                (c.getName() != null && c.getName().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getTags() != null && c.getTags().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getSensitiveTags() != null && c.getSensitiveTags().toLowerCase().contains(keyword.toLowerCase())) ||
                (c.getComment() != null && c.getComment().toLowerCase().contains(keyword.toLowerCase()))
            )
            .toList();
        audit.audit("READ", "catalog.column", String.valueOf(tableId));
        return ApiResponses.ok(Map.of("content", filtered, "total", filtered.size()));
    }

    @PostMapping("/columns")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema> createColumn(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema column
    ) {
        var saved = columnRepo.save(column);
        audit.audit("CREATE", "catalog.column", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/columns/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema> updateColumn(
        @PathVariable UUID id,
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema patch
    ) {
        var existing = columnRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setDataType(patch.getDataType());
        existing.setNullable(patch.getNullable());
        existing.setTags(patch.getTags());
        existing.setComment(patch.getComment());
        existing.setSensitiveTags(patch.getSensitiveTags());
        var saved = columnRepo.save(existing);
        audit.audit("UPDATE", "catalog.column", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/columns/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteColumn(@PathVariable UUID id) {
        columnRepo.deleteById(id);
        audit.audit("DELETE", "catalog.column", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    // Row filter rules CRUD
    @GetMapping("/row-filter-rules")
    public ApiResponse<List<com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule>> listRowFilters(@RequestParam UUID datasetId) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        var list = rowFilterRepo.findByDataset(ds);
        audit.audit("READ", "catalog.rowFilter", String.valueOf(datasetId));
        return ApiResponses.ok(list);
    }

    @PostMapping("/row-filter-rules")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule> createRowFilter(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule rule
    ) {
        var saved = rowFilterRepo.save(rule);
        audit.audit("CREATE", "catalog.rowFilter", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/row-filter-rules/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule> updateRowFilter(
        @PathVariable UUID id,
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule patch
    ) {
        var existing = rowFilterRepo.findById(id).orElseThrow();
        existing.setRoles(patch.getRoles());
        existing.setExpression(patch.getExpression());
        var saved = rowFilterRepo.save(existing);
        audit.audit("UPDATE", "catalog.rowFilter", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/row-filter-rules/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteRowFilter(@PathVariable UUID id) {
        rowFilterRepo.deleteById(id);
        audit.audit("DELETE", "catalog.rowFilter", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    private boolean canManageGrants() {
        return (
            SecurityUtils.isOpAdminAccount() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(
                AuthoritiesConstants.DATA_MAINTAINER_ROLES
            )
        );
    }

    private void ensureDatasetEditPermission(CatalogDataset dataset) {
        if (canEditDataset(dataset)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户无权编辑该数据集");
    }

    private boolean canEditDataset(CatalogDataset dataset) {
        if (dataset == null) {
            return false;
        }
        if (
            SecurityUtils.isOpAdminAccount() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(
                AuthoritiesConstants.DATA_MAINTAINER_ROLES
            )
        ) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INST_DATA_OWNER)) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPT_DATA_OWNER)) {
            String userDept = claim("dept_code");
            if (userDept == null || userDept.isBlank()) {
                return false;
            }
            return DepartmentUtils.matches(dataset.getOwnerDept(), userDept);
        }
        return false;
    }

    private Map<String, Object> toGrantDto(CatalogDatasetGrant grant) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", grant.getId());
        map.put("datasetId", grant.getDataset() != null ? grant.getDataset().getId() : null);
        map.put("userId", grant.getGranteeId());
        map.put("username", grant.getGranteeUsername());
        map.put("displayName", grant.getGranteeName());
        map.put("deptCode", grant.getGranteeDept());
        map.put("createdBy", grant.getCreatedBy());
        map.put("createdDate", grant.getCreatedDate());
        return map;
    }

    private record DatasetGrantRequest(String userId, String username, String displayName, String deptCode) {}

    private void putIfHasText(Map<String, Object> target, String key, Object raw) {
        if (target == null || key == null) {
            return;
        }
        String text = safeText(raw);
        if (text != null) {
            target.put(key, text);
        }
    }

    private String safeText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    private String sanitize(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }
}
