package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.repository.catalog.CatalogClassificationMappingRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogMetadataService;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogDatasetResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDomainRepository domainRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final CatalogClassificationMappingRepository mappingRepo;
    private final CatalogTableSchemaRepository tableSchemaRepo;
    private final CatalogColumnSchemaRepository columnSchemaRepo;
    private final AuditService audit;
    private final CatalogFeatureProperties catalogFeatures;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final OpenMetadataService openMetadataService;
    private final CatalogMetadataService catalogMetadataService;
    private final CatalogResourceHelper helper;

    public CatalogDatasetResource(
        CatalogDatasetRepository datasetRepo,
        CatalogDomainRepository domainRepo,
        CatalogMaskingRuleRepository maskingRepo,
        CatalogClassificationMappingRepository mappingRepo,
        CatalogTableSchemaRepository tableSchemaRepo,
        CatalogColumnSchemaRepository columnSchemaRepo,
        AuditService audit,
        CatalogFeatureProperties catalogFeatures,
        OrganizationVisibilityService organizationVisibilityService,
        OpenMetadataService openMetadataService,
        CatalogMetadataService catalogMetadataService,
        CatalogResourceHelper helper
    ) {
        this.datasetRepo = datasetRepo;
        this.domainRepo = domainRepo;
        this.maskingRepo = maskingRepo;
        this.mappingRepo = mappingRepo;
        this.tableSchemaRepo = tableSchemaRepo;
        this.columnSchemaRepo = columnSchemaRepo;
        this.audit = audit;
        this.catalogFeatures = catalogFeatures;
        this.organizationVisibilityService = organizationVisibilityService;
        this.openMetadataService = openMetadataService;
        this.catalogMetadataService = catalogMetadataService;
        this.helper = helper;
    }

    @GetMapping("/config")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("multiSourceEnabled", catalogFeatures.isMultiSourceEnabled());
        payload.put("defaultSourceType", helper.defaultSourceType());
        payload.put("hasPrimarySource", helper.hasPrimarySourceConfigured());
        payload.put("primarySourceType", helper.defaultSourceType());
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
    @Transactional(readOnly = true)
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

    @GetMapping("/datasets")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> listDatasets(
        @RequestParam(required = false) UUID domainId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String classification,
        @RequestParam(required = false) String ownerDept,
        @RequestParam(required = false) String warehouseLayer,
        @RequestParam(required = false, defaultValue = "true") boolean enabledOnly,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) UUID sourceId,
        @RequestParam(required = false) String exposedBy,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false, defaultValue = "desc") String sortDir,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept,
        @RequestParam(value = "auditPurpose", required = false) String auditPurpose
    ) {
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String safeSortBy = helper.normalizeDatasetSortBy(sortBy);
        boolean asc = "asc".equalsIgnoreCase(helper.trimToNull(sortDir));
        Sort sort = asc ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        if (!"createdDate".equals(safeSortBy)) {
            sort = sort.and(Sort.by("createdDate").descending());
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        long queryStartedAt = System.currentTimeMillis();
        Page<CatalogDataset> pageData = datasetRepo.findAll(
            helper.buildDatasetListSpecification(
                domainId,
                sourceId,
                keyword,
                classification,
                ownerDept,
                warehouseLayer,
                enabledOnly,
                type,
                exposedBy,
                owner,
                tag
            ),
            pageable
        );
        long queryCostMs = Math.max(0, System.currentTimeMillis() - queryStartedAt);
        List<Map<String, Object>> content = pageData.getContent().stream().map(helper::toDatasetDto).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", content);
        data.put("total", pageData.getTotalElements());
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("returned", content.size());
        data.put("sortBy", safeSortBy);
        data.put("sortDir", asc ? "asc" : "desc");
        data.put("queryCostMs", queryCostMs);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        String purpose = helper.trimToNull(auditPurpose);
        String summary;
        String actionCode;
        if ("explore.workbench".equalsIgnoreCase(purpose)) {
            summary = "进入SQL查询工作台";
            actionCode = "EXPLORE_SQL_OPEN";
            auditPayload.put("datasetCount", content.size());
        } else if ("explore.preview".equalsIgnoreCase(purpose)) {
            summary = "进入查询结果预览";
            actionCode = "EXPLORE_RESULTSET_VIEW";
            auditPayload.put("datasetCount", content.size());
        } else {
            summary = "查看数据资产列表";
            actionCode = "CATALOG_ASSET_LIST";
            auditPayload.put("page", page);
            auditPayload.put("size", size);
            auditPayload.put("returned", content.size());
        }
        auditPayload.put("summary", summary);
        if (purpose != null) {
            auditPayload.put("purpose", purpose);
        }
        if (domainId != null) {
            auditPayload.put("domainId", domainId.toString());
        }
        if (sourceId != null) {
            auditPayload.put("sourceId", sourceId.toString());
        }
        helper.putIfHasText(auditPayload, "keyword", keyword);
        helper.putIfHasText(auditPayload, "classification", classification);
        helper.putIfHasText(auditPayload, "ownerDept", ownerDept);
        helper.putIfHasText(auditPayload, "warehouseLayer", warehouseLayer);
        auditPayload.put("enabledOnly", enabledOnly);
        helper.putIfHasText(auditPayload, "type", type);
        helper.putIfHasText(auditPayload, "exposedBy", exposedBy);
        helper.putIfHasText(auditPayload, "owner", owner);
        helper.putIfHasText(auditPayload, "tag", tag);
        auditPayload.put("sortBy", safeSortBy);
        auditPayload.put("sortDir", asc ? "asc" : "desc");
        auditPayload.put("queryCostMs", queryCostMs);
        String resourceRef = "CATALOG_ASSET_LIST".equals(actionCode) ? "page=" + page : null;
        audit.auditAction(actionCode, AuditStage.SUCCESS, resourceRef, auditPayload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/datasets/{id}/fields")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> getDatasetFields(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在")
        );
        List<CatalogTableSchema> tables = tableSchemaRepo.findByDataset(dataset);
        List<Map<String, Object>> fields = tables.stream()
            .flatMap(table -> columnSchemaRepo.findByTable(table).stream()
                .map(col -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name", col.getName());
                    m.put("dataType", col.getDataType());
                    m.put("comment", col.getComment());
                    m.put("nullable", col.getNullable());
                    m.put("tableName", table.getName());
                    return m;
                }))
            .toList();
        return ApiResponses.ok(fields);
    }

    @GetMapping("/datasets/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getDataset(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> ds = helper.toDatasetDto(dataset, true);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        String datasetName = helper.safeText(ds.get("name"));
        auditPayload.put("summary", datasetName == null ? "查看数据资产详情" : "查看数据资产：" + datasetName);
        helper.putIfHasText(auditPayload, "targetName", datasetName);
        auditPayload.put("datasetId", id.toString());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        helper.putIfHasText(auditPayload, "classification", helper.safeText(ds.get("classification")));
        helper.putIfHasText(auditPayload, "ownerDept", helper.safeText(ds.get("ownerDept")));
        helper.putIfHasText(auditPayload, "owner", helper.safeText(ds.get("owner")));
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(ds);
    }

    @GetMapping("/datasets/{id}/openmetadata")
    @Transactional(readOnly = true)
    public ApiResponse<OpenMetadataService.OpenMetadataResult> getDatasetOpenMetadata(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看元数据信息");
        auditPayload.put("datasetId", id.toString());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchTableForDataset(dataset));
    }

    @GetMapping("/metadata/tables")
    @Transactional(readOnly = true)
    public ApiResponse<OpenMetadataService.OpenMetadataTablePage> listTechMetadataTables(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "size", required = false, defaultValue = "50") int size,
        @RequestParam(value = "sourceId", required = false) UUID sourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        OpenMetadataService.OpenMetadataTablePage localPage = catalogMetadataService.listLocalTables(keyword, size, effDept, sourceId);
        boolean sourceScoped = sourceId != null;
        boolean useLocal = sourceScoped || (localPage != null && localPage.items() != null && !localPage.items().isEmpty());
        OpenMetadataService.OpenMetadataTablePage page = useLocal ? localPage : openMetadataService.searchTables(keyword, size);
        boolean disabled = page == null || !page.enabled();
        if (!sourceScoped && !useLocal && (disabled || page.items() == null || page.items().isEmpty())) {
            OpenMetadataService.OpenMetadataTablePage fallback = catalogMetadataService.listLocalTables(keyword, size, effDept, null);
            if (fallback != null && fallback.items() != null && !fallback.items().isEmpty()) {
                page = fallback;
                useLocal = true;
                disabled = false;
            }
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "浏览元数据资产");
        if (keyword != null && !keyword.isBlank()) {
            auditPayload.put("keyword", keyword);
        }
        auditPayload.put("size", size);
        if (sourceId != null) {
            auditPayload.put("sourceId", sourceId.toString());
        }
        auditPayload.put("source", useLocal ? "catalog" : (disabled ? "disabled" : "openmetadata"));
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "tech-metadata", auditPayload);
        return ApiResponses.ok(page);
    }

    @GetMapping("/metadata/tables/detail")
    @Transactional(readOnly = true)
    public ApiResponse<OpenMetadataService.OpenMetadataResult> getTechMetadataTableDetail(
        @RequestParam("fqn") String fqn,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        OpenMetadataService.OpenMetadataResult result;
        boolean useLocal = catalogMetadataService.isLocalFqn(fqn);
        if (useLocal) {
            result = catalogMetadataService.fetchLocalTableDetail(fqn, effDept);
        } else {
            result = openMetadataService.fetchTableByFqn(fqn);
            boolean disabled = result == null || !result.enabled();
            if (disabled || (result != null && !result.found())) {
                OpenMetadataService.OpenMetadataResult local = catalogMetadataService.fetchLocalTableDetail(fqn, effDept);
                if (local != null && local.found()) {
                    result = local;
                    useLocal = true;
                }
            }
        }
        boolean disabled = result == null || !result.enabled();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看元数据详情");
        auditPayload.put("fqn", fqn);
        auditPayload.put("source", useLocal ? "catalog" : (disabled ? "disabled" : "openmetadata"));
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "tech-metadata-detail", auditPayload);
        return ApiResponses.ok(result);
    }

    @GetMapping("/datasets/{id}/lineage")
    @Transactional(readOnly = true)
    public ApiResponse<OpenMetadataService.OpenMetadataLineageResult> getDatasetLineage(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集血缘");
        auditPayload.put("datasetId", id.toString());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchLineageForDataset(dataset, 2, 2));
    }

    @GetMapping("/datasets/{id}/quality")
    @Transactional(readOnly = true)
    public ApiResponse<OpenMetadataService.OpenMetadataQualityResult> getDatasetQuality(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集质量");
        auditPayload.put("datasetId", id.toString());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchQualityForDataset(dataset));
    }

    @PostMapping("/quality/batch")
    @Transactional
    public ApiResponse<Map<String, OpenMetadataService.OpenMetadataQualitySummary>> batchDatasetQuality(
        @RequestBody CatalogResourceHelper.OpenMetadataBatchRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<UUID> ids = body != null && body.ids() != null ? body.ids() : List.of();
        if (ids.isEmpty()) {
            return ApiResponses.ok(Map.of());
        }
        if (ids.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量请求过大");
        }
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        Map<String, OpenMetadataService.OpenMetadataQualitySummary> payload = new LinkedHashMap<>();
        List<CatalogDataset> datasets = datasetRepo.findAllById(ids);
        for (CatalogDataset dataset : datasets) {
            if (dataset == null || dataset.getId() == null) {
                continue;
            }
            if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
                continue;
            }
            OpenMetadataService.OpenMetadataQualityResult result = openMetadataService.fetchQualityForDataset(dataset);
            payload.put(dataset.getId().toString(), openMetadataService.summarizeQuality(result));
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "批量查询质量结果");
        auditPayload.put("count", payload.size());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "batch-quality", auditPayload);
        return ApiResponses.ok(payload);
    }

    @PostMapping("/datasets/openmetadata/batch")
    @Transactional
    public ApiResponse<Map<String, OpenMetadataService.OpenMetadataSummary>> batchOpenMetadata(
        @RequestBody CatalogResourceHelper.OpenMetadataBatchRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<UUID> ids = body != null && body.ids() != null ? body.ids() : List.of();
        if (ids.isEmpty()) {
            return ApiResponses.ok(Map.of());
        }
        if (ids.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量请求过大");
        }
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        Map<String, OpenMetadataService.OpenMetadataSummary> payload = new LinkedHashMap<>();
        List<CatalogDataset> datasets = datasetRepo.findAllById(ids);
        for (CatalogDataset dataset : datasets) {
            if (dataset == null || dataset.getId() == null) {
                continue;
            }
            if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
                continue;
            }
            OpenMetadataService.OpenMetadataResult result = openMetadataService.fetchTableForDataset(dataset);
            payload.put(dataset.getId().toString(), openMetadataService.summarize(result));
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "批量查询元数据信息");
        auditPayload.put("count", payload.size());
        helper.putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "batch-openmetadata", auditPayload);
        return ApiResponses.ok(payload);
    }

    @PostMapping("/datasets")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDataset> createDataset(@Valid @RequestBody CatalogDataset dataset) {
        helper.applySourcePolicy(dataset);
        helper.normalizeClassification(dataset);
        helper.normalizeWarehouseLayer(dataset);
        dataset.setEnabled(dataset.getEnabled() != null ? Boolean.TRUE.equals(dataset.getEnabled()) : Boolean.TRUE);
        helper.applyOwnerDepartmentPolicy(dataset, null, false);
        helper.ensurePrimarySourceIfRequired(dataset);
        helper.ensureDatasetEditPermission(dataset);
        Map<String, Object> before = java.util.Collections.emptyMap();
        Map<String, Object> attempted = helper.datasetSnapshot(dataset);
        try {
            CatalogDataset saved = datasetRepo.save(dataset);
            Map<String, Object> after = helper.datasetSnapshot(saved);
            audit.auditAction(
                "CATALOG_ASSET_CREATE",
                AuditStage.SUCCESS,
                saved.getId().toString(),
                helper.datasetChangePayload("新增数据资产：" + helper.displayName(saved), before, after)
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            Map<String, Object> failureAfter = new LinkedHashMap<>(attempted);
            failureAfter.put("error", helper.sanitize(ex.getMessage()));
            audit.auditAction(
                "CATALOG_ASSET_CREATE",
                AuditStage.FAIL,
                attempted.containsKey("id") ? String.valueOf(attempted.get("id")) : "",
                helper.datasetChangePayload("新增数据资产失败：" + attempted.getOrDefault("name", ""), before, failureAfter)
            );
            throw ex;
        }
    }

    @PostMapping("/datasets/import")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importDatasets(@RequestBody List<CatalogDataset> items) {
        List<CatalogDataset> prepared = new ArrayList<>(items.size());
        for (CatalogDataset item : items) {
            helper.applySourcePolicy(item);
            helper.normalizeClassification(item);
            helper.normalizeWarehouseLayer(item);
            item.setEnabled(item.getEnabled() != null ? Boolean.TRUE.equals(item.getEnabled()) : Boolean.TRUE);
            helper.applyOwnerDepartmentPolicy(item, null, false);
            helper.ensurePrimarySourceIfRequired(item);
            helper.ensureDatasetEditPermission(item);
            prepared.add(item);
        }
        List<CatalogDataset> saved = datasetRepo.saveAll(prepared);
        audit.audit("CREATE", "catalog.dataset.import", "count=" + saved.size());
        return ApiResponses.ok(Map.of("imported", saved.size()));
    }

    @PutMapping("/datasets/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDataset> updateDataset(@PathVariable UUID id, @Valid @RequestBody CatalogDataset patch) {
        CatalogDataset existing = datasetRepo.findById(id).orElseThrow();
        helper.ensureDatasetEditPermission(existing);
        Map<String, Object> before = helper.datasetSnapshot(existing);
        try {
            String previousOwnerDept = existing.getOwnerDept();
            existing.setName(patch.getName());
            existing.setType(patch.getType());
            helper.applySourcePolicy(existing);
            existing.setClassification(patch.getClassification());
            helper.normalizeClassification(existing);
            existing.setOwnerDept(patch.getOwnerDept());
            helper.applyOwnerDepartmentPolicy(existing, previousOwnerDept, true);
            existing.setOwner(patch.getOwner());
            existing.setDomain(patch.getDomain());
            existing.setHiveDatabase(patch.getHiveDatabase());
            existing.setHiveTable(patch.getHiveTable());
            existing.setTrinoCatalog(patch.getTrinoCatalog());
            existing.setTags(patch.getTags());
            existing.setDescription(helper.trimToNull(patch.getDescription()));
            existing.setWarehouseLayer(helper.trimToNull(patch.getWarehouseLayer()));
            helper.normalizeWarehouseLayer(existing);
            if (patch.getEnabled() != null) {
                existing.setEnabled(Boolean.TRUE.equals(patch.getEnabled()));
            }
            existing.setExposedBy(patch.getExposedBy());
            existing.setLifecycleStatus(helper.trimToNull(patch.getLifecycleStatus()));
            existing.setRetentionDays(patch.getRetentionDays());
            existing.setExpiresAt(patch.getExpiresAt());
            CatalogDataset saved = datasetRepo.save(existing);
            Map<String, Object> after = helper.datasetSnapshot(saved);
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.SUCCESS,
                id.toString(),
                helper.datasetChangePayload("修改数据资产：" + helper.displayName(saved), before, after)
            );
            return ApiResponses.ok(saved);
        } catch (RuntimeException ex) {
            audit.auditAction(
                "CATALOG_ASSET_EDIT",
                AuditStage.FAIL,
                id.toString(),
                helper.datasetChangePayload("修改数据资产失败：" + helper.displayName(existing), before, Map.of("error", helper.sanitize(ex.getMessage())))
            );
            throw ex;
        }
    }

    @PostMapping("/datasets/{id}/publish")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> publishDataset(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        helper.ensureDatasetEditPermission(dataset);
        Map<String, Object> before = helper.datasetSnapshot(dataset);
        dataset.setEnabled(Boolean.TRUE);
        CatalogDataset saved = datasetRepo.save(dataset);
        Map<String, Object> after = helper.datasetSnapshot(saved);
        audit.auditAction(
            "CATALOG_ASSET_PUBLISH",
            AuditStage.SUCCESS,
            id.toString(),
            helper.datasetChangePayload("发布数据资产：" + helper.displayName(saved), before, after)
        );
        return ApiResponses.ok(Map.of("id", id.toString(), "enabled", Boolean.TRUE));
    }

    @PostMapping("/datasets/{id}/offline")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> offlineDataset(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        helper.ensureDatasetEditPermission(dataset);
        Map<String, Object> before = helper.datasetSnapshot(dataset);
        dataset.setEnabled(Boolean.FALSE);
        CatalogDataset saved = datasetRepo.save(dataset);
        Map<String, Object> after = helper.datasetSnapshot(saved);
        audit.auditAction(
            "CATALOG_ASSET_OFFLINE",
            AuditStage.SUCCESS,
            id.toString(),
            helper.datasetChangePayload("下线数据资产：" + helper.displayName(saved), before, after)
        );
        return ApiResponses.ok(Map.of("id", id.toString(), "enabled", Boolean.FALSE));
    }

    @DeleteMapping("/datasets/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDataset(@PathVariable UUID id) {
        CatalogDataset existing = datasetRepo.findById(id).orElseThrow();
        helper.ensureDatasetEditPermission(existing);
        Map<String, Object> before = helper.datasetSnapshot(existing);
        try {
            datasetRepo.delete(existing);
            audit.auditAction(
                "CATALOG_ASSET_DELETE",
                AuditStage.SUCCESS,
                id.toString(),
                helper.datasetChangePayload("删除数据资产：" + helper.displayName(existing), before, Map.of("deleted", true))
            );
            return ApiResponses.ok(Boolean.TRUE);
        } catch (RuntimeException ex) {
            audit.auditAction(
                "CATALOG_ASSET_DELETE",
                AuditStage.FAIL,
                id.toString(),
                helper.datasetChangePayload("删除数据资产失败：" + helper.displayName(existing), before, Map.of("error", helper.sanitize(ex.getMessage())))
            );
            throw ex;
        }
    }
}
