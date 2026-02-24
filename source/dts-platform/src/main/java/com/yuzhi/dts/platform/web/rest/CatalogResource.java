package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.repository.governance.GovIssueTicketRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogMetadataService;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import com.yuzhi.dts.platform.service.openmetadata.OpenMetadataService;
import jakarta.validation.Valid;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Array;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private static final Pattern STD_CODE_PATTERN = Pattern.compile("(?i)(?:\\bSTD\\b|标准)\\s*[:：]\\s*([A-Za-z0-9_\\-\\.]+)");
    private static final List<String> CLASSIFICATION_LEVEL_ORDER = List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");
    private static final Set<String> SUPPORTED_CLASSIFICATION_LEVELS = Set.copyOf(CLASSIFICATION_LEVEL_ORDER);
    private static final List<String> ISSUE_OPEN_STATUSES = List.of("OPEN", "NEW", "IN_PROGRESS", "PROCESSING", "REOPENED");
    private static final List<String> ISSUE_CLOSED_STATUSES = List.of("CLOSED", "RESOLVED");
    private static final List<String> QUALITY_PASS_STATUSES = List.of("SUCCESS", "PASSED", "COMPLETED");
    private static final List<String> QUALITY_FAIL_STATUSES = List.of("FAILED", "ERROR");
    private static final int GOVERNANCE_TREND_DAYS = 7;

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
    private final CatalogMetadataChangeLogRepository metadataChangeLogRepo;
    private final CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepo;
    private final CatalogDatasetGrantRepository grantRepo;
    private final GovQualityRunRepository qualityRunRepo;
    private final GovIssueTicketRepository issueTicketRepo;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final CatalogFeatureProperties catalogFeatures;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final DataStandardRepository dataStandardRepository;
    private final OpenMetadataService openMetadataService;
    private final CatalogMetadataService catalogMetadataService;

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
        CatalogMetadataChangeLogRepository metadataChangeLogRepo,
        CatalogDatasetSecurityMappingRepository datasetSecurityMappingRepo,
        CatalogDatasetGrantRepository grantRepo,
        GovQualityRunRepository qualityRunRepo,
        GovIssueTicketRepository issueTicketRepo,
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogFeatureProperties catalogFeatures,
        OrganizationVisibilityService organizationVisibilityService,
        DataStandardRepository dataStandardRepository,
        OpenMetadataService openMetadataService,
        CatalogMetadataService catalogMetadataService
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
        this.metadataChangeLogRepo = metadataChangeLogRepo;
        this.datasetSecurityMappingRepo = datasetSecurityMappingRepo;
        this.grantRepo = grantRepo;
        this.qualityRunRepo = qualityRunRepo;
        this.issueTicketRepo = issueTicketRepo;
        this.infraDataSourceRepository = infraDataSourceRepository;
        this.catalogFeatures = catalogFeatures;
        this.organizationVisibilityService = organizationVisibilityService;
        this.dataStandardRepository = dataStandardRepository;
        this.openMetadataService = openMetadataService;
        this.catalogMetadataService = catalogMetadataService;
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
        String k = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        Page<CatalogDomain> p = k == null
            ? domainRepo.findAll(pageable)
            : domainRepo.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCaseOrOwnerContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                k,
                k,
                k,
                k,
                pageable
            );
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
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String safeSortBy = normalizeDatasetSortBy(sortBy);
        boolean asc = "asc".equalsIgnoreCase(trimToNull(sortDir));
        Sort sort = asc ? Sort.by(safeSortBy).ascending() : Sort.by(safeSortBy).descending();
        if (!"createdDate".equals(safeSortBy)) {
            sort = sort.and(Sort.by("createdDate").descending());
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        long queryStartedAt = System.currentTimeMillis();
        Page<CatalogDataset> pageData = datasetRepo.findAll(
            buildDatasetListSpecification(
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
        List<Map<String, Object>> content = pageData.getContent().stream().map(this::toDatasetDto).toList();
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
        String purpose = trimToNull(auditPurpose);
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
        putIfHasText(auditPayload, "keyword", keyword);
        putIfHasText(auditPayload, "classification", classification);
        putIfHasText(auditPayload, "ownerDept", ownerDept);
        putIfHasText(auditPayload, "warehouseLayer", warehouseLayer);
        auditPayload.put("enabledOnly", enabledOnly);
        putIfHasText(auditPayload, "type", type);
        putIfHasText(auditPayload, "exposedBy", exposedBy);
        putIfHasText(auditPayload, "owner", owner);
        putIfHasText(auditPayload, "tag", tag);
        auditPayload.put("sortBy", safeSortBy);
        auditPayload.put("sortDir", asc ? "asc" : "desc");
        auditPayload.put("queryCostMs", queryCostMs);
        String resourceRef = "CATALOG_ASSET_LIST".equals(actionCode) ? "page=" + page : null;
        audit.auditAction(actionCode, AuditStage.SUCCESS, resourceRef, auditPayload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/datasets/{id}")
    public ApiResponse<Map<String, Object>> getDataset(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        // Metadata visibility: allow all authenticated users to view dataset metadata.
        // Data-content access (query/preview) is enforced at execution endpoints.
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> ds = toDatasetDto(dataset, true);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        String datasetName = safeText(ds.get("name"));
        auditPayload.put("summary", datasetName == null ? "查看数据资产详情" : "查看数据资产：" + datasetName);
        putIfHasText(auditPayload, "targetName", datasetName);
        auditPayload.put("datasetId", id.toString());
        putIfHasText(auditPayload, "activeDept", effDept);
        putIfHasText(auditPayload, "classification", safeText(ds.get("classification")));
        putIfHasText(auditPayload, "ownerDept", safeText(ds.get("ownerDept")));
        putIfHasText(auditPayload, "owner", safeText(ds.get("owner")));
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(ds);
    }

    @GetMapping("/datasets/{id}/openmetadata")
    public ApiResponse<OpenMetadataService.OpenMetadataResult> getDatasetOpenMetadata(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看元数据信息");
        auditPayload.put("datasetId", id.toString());
        putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchTableForDataset(dataset));
    }

    @GetMapping("/metadata/tables")
    public ApiResponse<OpenMetadataService.OpenMetadataTablePage> listTechMetadataTables(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "size", required = false, defaultValue = "50") int size,
        @RequestParam(value = "sourceId", required = false) UUID sourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : claim("dept_code");
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
    public ApiResponse<OpenMetadataService.OpenMetadataResult> getTechMetadataTableDetail(
        @RequestParam("fqn") String fqn,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effDept = activeDept != null ? activeDept : claim("dept_code");
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
    public ApiResponse<OpenMetadataService.OpenMetadataLineageResult> getDatasetLineage(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集血缘");
        auditPayload.put("datasetId", id.toString());
        putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchLineageForDataset(dataset, 2, 2));
    }

    @GetMapping("/datasets/{id}/quality")
    public ApiResponse<OpenMetadataService.OpenMetadataQualityResult> getDatasetQuality(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue() && !SecurityUtils.isOpAdminAccount()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据集质量");
        auditPayload.put("datasetId", id.toString());
        putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(openMetadataService.fetchQualityForDataset(dataset));
    }

    @PostMapping("/quality/batch")
    public ApiResponse<Map<String, OpenMetadataService.OpenMetadataQualitySummary>> batchDatasetQuality(
        @RequestBody OpenMetadataBatchRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<UUID> ids = body != null && body.ids() != null ? body.ids() : List.of();
        if (ids.isEmpty()) {
            return ApiResponses.ok(Map.of());
        }
        if (ids.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量请求过大");
        }
        String effDept = activeDept != null ? activeDept : claim("dept_code");
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
        putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "batch-quality", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/datasets/{id}/governance-health")
    public ApiResponse<Map<String, Object>> getDatasetGovernanceHealth(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }
        Map<String, Object> payload = buildDatasetGovernanceHealth(dataset);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看资产治理健康");
        auditPayload.put("datasetId", id.toString());
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, id.toString(), auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/ops/reconciliation")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> catalogReconciliation(
        @RequestParam(name = "sampleLimit", defaultValue = "20") int sampleLimit
    ) {
        int safeSampleLimit = Math.max(5, Math.min(sampleLimit, 100));
        Map<String, Object> payload = buildCatalogReconciliation(safeSampleLimit);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "执行资产中心一致性核对");
        auditPayload.put("sampleLimit", safeSampleLimit);
        Object assertionCount = payload.get("assertionCount");
        if (assertionCount != null) {
            auditPayload.put("assertionCount", assertionCount);
        }
        audit.auditAction("CATALOG_RECONCILIATION_CHECK", AuditStage.SUCCESS, "catalog", auditPayload);
        return ApiResponses.ok(payload);
    }

    @PostMapping("/datasets/openmetadata/batch")
    public ApiResponse<Map<String, OpenMetadataService.OpenMetadataSummary>> batchOpenMetadata(
        @RequestBody OpenMetadataBatchRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<UUID> ids = body != null && body.ids() != null ? body.ids() : List.of();
        if (ids.isEmpty()) {
            return ApiResponses.ok(Map.of());
        }
        if (ids.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量请求过大");
        }
        String effDept = activeDept != null ? activeDept : claim("dept_code");
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
        putIfHasText(auditPayload, "activeDept", effDept);
        audit.auditAction("CATALOG_ASSET_VIEW", AuditStage.SUCCESS, "batch-openmetadata", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/datasets/{id}/security-mapping")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> getDatasetSecurityMapping(@PathVariable UUID id) {
        datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        CatalogDatasetSecurityMapping mapping = datasetSecurityMappingRepo.findById(id).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", id.toString());
        payload.put("dataLevelField", mapping != null ? mapping.getDataLevelField() : null);
        payload.put("deptField", mapping != null ? mapping.getDeptField() : null);
        audit.auditAction(
            "CATALOG_SECURITY_MAPPING_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看行级安全字段映射", "datasetId", id.toString())
        );
        return ApiResponses.ok(payload);
    }

    @PutMapping("/datasets/{id}/security-mapping")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> upsertDatasetSecurityMapping(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        CatalogDataset dataset = datasetRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        ensureDatasetEditPermission(dataset);

        List<CatalogTableSchema> tables = tableRepo.findByDataset(dataset);
        if (tables == null || tables.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集尚未采集 Schema，无法配置字段映射");
        }
        Set<String> columnsLower = new HashSet<>();
        for (CatalogTableSchema table : tables) {
            for (CatalogColumnSchema column : columnRepo.findByTable(table)) {
                if (column != null && StringUtils.hasText(column.getName())) {
                    columnsLower.add(column.getName().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (columnsLower.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据集尚未采集字段信息，无法配置字段映射");
        }

        String dataLevelField = trimToNull(safeText(body.get("dataLevelField")));
        String deptField = trimToNull(safeText(body.get("deptField")));
        if (dataLevelField != null && !columnsLower.contains(dataLevelField.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据密级字段不存在：" + dataLevelField);
        }
        if (deptField != null && !columnsLower.contains(deptField.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门字段不存在：" + deptField);
        }

        CatalogDatasetSecurityMapping mapping = datasetSecurityMappingRepo.findById(id).orElse(null);
        Map<String, Object> before = new LinkedHashMap<>();
        if (mapping != null) {
            before.put("dataLevelField", mapping.getDataLevelField());
            before.put("deptField", mapping.getDeptField());
        }

        if (dataLevelField == null && deptField == null) {
            if (mapping != null) {
                datasetSecurityMappingRepo.delete(mapping);
            }
            audit.auditAction(
                "CATALOG_SECURITY_MAPPING_EDIT",
                AuditStage.SUCCESS,
                id.toString(),
                Map.of("summary", "清除行级安全字段映射", "datasetId", id.toString(), "before", before, "after", Map.of())
            );
            return ApiResponses.ok(Map.of("datasetId", id.toString(), "dataLevelField", null, "deptField", null));
        }

        if (mapping == null) {
            mapping = new CatalogDatasetSecurityMapping();
            mapping.setDatasetId(id);
        }
        mapping.setDataLevelField(dataLevelField);
        mapping.setDeptField(deptField);
        CatalogDatasetSecurityMapping saved = datasetSecurityMappingRepo.save(mapping);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("dataLevelField", saved.getDataLevelField());
        after.put("deptField", saved.getDeptField());

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "配置行级安全字段映射");
        auditPayload.put("datasetId", id.toString());
        auditPayload.put("before", before);
        auditPayload.put("after", after);
        audit.auditAction("CATALOG_SECURITY_MAPPING_EDIT", AuditStage.SUCCESS, id.toString(), auditPayload);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", id.toString());
        payload.put("dataLevelField", saved.getDataLevelField());
        payload.put("deptField", saved.getDeptField());
        return ApiResponses.ok(payload);
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
        m.put("domainName", d.getDomain() != null ? d.getDomain().getName() : null);
        m.put("type", d.getType());
        m.put("sourceId", d.getSourceId());
        m.put("classification", d.getClassification());
        // ABAC fields (optional for backward compatibility)
        m.put("ownerDept", d.getOwnerDept());
        m.put("owner", d.getOwner());
        m.put("hiveDatabase", d.getHiveDatabase());
        m.put("hiveTable", d.getHiveTable());
        m.put("trinoCatalog", d.getTrinoCatalog());
        m.put("tags", d.getTags());
        m.put("description", d.getDescription());
        m.put("warehouseLayer", d.getWarehouseLayer());
        m.put("enabled", d.getEnabled());
        m.put("exposedBy", d.getExposedBy());
        m.put("lifecycleStatus", d.getLifecycleStatus());
        m.put("retentionDays", d.getRetentionDays());
        m.put("expiresAt", d.getExpiresAt());
        m.put("snapshotTime", d.getSnapshotTime());
        m.put("createdDate", d.getCreatedDate());
        m.put("lastModifiedDate", d.getLastModifiedDate());
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
                            colDto.put("status", trimToNull(col.getStatus()));
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

    private Specification<CatalogDataset> buildDatasetListSpecification(
        UUID domainId,
        UUID sourceId,
        String keyword,
        String classification,
        String ownerDept,
        String warehouseLayer,
        boolean enabledOnly,
        String type,
        String exposedBy,
        String owner,
        String tag
    ) {
        String keywordText = trimToNull(keyword);
        String ownerText = trimToNull(owner);
        String tagText = trimToNull(tag);
        String classificationText = trimToNull(classification);
        String ownerDeptText = trimToNull(ownerDept);
        String layerText = trimToNull(warehouseLayer);
        String typeText = trimToNull(type);
        String exposedByText = trimToNull(exposedBy);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (domainId != null) {
                predicates.add(cb.equal(root.get("domain").get("id"), domainId));
            }
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (classificationText != null) {
                predicates.add(cb.equal(cb.lower(root.get("classification")), classificationText.toLowerCase(Locale.ROOT)));
            }
            if (ownerDeptText != null) {
                predicates.add(cb.equal(cb.lower(root.get("ownerDept")), ownerDeptText.toLowerCase(Locale.ROOT)));
            }
            if (layerText != null) {
                predicates.add(cb.equal(cb.lower(root.get("warehouseLayer")), layerText.toLowerCase(Locale.ROOT)));
            }
            if (typeText != null) {
                predicates.add(cb.equal(cb.lower(root.get("type")), typeText.toLowerCase(Locale.ROOT)));
            }
            if (exposedByText != null) {
                predicates.add(cb.equal(cb.lower(root.get("exposedBy")), exposedByText.toLowerCase(Locale.ROOT)));
            }
            if (ownerText != null) {
                predicates.add(cb.like(cb.lower(root.get("owner")), "%" + ownerText.toLowerCase(Locale.ROOT) + "%"));
            }
            if (tagText != null) {
                predicates.add(cb.like(cb.lower(root.get("tags")), "%" + tagText.toLowerCase(Locale.ROOT) + "%"));
            }
            if (enabledOnly) {
                predicates.add(cb.or(cb.isNull(root.get("enabled")), cb.isTrue(root.get("enabled"))));
            }
            if (keywordText != null) {
                String keywordLike = "%" + keywordText.toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("name")), keywordLike),
                        cb.like(cb.lower(root.get("owner")), keywordLike),
                        cb.like(cb.lower(root.get("ownerDept")), keywordLike),
                        cb.like(cb.lower(root.get("tags")), keywordLike),
                        cb.like(cb.lower(root.get("description")), keywordLike),
                        cb.like(cb.lower(root.get("hiveDatabase")), keywordLike),
                        cb.like(cb.lower(root.get("hiveTable")), keywordLike)
                    )
                );
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeDatasetSortBy(String sortBy) {
        String normalized = trimToNull(sortBy);
        if (normalized == null) {
            return "createdDate";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "name" -> "name";
            case "lastmodifieddate", "updatedat", "updated_at" -> "lastModifiedDate";
            case "createddate", "createdat", "created_at" -> "createdDate";
            default -> "createdDate";
        };
    }

    @PostMapping("/datasets")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDataset> createDataset(@Valid @RequestBody CatalogDataset dataset) {
        applySourcePolicy(dataset);
        normalizeClassification(dataset);
        normalizeWarehouseLayer(dataset);
        dataset.setEnabled(dataset.getEnabled() != null ? Boolean.TRUE.equals(dataset.getEnabled()) : Boolean.TRUE);
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
            normalizeWarehouseLayer(item);
            item.setEnabled(item.getEnabled() != null ? Boolean.TRUE.equals(item.getEnabled()) : Boolean.TRUE);
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
            existing.setDescription(trimToNull(patch.getDescription()));
            existing.setWarehouseLayer(trimToNull(patch.getWarehouseLayer()));
            normalizeWarehouseLayer(existing);
            if (patch.getEnabled() != null) {
                existing.setEnabled(Boolean.TRUE.equals(patch.getEnabled()));
            }
            existing.setExposedBy(patch.getExposedBy());
            existing.setLifecycleStatus(trimToNull(patch.getLifecycleStatus()));
            existing.setRetentionDays(patch.getRetentionDays());
            existing.setExpiresAt(patch.getExpiresAt());
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

    @PostMapping("/datasets/{id}/publish")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> publishDataset(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        ensureDatasetEditPermission(dataset);
        Map<String, Object> before = datasetSnapshot(dataset);
        dataset.setEnabled(Boolean.TRUE);
        CatalogDataset saved = datasetRepo.save(dataset);
        Map<String, Object> after = datasetSnapshot(saved);
        audit.auditAction(
            "CATALOG_ASSET_PUBLISH",
            AuditStage.SUCCESS,
            id.toString(),
            datasetChangePayload("发布数据资产：" + displayName(saved), before, after)
        );
        return ApiResponses.ok(Map.of("id", id.toString(), "enabled", Boolean.TRUE));
    }

    @PostMapping("/datasets/{id}/offline")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> offlineDataset(@PathVariable UUID id) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        ensureDatasetEditPermission(dataset);
        Map<String, Object> before = datasetSnapshot(dataset);
        dataset.setEnabled(Boolean.FALSE);
        CatalogDataset saved = datasetRepo.save(dataset);
        Map<String, Object> after = datasetSnapshot(saved);
        audit.auditAction(
            "CATALOG_ASSET_OFFLINE",
            AuditStage.SUCCESS,
            id.toString(),
            datasetChangePayload("下线数据资产：" + displayName(saved), before, after)
        );
        return ApiResponses.ok(Map.of("id", id.toString(), "enabled", Boolean.FALSE));
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
        if (dataset.getSourceId() != null) {
            snapshot.put("sourceId", dataset.getSourceId().toString());
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
        if (StringUtils.hasText(dataset.getDescription())) {
            snapshot.put("description", dataset.getDescription());
        }
        if (StringUtils.hasText(dataset.getTags())) {
            snapshot.put("tags", dataset.getTags());
        }
        if (StringUtils.hasText(dataset.getWarehouseLayer())) {
            snapshot.put("warehouseLayer", dataset.getWarehouseLayer());
        }
        if (dataset.getEnabled() != null) {
            snapshot.put("enabled", dataset.getEnabled());
        }
        if (StringUtils.hasText(dataset.getExposedBy())) {
            snapshot.put("exposedBy", dataset.getExposedBy());
        }
        if (StringUtils.hasText(dataset.getTrinoCatalog())) {
            snapshot.put("trinoCatalog", dataset.getTrinoCatalog());
        }
        if (StringUtils.hasText(dataset.getLifecycleStatus())) {
            snapshot.put("lifecycleStatus", dataset.getLifecycleStatus());
        }
        if (dataset.getRetentionDays() != null) {
            snapshot.put("retentionDays", dataset.getRetentionDays());
        }
        if (dataset.getExpiresAt() != null) {
            snapshot.put("expiresAt", dataset.getExpiresAt().toString());
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
        String rootDept = organizationVisibilityService.resolveDefaultRootDept().orElse(null);
        if (!StringUtils.hasText(rootDept)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所属部门不能为空（未获取到所级部门，请先同步组织树）");
        }

        if (SecurityUtils.isOpAdminAccount()) {
            dataset.setOwnerDept(requested != null ? requested : rootDept);
            return;
        }

        if (enforceNoChangeForNonOp && !Objects.equals(requested, trimmedPrevious)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅运维管理员可以调整数据资产归属部门");
        }

        dataset.setOwnerDept(trimmedPrevious != null ? trimmedPrevious : rootDept);
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
            dataset.setSourceId(null);
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

    private void normalizeWarehouseLayer(CatalogDataset dataset) {
        String value = trimToNull(dataset.getWarehouseLayer());
        if (value == null) {
            dataset.setWarehouseLayer(null);
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        dataset.setWarehouseLayer(normalized);
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
        MappingValidationResult validation = validateClassificationMappingInternal(items);
        if (!validation.conflicts().isEmpty()) {
            String msg = validation
                .conflicts()
                .stream()
                .map(it -> Objects.toString(it.get("message"), "映射冲突"))
                .findFirst()
                .orElse("分类映射存在冲突");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg + "（可先执行分类映射冲突预检）");
        }
        mappingRepo.deleteAll();
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(validation.normalizedItems());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新分类映射");
        auditPayload.put("savedCount", saved.size());
        auditPayload.put("warningCount", validation.warnings().size());
        auditPayload.put("warnings", validation.warnings());
        audit.auditAction("CATALOG_CLASSIFICATION_MAPPING_REPLACE", AuditStage.SUCCESS, "replace:" + saved.size(), auditPayload);
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

    @PostMapping("/classification-mapping/validate")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> validateClassificationMapping(
        @RequestBody(required = false) List<CatalogClassificationMapping> items
    ) {
        MappingValidationResult validation = validateClassificationMappingInternal(items);
        Map<String, Object> payload = toMappingValidationPayload(validation);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "分类映射冲突预检");
        auditPayload.put("conflictCount", validation.conflicts().size());
        auditPayload.put("warningCount", validation.warnings().size());
        audit.auditAction("CATALOG_CLASSIFICATION_MAPPING_VALIDATE", AuditStage.SUCCESS, "classification-mapping", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/classification-masking/linkage")
    public ApiResponse<Map<String, Object>> classificationMaskingLinkage(
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        if (datasetId == null && !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅维护角色可查看联动总览");
        }
        Map<String, Object> payload = datasetId != null ? buildDatasetLinkage(datasetId, activeDept) : buildLinkageSummary();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", datasetId != null ? "查看资产密级与脱敏策略联动" : "查看密级与脱敏联动总览");
        if (datasetId != null) {
            auditPayload.put("datasetId", datasetId.toString());
        }
        audit.auditAction("CATALOG_CLASSIFICATION_MASKING_LINKAGE_VIEW", AuditStage.SUCCESS, datasetId != null ? datasetId.toString() : "summary", auditPayload);
        return ApiResponses.ok(payload);
    }

    private MappingValidationResult validateClassificationMappingInternal(List<CatalogClassificationMapping> items) {
        List<CatalogClassificationMapping> normalized = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        Map<String, String> seenKeys = new LinkedHashMap<>();
        List<CatalogClassificationMapping> safeItems = items != null ? items : List.of();
        for (int i = 0; i < safeItems.size(); i++) {
            CatalogClassificationMapping item = safeItems.get(i);
            String source = trimToNull(item != null ? item.getSource() : null);
            String sourceLevel = trimToNull(item != null ? item.getSourceLevel() : null);
            String platformLevel = normalizeClassification(item != null ? item.getPlatformLevel() : null);
            int rowNo = i + 1;

            if (source == null || sourceLevel == null || platformLevel == null) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "INVALID_ROW");
                conflict.put("message", "来源系统、来源级别、平台级别不能为空，且平台级别必须合法。");
                conflict.put("suggestion", "请补全字段并将平台级别修正为 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL。");
                conflicts.add(conflict);
                continue;
            }
            if (!SUPPORTED_CLASSIFICATION_LEVELS.contains(platformLevel)) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "INVALID_PLATFORM_LEVEL");
                conflict.put("message", "平台级别不合法：" + platformLevel);
                conflict.put("suggestion", "平台级别仅支持 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL。");
                conflicts.add(conflict);
                continue;
            }

            String key = source.toUpperCase(Locale.ROOT) + "::" + sourceLevel.toUpperCase(Locale.ROOT);
            if (seenKeys.containsKey(key)) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "DUPLICATE_MAPPING");
                conflict.put("message", "存在重复映射键：" + source + " / " + sourceLevel);
                conflict.put("suggestion", "请保留唯一映射并删除重复项。");
                conflicts.add(conflict);
                continue;
            }
            seenKeys.put(key, platformLevel);

            CatalogClassificationMapping normalizedItem = new CatalogClassificationMapping();
            normalizedItem.setSource(source);
            normalizedItem.setSourceLevel(sourceLevel);
            normalizedItem.setPlatformLevel(platformLevel);
            normalized.add(normalizedItem);
        }

        Map<UUID, Integer> datasetRuleCount = new HashMap<>();
        for (CatalogMaskingRule rule : maskingRepo.findAll()) {
            UUID datasetId = rule != null && rule.getDataset() != null ? rule.getDataset().getId() : null;
            if (datasetId != null) {
                datasetRuleCount.merge(datasetId, 1, Integer::sum);
            }
        }
        Map<String, Integer> classificationDatasetCount = new HashMap<>();
        List<CatalogDataset> datasets = datasetRepo.findAll();
        for (CatalogDataset dataset : datasets) {
            String level = normalizeClassification(dataset.getClassification());
            if (level != null) {
                classificationDatasetCount.merge(level, 1, Integer::sum);
            }
            if (!requiresMasking(level) || datasetRuleCount.getOrDefault(dataset.getId(), 0) > 0) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("code", "MASKING_GAP");
            warning.put("datasetId", dataset.getId() != null ? dataset.getId().toString() : null);
            warning.put("datasetName", dataset.getName());
            warning.put("classification", level);
            warning.put("message", "高密级数据集缺少脱敏规则：" + dataset.getName());
            warning.put("suggestion", "请在“脱敏规则”中为该数据集至少配置 1 条规则。");
            warnings.add(warning);
        }

        Set<String> mappedLevels = normalized.stream().map(CatalogClassificationMapping::getPlatformLevel).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        for (String level : mappedLevels) {
            if (classificationDatasetCount.getOrDefault(level, 0) > 0) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("code", "UNUSED_PLATFORM_LEVEL");
            warning.put("classification", level);
            warning.put("message", "平台级别 " + level + " 当前没有对应数据集。");
            warning.put("suggestion", "可保留为预留映射，或移除以减少维护成本。");
            warnings.add(warning);
        }
        return new MappingValidationResult(normalized, conflicts, warnings);
    }

    private Map<String, Object> toMappingValidationPayload(MappingValidationResult validation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", validation.conflicts().isEmpty());
        payload.put("normalizedCount", validation.normalizedItems().size());
        payload.put("conflicts", validation.conflicts());
        payload.put("warnings", validation.warnings());
        return payload;
    }

    private Map<String, Object> buildDatasetLinkage(UUID datasetId, String activeDept) {
        CatalogDataset dataset = datasetRepo
            .findById(datasetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该数据集");
        }
        String classification = normalizeClassification(dataset.getClassification());
        List<CatalogMaskingRule> rules = maskingRepo.findByDataset(dataset);
        List<Map<String, Object>> effectiveRules = rules.stream().map(this::toMaskingRuleDto).toList();
        List<Map<String, Object>> mappingMatches = mappingRepo
            .findAll()
            .stream()
            .filter(item -> Objects.equals(normalizeClassification(item.getPlatformLevel()), classification))
            .map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId() != null ? item.getId().toString() : null);
                row.put("source", trimToNull(item.getSource()));
                row.put("sourceLevel", trimToNull(item.getSourceLevel()));
                row.put("platformLevel", normalizeClassification(item.getPlatformLevel()));
                return row;
            })
            .toList();
        boolean requiresMasking = requiresMasking(classification);
        boolean conflict = requiresMasking && rules.isEmpty();
        List<String> suggestions = new ArrayList<>();
        if (conflict) {
            suggestions.add("当前密级要求至少 1 条脱敏规则，请在“数据治理中心 / 分级分类 -> 脱敏规则”中补齐。");
        }
        if (mappingMatches.isEmpty()) {
            suggestions.add("当前密级在分类映射中无对应关系，请在“分类映射”中新增来源级别映射。");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("datasetName", dataset.getName());
        payload.put("classification", classification);
        payload.put("requiresMasking", requiresMasking);
        payload.put("maskingRuleCount", rules.size());
        payload.put("effectiveRules", effectiveRules);
        payload.put("mappingMatches", mappingMatches);
        payload.put("conflict", conflict);
        payload.put("suggestions", suggestions);
        return payload;
    }

    private Map<String, Object> buildLinkageSummary() {
        List<CatalogDataset> datasets = datasetRepo.findAll();
        List<CatalogMaskingRule> rules = maskingRepo.findAll();
        List<CatalogClassificationMapping> mappings = mappingRepo.findAll();
        Map<UUID, Integer> datasetRuleCount = new HashMap<>();
        for (CatalogMaskingRule rule : rules) {
            UUID datasetId = rule != null && rule.getDataset() != null ? rule.getDataset().getId() : null;
            if (datasetId != null) {
                datasetRuleCount.merge(datasetId, 1, Integer::sum);
            }
        }
        Map<String, Integer> mappingCountByLevel = new HashMap<>();
        for (CatalogClassificationMapping mapping : mappings) {
            String level = normalizeClassification(mapping.getPlatformLevel());
            if (level != null) {
                mappingCountByLevel.merge(level, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (String level : CLASSIFICATION_LEVEL_ORDER) {
            int datasetCount = 0;
            int datasetWithRules = 0;
            int maskingRuleCount = 0;
            List<String> noRuleDatasets = new ArrayList<>();
            for (CatalogDataset dataset : datasets) {
                if (!Objects.equals(normalizeClassification(dataset.getClassification()), level)) {
                    continue;
                }
                datasetCount += 1;
                int ruleCount = datasetRuleCount.getOrDefault(dataset.getId(), 0);
                maskingRuleCount += ruleCount;
                if (ruleCount > 0) {
                    datasetWithRules += 1;
                } else if (requiresMasking(level)) {
                    noRuleDatasets.add(dataset.getName());
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classification", level);
            row.put("requiresMasking", requiresMasking(level));
            row.put("datasetCount", datasetCount);
            row.put("datasetWithRules", datasetWithRules);
            row.put("maskingRuleCount", maskingRuleCount);
            row.put("mappingCount", mappingCountByLevel.getOrDefault(level, 0));
            rows.add(row);

            if (!noRuleDatasets.isEmpty()) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("type", "MASKING_GAP");
                conflict.put("classification", level);
                conflict.put("count", noRuleDatasets.size());
                conflict.put("datasets", noRuleDatasets.stream().limit(10).toList());
                conflict.put("suggestion", "为该密级数据集补充脱敏规则，至少覆盖核心敏感字段。");
                conflicts.add(conflict);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", rows);
        payload.put("conflicts", conflicts);
        payload.put("datasetTotal", datasets.size());
        payload.put("maskingRuleTotal", rules.size());
        payload.put("mappingTotal", mappings.size());
        return payload;
    }

    private boolean requiresMasking(String classification) {
        return Objects.equals(classification, "SECRET") || Objects.equals(classification, "CONFIDENTIAL");
    }

    private String normalizeClassification(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        DataLevel normalized = DataLevel.normalize(value);
        return normalized != null ? normalized.classification() : value.toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> toMaskingRuleDto(CatalogMaskingRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (rule == null) {
            return row;
        }
        if (rule.getId() != null) {
            row.put("id", rule.getId().toString());
        }
        row.put("column", trimToNull(rule.getColumn()));
        row.put("function", trimToNull(rule.getFunction()));
        row.put("args", trimToNull(rule.getArgs()));
        return row;
    }

    private Map<String, Object> buildDatasetGovernanceHealth(CatalogDataset dataset) {
        UUID datasetId = dataset != null ? dataset.getId() : null;
        if (datasetId == null) {
            return Map.of();
        }
        Instant now = Instant.now();
        Instant trendStart = now.minusSeconds(86400L * GOVERNANCE_TREND_DAYS);
        List<GovQualityRun> recentRuns = qualityRunRepo.findByDatasetId(datasetId, PageRequest.of(0, 200, Sort.by("createdDate").descending()));
        long totalRuns = qualityRunRepo.countByDatasetId(datasetId);
        long passRuns = 0;
        long failRuns = 0;
        long runningRuns = 0;
        Map<String, Integer> failureCategoryCount = new LinkedHashMap<>();
        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (int i = GOVERNANCE_TREND_DAYS - 1; i >= 0; i--) {
            Instant day = now.minusSeconds(86400L * i);
            String key = day.toString().substring(0, 10);
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("date", key);
            slot.put("total", 0);
            slot.put("passed", 0);
            slot.put("failed", 0);
            trend.put(key, slot);
        }
        for (GovQualityRun run : recentRuns) {
            String status = normalizeUpper(run != null ? run.getStatus() : null);
            if (QUALITY_PASS_STATUSES.contains(status)) {
                passRuns += 1;
            } else if (QUALITY_FAIL_STATUSES.contains(status)) {
                failRuns += 1;
                String category = trimToNull(run != null ? run.getErrorCategory() : null);
                failureCategoryCount.merge(category != null ? category : "UNKNOWN", 1, Integer::sum);
            } else if ("RUNNING".equals(status) || "PENDING".equals(status) || "QUEUED".equals(status)) {
                runningRuns += 1;
            }
            Instant createdAt = run != null ? run.getCreatedDate() : null;
            if (createdAt == null || createdAt.isBefore(trendStart)) {
                continue;
            }
            String dayKey = createdAt.toString().substring(0, 10);
            Map<String, Object> slot = trend.get(dayKey);
            if (slot == null) {
                continue;
            }
            slot.put("total", ((Number) slot.get("total")).intValue() + 1);
            if (QUALITY_PASS_STATUSES.contains(status)) {
                slot.put("passed", ((Number) slot.get("passed")).intValue() + 1);
            }
            if (QUALITY_FAIL_STATUSES.contains(status)) {
                slot.put("failed", ((Number) slot.get("failed")).intValue() + 1);
            }
        }
        List<Map<String, Object>> failureTop = failureCategoryCount
            .entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .toList();
        GovQualityRun latestRun = qualityRunRepo.findFirstByDatasetIdOrderByCreatedDateDesc(datasetId).orElse(null);

        List<GovIssueTicket> recentIssues = issueTicketRepo.findTop100ByDatasetIdOrderByCreatedDateDesc(datasetId);
        long issueTotal = issueTicketRepo.countByDatasetId(datasetId);
        long issueOpen = issueTicketRepo.countByDatasetIdAndStatusIn(datasetId, ISSUE_OPEN_STATUSES);
        long issueClosed = issueTicketRepo.countByDatasetIdAndStatusIn(datasetId, ISSUE_CLOSED_STATUSES);
        long overdueIssue = recentIssues
            .stream()
            .filter(issue -> {
                String status = normalizeUpper(issue != null ? issue.getStatus() : null);
                if (!ISSUE_OPEN_STATUSES.contains(status)) {
                    return false;
                }
                Instant dueAt = issue != null ? issue.getDueAt() : null;
                return dueAt != null && dueAt.isBefore(now) && issue.getResolvedAt() == null;
            })
            .count();
        List<Map<String, Object>> issueTop = recentIssues
            .stream()
            .filter(Objects::nonNull)
            .limit(5)
            .map(issue -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", issue.getId() != null ? issue.getId().toString() : null);
                row.put("title", trimToNull(issue.getTitle()));
                row.put("status", normalizeUpper(issue.getStatus()));
                row.put("priority", normalizeUpper(issue.getPriority()));
                row.put("severity", normalizeUpper(issue.getSeverity()));
                row.put("dueAt", issue.getDueAt());
                row.put("updatedAt", issue.getLastModifiedDate());
                return row;
            })
            .toList();

        int healthScore = 100;
        healthScore -= Math.min(40, (int) failRuns * 8);
        healthScore -= Math.min(30, (int) issueOpen * 2);
        healthScore -= Math.min(20, (int) overdueIssue * 5);
        if (failRuns == 0 && passRuns > 0) {
            healthScore = Math.min(100, healthScore + 5);
        }
        String healthLevel = healthScore >= 80 ? "HEALTHY" : healthScore >= 60 ? "WARN" : "RISK";

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("totalRuns", totalRuns);
        quality.put("passRuns", passRuns);
        quality.put("failRuns", failRuns);
        quality.put("runningRuns", runningRuns);
        quality.put("latestRunAt", latestRun != null ? latestRun.getCreatedDate() : null);
        quality.put("latestStatus", latestRun != null ? normalizeUpper(latestRun.getStatus()) : null);
        quality.put("failureTop", failureTop);
        quality.put("trend", new ArrayList<>(trend.values()));

        Map<String, Object> issues = new LinkedHashMap<>();
        issues.put("total", issueTotal);
        issues.put("open", issueOpen);
        issues.put("closed", issueClosed);
        issues.put("overdue", overdueIssue);
        issues.put("top", issueTop);

        Map<String, Object> links = new LinkedHashMap<>();
        links.put("qualityRulesPath", "/governance/rules?runDatasetId=" + datasetId + "&runStatus=FAILED");
        links.put("qualityReportPath", "/governance/quality?datasetId=" + datasetId);
        links.put("issuesPath", "/governance/rules?issueDatasetId=" + datasetId + "&issueStatus=OPEN");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("datasetName", dataset.getName());
        payload.put("healthScore", healthScore);
        payload.put("healthLevel", healthLevel);
        payload.put("quality", quality);
        payload.put("issues", issues);
        payload.put("links", links);
        return payload;
    }

    private Map<String, Object> buildCatalogReconciliation(int sampleLimit) {
        List<CatalogDataset> datasets = datasetRepo.findAll();
        Set<UUID> datasetIds = datasets
            .stream()
            .map(CatalogDataset::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        List<GovQualityRun> allRuns = qualityRunRepo.findAll();
        List<GovIssueTicket> allIssues = issueTicketRepo.findAll();

        long enabledDatasets = datasets.stream().filter(ds -> ds.getEnabled() == null || ds.getEnabled().booleanValue()).count();
        long staleDatasets = datasets.stream().filter(ds -> "STALE".equals(normalizeUpper(ds.getLifecycleStatus()))).count();
        long enabledNoSnapshot = datasets
            .stream()
            .filter(ds -> ds.getEnabled() == null || ds.getEnabled().booleanValue())
            .filter(ds -> ds.getSnapshotTime() == null)
            .count();
        long noOwnerDept = datasets.stream().filter(ds -> trimToNull(ds.getOwnerDept()) == null).count();

        List<GovQualityRun> orphanRuns = allRuns
            .stream()
            .filter(run -> run.getDatasetId() != null && !datasetIds.contains(run.getDatasetId()))
            .limit(sampleLimit)
            .toList();
        List<GovIssueTicket> orphanIssues = allIssues
            .stream()
            .filter(issue -> issue.getDatasetId() != null && !datasetIds.contains(issue.getDatasetId()))
            .limit(sampleLimit)
            .toList();

        List<Map<String, Object>> assertions = new ArrayList<>();
        assertions.add(assertion("A01", "资产目录非空", !datasets.isEmpty(), "ERROR", "datasetCount=" + datasets.size(), "至少完成一批元数据采集后再发布。"));
        assertions.add(
            assertion(
                "A02",
                "质量运行无孤儿记录",
                orphanRuns.isEmpty(),
                "ERROR",
                "orphanQualityRuns=" + orphanRuns.size(),
                "检查治理运行数据中的 dataset_id 是否仍在资产目录中。"
            )
        );
        assertions.add(
            assertion(
                "A03",
                "问题工单无孤儿记录",
                orphanIssues.isEmpty(),
                "ERROR",
                "orphanIssueTickets=" + orphanIssues.size(),
                "检查问题单中的 dataset_id 与资产目录同步状态。"
            )
        );
        assertions.add(
            assertion(
                "A04",
                "启用资产存在快照时间",
                enabledNoSnapshot == 0,
                "WARN",
                "enabledWithoutSnapshot=" + enabledNoSnapshot,
                "建议先执行元数据采集，补齐 snapshot_time。"
            )
        );
        assertions.add(
            assertion(
                "A05",
                "资产负责人部门已维护",
                noOwnerDept == 0,
                "WARN",
                "noOwnerDept=" + noOwnerDept,
                "建议补充 owner_dept，避免权限策略和工单路由失效。"
            )
        );
        assertions.add(
            assertion(
                "A06",
                "失效资产占比可控",
                datasets.isEmpty() || ((double) staleDatasets / (double) datasets.size()) < 0.3d,
                "WARN",
                "staleRatio=" + (datasets.isEmpty() ? 0 : String.format(Locale.ROOT, "%.4f", ((double) staleDatasets / (double) datasets.size()))),
                "建议清理失效资产或重新采集，避免模型映射到历史表。"
            )
        );

        long failedCount = assertions.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed"))).count();
        long errorCount = assertions
            .stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("passed")))
            .filter(item -> Objects.equals(item.get("severity"), "ERROR"))
            .count();
        long warningCount = assertions
            .stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("passed")))
            .filter(item -> Objects.equals(item.get("severity"), "WARN"))
            .count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("datasetTotal", datasets.size());
        counts.put("datasetEnabled", enabledDatasets);
        counts.put("datasetStale", staleDatasets);
        counts.put("tableTotal", tableRepo.count());
        counts.put("grantTotal", grantRepo.count());
        counts.put("qualityRunTotal", allRuns.size());
        counts.put("issueTicketTotal", allIssues.size());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put(
            "orphanQualityRuns",
            orphanRuns
                .stream()
                .map(run -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", run.getId() != null ? run.getId().toString() : null);
                    item.put("datasetId", run.getDatasetId() != null ? run.getDatasetId().toString() : null);
                    item.put("status", normalizeUpper(run.getStatus()));
                    return item;
                })
                .toList()
        );
        details.put(
            "orphanIssueTickets",
            orphanIssues
                .stream()
                .map(issue -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", issue.getId() != null ? issue.getId().toString() : null);
                    item.put("datasetId", issue.getDatasetId() != null ? issue.getDatasetId().toString() : null);
                    item.put("status", normalizeUpper(issue.getStatus()));
                    return item;
                })
                .toList()
        );

        List<Map<String, Object>> regressionChecklist = List.of(
            checklistItem("UI-01", "资产列表筛选与分页", "/catalog/datasets", "验证关键字/主题域/密级/分层过滤与分页一致性。"),
            checklistItem("UI-02", "资产详情信息完整性", "/catalog/datasets", "验证基础信息、结构信息、治理状态三页签数据完整。"),
            checklistItem("UI-03", "搜索页命中一致性", "/catalog/search", "同一关键字在搜索页与资产列表返回主数据一致。"),
            checklistItem("UI-04", "血缘影响查询", "/catalog/lineage", "资产详情跳转血缘后节点/边数量可复核。"),
            checklistItem("UI-05", "权限审批闭环", "/security/dataset-access-approval", "申请-审批-授权记录可闭环追踪。")
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now());
        payload.put("assertionCount", assertions.size());
        payload.put("failedCount", failedCount);
        payload.put("errorCount", errorCount);
        payload.put("warningCount", warningCount);
        payload.put("counts", counts);
        payload.put("assertions", assertions);
        payload.put("details", details);
        payload.put("regressionChecklist", regressionChecklist);
        return payload;
    }

    private Map<String, Object> assertion(
        String code,
        String name,
        boolean passed,
        String severity,
        String detail,
        String suggestion
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("passed", passed);
        row.put("severity", severity);
        row.put("detail", detail);
        row.put("suggestion", suggestion);
        return row;
    }

    private Map<String, Object> checklistItem(String code, String name, String route, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("name", name);
        row.put("route", route);
        row.put("description", description);
        return row;
    }

    private String normalizeUpper(String text) {
        String value = trimToNull(text);
        return value != null ? value.toUpperCase(Locale.ROOT) : "";
    }

    private record MappingValidationResult(
        List<CatalogClassificationMapping> normalizedItems,
        List<Map<String, Object>> conflicts,
        List<Map<String, Object>> warnings
    ) {}

    // Table schema CRUD, filter and bulk import
    @GetMapping("/tables")
    public ApiResponse<Map<String, Object>> listTables(
        @RequestParam UUID datasetId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(ds) || !accessChecker.departmentAllowed(ds, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
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
        Map<String, Object> before = snapshotTable(existing);
        existing.setName(patch.getName());
        existing.setOwner(patch.getOwner());
        existing.setClassification(patch.getClassification());
        existing.setBizDomain(patch.getBizDomain());
        existing.setTags(patch.getTags());
        var saved = tableRepo.save(existing);
        recordTableMetadataChanges(saved, before, "MANUAL");
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新数据表元数据");
        auditPayload.put("tableId", id.toString());
        UUID datasetId = saved.getDataset() != null ? saved.getDataset().getId() : null;
        if (datasetId != null) {
            auditPayload.put("datasetId", datasetId.toString());
        }
        audit.auditAction(
            "CATALOG_METADATA_TABLE_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload
        );
        return ApiResponses.ok(saved);
    }

    @GetMapping("/tables/{id}/standard-mapping/validate")
    public ApiResponse<Map<String, Object>> validateTableStandardMapping(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standards = loadStandardsById(columns);

        String warehouseLayer = dataset != null ? trimToNull(dataset.getWarehouseLayer()) : null;
        boolean strictDwd = warehouseLayer != null && "DWD".equalsIgnoreCase(warehouseLayer);

        int totalColumns = columns.size();
        int mappedColumns = 0;
        int unmappedColumns = 0;
        int mismatchedColumns = 0;
        List<Map<String, Object>> issues = new ArrayList<>();

        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            UUID standardId = col.getStandardId();
            DataStandard standard = standardId != null ? standards.get(standardId) : null;

            if (standardId == null) {
                unmappedColumns++;
                issues.add(buildStandardIssue(col, null, "未绑定数据元（字段标准）"));
                continue;
            }

            mappedColumns++;
            if (standard == null) {
                mismatchedColumns++;
                issues.add(buildStandardIssue(col, null, "关联的数据元不存在或无权限"));
                continue;
            }

            String typeReason = null;
            String nullableReason = null;

            String standardType = trimToNull(standard.getDataType());
            String columnType = trimToNull(col.getDataType());
            if (standardType != null && columnType != null && !isTypeCompatible(standardType, columnType)) {
                typeReason = "字段类型与数据元不一致";
            }

            Boolean standardNullable = standard.getNullable();
            Boolean columnNullable = col.getNullable();
            if (standardNullable != null && Boolean.FALSE.equals(standardNullable) && Boolean.TRUE.equals(columnNullable)) {
                nullableReason = "字段可空性与数据元不一致（数据元要求不可空）";
            }

            if (typeReason != null || nullableReason != null) {
                mismatchedColumns++;
                String reason = typeReason != null && nullableReason != null ? (typeReason + "；" + nullableReason) : (typeReason != null ? typeReason : nullableReason);
                issues.add(buildStandardIssue(col, standard, reason));
            }
        }

        boolean blocking = strictDwd && (unmappedColumns > 0 || mismatchedColumns > 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("warehouseLayer", warehouseLayer);
        payload.put("strictDwd", strictDwd);
        payload.put("blocking", blocking);
        payload.put("totalColumns", totalColumns);
        payload.put("mappedColumns", mappedColumns);
        payload.put("unmappedColumns", unmappedColumns);
        payload.put("mismatchedColumns", mismatchedColumns);
        payload.put("issues", issues);

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_VALIDATE",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "校验字段与数据元映射",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "warehouseLayer",
                warehouseLayer != null ? warehouseLayer : "",
                "blocking",
                blocking
            )
        );
        return ApiResponses.ok(payload);
    }

    public record StandardAutoMapApplyRequest(Boolean overwrite, Boolean onlyUnmapped) {}

    @GetMapping("/tables/{id}/standard-mapping/auto-map/preview")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> previewAutoMapTableStandardMapping(
        @PathVariable UUID id,
        @RequestParam(name = "overwrite", required = false, defaultValue = "false") boolean overwrite,
        @RequestParam(name = "onlyUnmapped", required = false, defaultValue = "true") boolean onlyUnmapped,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standardsById = loadStandardsById(columns);

        Set<String> codeCandidates = new LinkedHashSet<>();
        Map<UUID, String> hintedCodes = new HashMap<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            String hint = extractStandardCodeHint(col.getComment());
            if (hint != null) {
                hintedCodes.put(col.getId(), hint);
                codeCandidates.add(hint.toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(col.getName())) {
                codeCandidates.add(col.getName().trim().toLowerCase(Locale.ROOT));
            }
        }

        Map<String, DataStandard> standardsByCode = new HashMap<>();
        if (!codeCandidates.isEmpty()) {
            for (DataStandard s : dataStandardRepository.findByCodeLowerIn(codeCandidates)) {
                if (s != null && StringUtils.hasText(s.getCode())) {
                    standardsByCode.put(s.getCode().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        int totalColumns = columns.size();
        int matchedColumns = 0;
        int conflictColumns = 0;
        int noMatchColumns = 0;
        int willUpdateColumns = 0;
        int skippedColumns = 0;

        List<Map<String, Object>> items = new ArrayList<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            UUID columnId = col.getId();

            UUID currentStandardId = col.getStandardId();
            DataStandard currentStandard = currentStandardId != null ? standardsById.get(currentStandardId) : null;
            String columnName = trimToNull(col.getName());

            String hinted = columnId != null ? hintedCodes.get(columnId) : null;
            DataStandard byHint = hinted != null ? standardsByCode.get(hinted.trim().toLowerCase(Locale.ROOT)) : null;
            DataStandard byName = columnName != null ? standardsByCode.get(columnName.trim().toLowerCase(Locale.ROOT)) : null;

            DataStandard proposed = null;
            String source = null;
            String status = null;
            String reason = null;

            if (byHint != null && byName != null && byHint.getId() != null && byName.getId() != null && !byHint.getId().equals(byName.getId())) {
                conflictColumns++;
                status = "CONFLICT";
                reason = "注释STD与字段名匹配到不同数据元";
            } else if (byHint != null) {
                proposed = byHint;
                source = byName != null ? "COMMENT+NAME" : "COMMENT";
            } else if (byName != null) {
                proposed = byName;
                source = "COLUMN_NAME";
            }

            if (status == null) {
                if (proposed == null) {
                    noMatchColumns++;
                    status = "NO_MATCH";
                } else {
                    matchedColumns++;
                    if (currentStandardId != null) {
                        if (proposed.getId() != null && proposed.getId().equals(currentStandardId)) {
                            skippedColumns++;
                            status = "ALREADY_OK";
                        } else if (onlyUnmapped) {
                            skippedColumns++;
                            status = "SKIP_MAPPED";
                            reason = "字段已绑定数据元（onlyUnmapped=true）";
                        } else if (!overwrite) {
                            skippedColumns++;
                            status = "SKIP_MAPPED";
                            reason = "字段已绑定数据元（overwrite=false）";
                        } else {
                            willUpdateColumns++;
                            status = "WILL_UPDATE";
                        }
                    } else {
                        willUpdateColumns++;
                        status = "WILL_UPDATE";
                    }
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("columnId", columnId != null ? columnId.toString() : null);
            item.put("columnName", columnName);
            item.put("columnDataType", trimToNull(col.getDataType()));
            item.put("hintedStandardCode", hinted);
            item.put("currentStandardId", currentStandardId != null ? currentStandardId.toString() : null);
            item.put("currentStandardCode", currentStandard != null ? currentStandard.getCode() : null);
            item.put("proposedStandardId", proposed != null && proposed.getId() != null ? proposed.getId().toString() : null);
            item.put("proposedStandardCode", proposed != null ? proposed.getCode() : null);
            item.put("proposedStandardName", proposed != null ? proposed.getName() : null);
            item.put("source", source);
            item.put("status", status);
            item.put("reason", reason);
            item.put("willUpdate", "WILL_UPDATE".equals(status));
            items.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("totalColumns", totalColumns);
        payload.put("matchedColumns", matchedColumns);
        payload.put("noMatchColumns", noMatchColumns);
        payload.put("conflictColumns", conflictColumns);
        payload.put("willUpdateColumns", willUpdateColumns);
        payload.put("skippedColumns", skippedColumns);
        payload.put("overwrite", overwrite);
        payload.put("onlyUnmapped", onlyUnmapped);
        payload.put("items", items);

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_AUTOMAP_PREVIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "预览自动匹配字段与数据元",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "willUpdate",
                willUpdateColumns,
                "conflicts",
                conflictColumns
            )
        );
        return ApiResponses.ok(payload);
    }

    @PostMapping("/tables/{id}/standard-mapping/auto-map/apply")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> applyAutoMapTableStandardMapping(
        @PathVariable UUID id,
        @RequestBody(required = false) StandardAutoMapApplyRequest body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        boolean overwrite = body != null && body.overwrite != null && body.overwrite.booleanValue();
        boolean onlyUnmapped = body == null || body.onlyUnmapped == null || body.onlyUnmapped.booleanValue();

        CatalogTableSchema table = tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }

        List<CatalogColumnSchema> columns = columnRepo.findByTable(table);
        Map<UUID, DataStandard> standardsById = loadStandardsById(columns);

        Set<String> codeCandidates = new LinkedHashSet<>();
        Map<UUID, String> hintedCodes = new HashMap<>();
        for (CatalogColumnSchema col : columns) {
            if (col == null) continue;
            String hint = extractStandardCodeHint(col.getComment());
            if (hint != null) {
                hintedCodes.put(col.getId(), hint);
                codeCandidates.add(hint.toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(col.getName())) {
                codeCandidates.add(col.getName().trim().toLowerCase(Locale.ROOT));
            }
        }
        Map<String, DataStandard> standardsByCode = new HashMap<>();
        if (!codeCandidates.isEmpty()) {
            for (DataStandard s : dataStandardRepository.findByCodeLowerIn(codeCandidates)) {
                if (s != null && StringUtils.hasText(s.getCode())) {
                    standardsByCode.put(s.getCode().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        int applied = 0;
        int conflicts = 0;
        int skipped = 0;
        List<UUID> updatedIds = new ArrayList<>();

        for (CatalogColumnSchema col : columns) {
            if (col == null || col.getId() == null) continue;
            UUID columnId = col.getId();
            UUID currentStandardId = col.getStandardId();

            String hinted = hintedCodes.get(columnId);
            DataStandard byHint = hinted != null ? standardsByCode.get(hinted.trim().toLowerCase(Locale.ROOT)) : null;
            String columnName = trimToNull(col.getName());
            DataStandard byName = columnName != null ? standardsByCode.get(columnName.trim().toLowerCase(Locale.ROOT)) : null;

            if (byHint != null && byName != null && byHint.getId() != null && byName.getId() != null && !byHint.getId().equals(byName.getId())) {
                conflicts++;
                continue;
            }
            DataStandard proposed = byHint != null ? byHint : byName;
            if (proposed == null || proposed.getId() == null) {
                continue;
            }

            if (currentStandardId != null) {
                if (proposed.getId().equals(currentStandardId)) {
                    skipped++;
                    continue;
                }
                if (onlyUnmapped) {
                    skipped++;
                    continue;
                }
                if (!overwrite) {
                    skipped++;
                    continue;
                }
            }

            Map<String, Object> before = snapshotColumn(col);
            col.setStandardId(proposed.getId());
            CatalogColumnSchema saved = columnRepo.save(col);
            recordColumnMetadataChanges(saved, before, "AUTO_MAP");
            standardsById.put(proposed.getId(), proposed);
            applied++;
            updatedIds.add(saved.getId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tableId", id.toString());
        payload.put("datasetId", dataset != null && dataset.getId() != null ? dataset.getId().toString() : null);
        payload.put("applied", applied);
        payload.put("conflicts", conflicts);
        payload.put("skipped", skipped);
        payload.put("updatedColumnIds", updatedIds.stream().map(UUID::toString).toList());

        audit.auditAction(
            "CATALOG_STANDARD_MAPPING_AUTOMAP_APPLY",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "自动匹配字段与数据元",
                "tableId",
                id.toString(),
                "datasetId",
                dataset != null && dataset.getId() != null ? dataset.getId().toString() : null,
                "applied",
                applied,
                "conflicts",
                conflicts,
                "skipped",
                skipped
            )
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/tables/{id}/changes")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listTableChanges(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        tableRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据表不存在"));
        Page<CatalogMetadataChangeLog> p = metadataChangeLogRepo.findByObjectTypeIgnoreCaseAndObjectId(
            "TABLE",
            id,
            PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdDate"))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", p.getContent());
        payload.put("page", page);
        payload.put("size", size);
        payload.put("total", p.getTotalElements());
        audit.auditAction("CATALOG_METADATA_CHANGELOG_VIEW", AuditStage.SUCCESS, id.toString(), Map.of("summary", "查看数据表元数据变更历史", "tableId", id.toString()));
        return ApiResponses.ok(payload);
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
    public ApiResponse<Map<String, Object>> listColumns(
        @RequestParam UUID tableId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var table = tableRepo.findById(tableId).orElseThrow();
        CatalogDataset dataset = table.getDataset();
        if (dataset != null) {
            String effDept = activeDept != null ? activeDept : claim("dept_code");
            if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
                return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
            }
        }
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
        Map<UUID, DataStandard> standards = loadStandardsById(filtered);
        List<Map<String, Object>> content = filtered.stream().map(col -> toColumnDto(col, standards)).toList();
        audit.audit("READ", "catalog.column", String.valueOf(tableId));
        return ApiResponses.ok(Map.of("content", content, "total", filtered.size()));
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
    public ApiResponse<Map<String, Object>> updateColumn(
        @PathVariable UUID id,
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema patch
    ) {
        var existing = columnRepo.findById(id).orElseThrow();
        Map<String, Object> before = snapshotColumn(existing);
        existing.setName(patch.getName());
        existing.setDataType(patch.getDataType());
        existing.setNullable(patch.getNullable());
        existing.setTags(patch.getTags());
        existing.setComment(patch.getComment());
        existing.setSensitiveTags(patch.getSensitiveTags());
        existing.setStandardId(patch.getStandardId());
        existing.setStandardRule(trimToNull(patch.getStandardRule()));
        existing.setStandardMismatchReason(trimToNull(patch.getStandardMismatchReason()));
        var saved = columnRepo.save(existing);
        recordColumnMetadataChanges(saved, before, "MANUAL");
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新数据字段元数据");
        auditPayload.put("columnId", id.toString());
        UUID tableId = saved.getTable() != null ? saved.getTable().getId() : null;
        if (tableId != null) {
            auditPayload.put("tableId", tableId.toString());
        }
        audit.auditAction(
            "CATALOG_METADATA_COLUMN_EDIT",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload
        );
        Map<UUID, DataStandard> standards = loadStandardsById(List.of(saved));
        return ApiResponses.ok(toColumnDto(saved, standards));
    }

    @GetMapping("/columns/{id}/changes")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listColumnChanges(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        columnRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据字段不存在"));
        Page<CatalogMetadataChangeLog> p = metadataChangeLogRepo.findByObjectTypeIgnoreCaseAndObjectId(
            "COLUMN",
            id,
            PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdDate"))
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", p.getContent());
        payload.put("page", page);
        payload.put("size", size);
        payload.put("total", p.getTotalElements());
        audit.auditAction(
            "CATALOG_METADATA_CHANGELOG_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看数据字段元数据变更历史", "columnId", id.toString())
        );
        return ApiResponses.ok(payload);
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

    private void recordTableMetadataChanges(CatalogTableSchema saved, Map<String, Object> before, String source) {
        if (saved == null || saved.getId() == null) {
            return;
        }
        UUID tableId = saved.getId();
        UUID datasetId = saved.getDataset() != null ? saved.getDataset().getId() : null;
        String actorDept = claim("dept_code");
        List<CatalogMetadataChangeLog> changes = new ArrayList<>();

        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "name", safeText(before.get("name")), saved.getName(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "owner", safeText(before.get("owner")), saved.getOwner(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "classification", safeText(before.get("classification")), saved.getClassification(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "bizDomain", safeText(before.get("bizDomain")), saved.getBizDomain(), "更新数据表元数据", actorDept, source);
        addMetadataChange(changes, "TABLE", tableId, datasetId, null, "tags", safeText(before.get("tags")), saved.getTags(), "更新数据表元数据", actorDept, source);

        if (!changes.isEmpty()) {
            metadataChangeLogRepo.saveAll(changes);
        }
    }

    private void recordColumnMetadataChanges(CatalogColumnSchema saved, Map<String, Object> before, String source) {
        if (saved == null || saved.getId() == null) {
            return;
        }
        UUID columnId = saved.getId();
        UUID tableId = saved.getTable() != null ? saved.getTable().getId() : null;
        UUID datasetId = null;
        if (saved.getTable() != null && saved.getTable().getDataset() != null) {
            datasetId = saved.getTable().getDataset().getId();
        }
        String actorDept = claim("dept_code");
        List<CatalogMetadataChangeLog> changes = new ArrayList<>();

        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "name", safeText(before.get("name")), saved.getName(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "dataType", safeText(before.get("dataType")), saved.getDataType(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "nullable", safeText(before.get("nullable")), safeBool(saved.getNullable()), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "tags", safeText(before.get("tags")), saved.getTags(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "sensitiveTags", safeText(before.get("sensitiveTags")), saved.getSensitiveTags(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "comment", safeText(before.get("comment")), saved.getComment(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardId", safeText(before.get("standardId")), safeText(saved.getStandardId()), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardRule", safeText(before.get("standardRule")), saved.getStandardRule(), "更新数据字段元数据", actorDept, source);
        addMetadataChange(changes, "COLUMN", columnId, datasetId, tableId, "standardMismatchReason", safeText(before.get("standardMismatchReason")), saved.getStandardMismatchReason(), "更新数据字段元数据", actorDept, source);

        if (!changes.isEmpty()) {
            metadataChangeLogRepo.saveAll(changes);
        }
    }

    private void addMetadataChange(
        List<CatalogMetadataChangeLog> out,
        String objectType,
        UUID objectId,
        UUID datasetId,
        UUID tableId,
        String fieldName,
        String beforeValue,
        String afterValue,
        String summary,
        String actorDept,
        String source
    ) {
        String before = normalizeText(beforeValue);
        String after = normalizeText(afterValue);
        if (Objects.equals(before, after)) {
            return;
        }
        CatalogMetadataChangeLog log = new CatalogMetadataChangeLog();
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setDatasetId(datasetId);
        log.setTableId(tableId);
        log.setFieldName(fieldName);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setChangeSummary(summary);
        log.setActorDept(normalizeText(actorDept));
        log.setSource(normalizeText(source));
        out.add(log);
    }

    private Map<String, Object> snapshotTable(CatalogTableSchema table) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (table == null) {
            return m;
        }
        m.put("name", trimToNull(table.getName()));
        m.put("owner", trimToNull(table.getOwner()));
        m.put("classification", trimToNull(table.getClassification()));
        m.put("bizDomain", trimToNull(table.getBizDomain()));
        m.put("tags", trimToNull(table.getTags()));
        return m;
    }

    private Map<String, Object> snapshotColumn(CatalogColumnSchema column) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (column == null) {
            return m;
        }
        m.put("name", trimToNull(column.getName()));
        m.put("dataType", trimToNull(column.getDataType()));
        m.put("nullable", column.getNullable());
        m.put("tags", trimToNull(column.getTags()));
        m.put("sensitiveTags", trimToNull(column.getSensitiveTags()));
        m.put("comment", trimToNull(column.getComment()));
        m.put("standardId", column.getStandardId() != null ? column.getStandardId().toString() : null);
        m.put("standardRule", trimToNull(column.getStandardRule()));
        m.put("standardMismatchReason", trimToNull(column.getStandardMismatchReason()));
        m.put("status", trimToNull(column.getStatus()));
        return m;
    }

    private Map<UUID, DataStandard> loadStandardsById(List<CatalogColumnSchema> columns) {
        if (columns == null || columns.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (CatalogColumnSchema c : columns) {
            if (c == null) continue;
            if (c.getStandardId() != null) {
                ids.add(c.getStandardId());
            }
        }
        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<UUID, DataStandard> map = new HashMap<>();
        for (DataStandard standard : dataStandardRepository.findAllById(ids)) {
            if (standard != null && standard.getId() != null) {
                map.put(standard.getId(), standard);
            }
        }
        return map;
    }

    private Map<String, Object> buildStandardIssue(CatalogColumnSchema col, DataStandard standard, String reason) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("columnId", col.getId() != null ? col.getId().toString() : null);
        issue.put("columnName", col.getName());
        issue.put("columnDataType", trimToNull(col.getDataType()));
        issue.put("columnNullable", col.getNullable());
        issue.put("standardId", col.getStandardId() != null ? col.getStandardId().toString() : null);
        if (standard != null) {
            issue.put("standardCode", standard.getCode());
            issue.put("standardName", standard.getName());
            issue.put("standardDataType", trimToNull(standard.getDataType()));
            issue.put("standardNullable", standard.getNullable());
            issue.put("standardCodeSet", trimToNull(standard.getCodeSet()));
        }
        issue.put("reason", reason);
        return issue;
    }

    private boolean isTypeCompatible(String standardType, String columnType) {
        String standard = normalizeDataType(standardType);
        String column = normalizeDataType(columnType);
        if (standard == null || column == null) {
            return true;
        }
        return standard.equals(column);
    }

    private String normalizeDataType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        String t = rawType.trim().toLowerCase(Locale.ROOT);
        int paren = t.indexOf('(');
        if (paren > 0) {
            t = t.substring(0, paren).trim();
        }
        if (t.isEmpty()) {
            return null;
        }
        return switch (t) {
            case "varchar", "char", "character", "character varying", "string", "text" -> "string";
            case "bigint", "int8", "long" -> "bigint";
            case "int", "integer", "int4" -> "int";
            case "smallint", "int2", "short" -> "smallint";
            case "double", "float8" -> "double";
            case "float", "float4", "real" -> "float";
            case "decimal", "numeric" -> "decimal";
            case "boolean", "bool" -> "boolean";
            case "timestamp", "datetime" -> "timestamp";
            default -> t;
        };
    }

    private Map<String, Object> toColumnDto(CatalogColumnSchema col, Map<UUID, DataStandard> standards) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (col == null) {
            return dto;
        }
        dto.put("id", col.getId());
        dto.put("name", col.getName());
        dto.put("dataType", col.getDataType());
        dto.put("nullable", col.getNullable());
        dto.put("tags", col.getTags());
        dto.put("sensitiveTags", col.getSensitiveTags());
        dto.put("comment", trimToNull(col.getComment()));
        dto.put("standardId", col.getStandardId());
        dto.put("standardRule", trimToNull(col.getStandardRule()));
        dto.put("standardMismatchReason", trimToNull(col.getStandardMismatchReason()));
        dto.put("status", trimToNull(col.getStatus()));

        DataStandard standard = (col.getStandardId() != null && standards != null) ? standards.get(col.getStandardId()) : null;
        if (standard != null) {
            dto.put("standardCode", standard.getCode());
            dto.put("standardName", standard.getName());
            dto.put("standardDataType", standard.getDataType());
            dto.put("standardNullable", standard.getNullable());
            dto.put("standardCodeSet", standard.getCodeSet());
        }
        String computedMismatchReason = computeStandardMismatchReason(col, standard);
        dto.put("computedMismatchReason", computedMismatchReason);
        dto.put("mappingStatus", computeStandardMappingStatus(col, standard, computedMismatchReason));
        return dto;
    }

    private String extractStandardCodeHint(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        Matcher matcher = STD_CODE_PATTERN.matcher(comment);
        if (matcher.find()) {
            String raw = matcher.group(1);
            return StringUtils.hasText(raw) ? raw.trim() : null;
        }
        return null;
    }

    private String computeStandardMismatchReason(CatalogColumnSchema col, DataStandard standard) {
        if (col == null) {
            return null;
        }
        UUID standardId = col.getStandardId();
        if (standardId == null) {
            return "未绑定数据元（字段标准）";
        }
        if (standard == null) {
            return "关联的数据元不存在或无权限";
        }
        String typeReason = null;
        String nullableReason = null;
        String standardType = trimToNull(standard.getDataType());
        String columnType = trimToNull(col.getDataType());
        if (standardType != null && columnType != null && !isTypeCompatible(standardType, columnType)) {
            typeReason = "字段类型与数据元不一致";
        }
        Boolean standardNullable = standard.getNullable();
        Boolean columnNullable = col.getNullable();
        if (standardNullable != null && Boolean.FALSE.equals(standardNullable) && Boolean.TRUE.equals(columnNullable)) {
            nullableReason = "字段可空性与数据元不一致（数据元要求不可空）";
        }
        if (typeReason != null || nullableReason != null) {
            return typeReason != null && nullableReason != null ? (typeReason + "；" + nullableReason) : (typeReason != null ? typeReason : nullableReason);
        }
        return null;
    }

    private String computeStandardMappingStatus(CatalogColumnSchema col, DataStandard standard, String computedMismatchReason) {
        if (col == null) {
            return "UNKNOWN";
        }
        if (col.getStandardId() == null) {
            return "UNMAPPED";
        }
        if (standard == null) {
            return "STANDARD_MISSING";
        }
        if (computedMismatchReason != null) {
            return "MISMATCHED";
        }
        return "OK";
    }

    private String safeBool(Boolean value) {
        if (value == null) {
            return null;
        }
        return Boolean.TRUE.equals(value) ? "true" : "false";
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean canEditDataset(CatalogDataset dataset) {
        if (dataset == null) {
            return false;
        }
        if (SecurityUtils.isOpAdminAccount()) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DEPARTMENT_PRIVILEGED_ROLES)) {
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
        map.put("grantType", grant.getGrantType());
        map.put("canQuery", grant.getCanQuery());
        map.put("canPreview", grant.getCanPreview());
        map.put("validFrom", grant.getValidFrom());
        map.put("validTo", grant.getValidTo());
        map.put("sourceRequestId", grant.getSourceRequestId());
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

    public record OpenMetadataBatchRequest(List<UUID> ids) {}
}
