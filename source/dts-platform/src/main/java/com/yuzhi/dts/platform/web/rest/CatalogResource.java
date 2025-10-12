package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.CatalogFeatureProperties;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/catalog")
@Transactional
public class CatalogResource {

    private static final String TYPE_INCEPTOR = "INCEPTOR";

    private final CatalogDomainRepository domainRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final CatalogClassificationMappingRepository mappingRepo;
    private final AuditService audit;
    private final ClassificationUtils classificationUtils;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository policyRepo;
    private final com.yuzhi.dts.platform.service.security.AccessChecker accessChecker;
    private final com.yuzhi.dts.platform.service.security.SecurityViewService securityViewService;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository tableRepo;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository columnRepo;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository rowFilterRepo;
    private final com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository secureViewRepo;
    private final InfraDataSourceRepository infraDataSourceRepository;
    private final CatalogFeatureProperties catalogFeatures;

    public CatalogResource(
        CatalogDomainRepository domainRepo,
        CatalogDatasetRepository datasetRepo,
        CatalogMaskingRuleRepository maskingRepo,
        CatalogClassificationMappingRepository mappingRepo,
        AuditService audit,
        ClassificationUtils classificationUtils,
        com.yuzhi.dts.platform.repository.catalog.CatalogAccessPolicyRepository policyRepo,
        com.yuzhi.dts.platform.service.security.AccessChecker accessChecker,
        com.yuzhi.dts.platform.service.security.SecurityViewService securityViewService,
        com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository tableRepo,
        com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository columnRepo,
        com.yuzhi.dts.platform.repository.catalog.CatalogRowFilterRuleRepository rowFilterRepo,
        com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository secureViewRepo,
        InfraDataSourceRepository infraDataSourceRepository,
        CatalogFeatureProperties catalogFeatures
    ) {
        this.domainRepo = domainRepo;
        this.datasetRepo = datasetRepo;
        this.maskingRepo = maskingRepo;
        this.mappingRepo = mappingRepo;
        this.audit = audit;
        this.classificationUtils = classificationUtils;
        this.policyRepo = policyRepo;
        this.accessChecker = accessChecker;
        this.securityViewService = securityViewService;
        this.tableRepo = tableRepo;
        this.columnRepo = columnRepo;
        this.rowFilterRepo = rowFilterRepo;
        this.secureViewRepo = secureViewRepo;
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
        audit.audit("READ", "catalog.config", "config");
        return ApiResponses.ok(payload);
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domains", domainRepo.count());
        map.put("datasets", datasetRepo.count());
        map.put("maskingRules", maskingRepo.count());
        map.put("classificationMappings", mappingRepo.count());
        audit.audit("READ", "catalog.summary", "summary");
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
        @RequestParam(required = false) String dataLevel,
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) String ownerDept,
        @RequestParam(required = false) String shareScope,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String exposedBy,
        @RequestParam(required = false) String owner,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestHeader(value = "X-Active-Scope", required = false) String activeScope,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        Page<CatalogDataset> p = datasetRepo.findAll(pageable);
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
            // ABAC filters (optional)
            .filter(ds -> dataLevel == null || (ds.getDataLevel() != null && ds.getDataLevel().equalsIgnoreCase(dataLevel)))
            .filter(ds -> scope == null || (ds.getScope() != null && ds.getScope().equalsIgnoreCase(scope)))
            .filter(ds -> ownerDept == null || (ds.getOwnerDept() != null && ds.getOwnerDept().equalsIgnoreCase(ownerDept)))
            .filter(ds -> shareScope == null || (ds.getShareScope() != null && ds.getShareScope().equalsIgnoreCase(shareScope)))
            .filter(ds -> exposedBy == null || (ds.getExposedBy() != null && ds.getExposedBy().equalsIgnoreCase(exposedBy)))
            .filter(ds -> owner == null || (ds.getOwner() != null && ds.getOwner().toLowerCase().contains(owner.toLowerCase())))
            .filter(ds -> tag == null || (ds.getTags() != null && ds.getTags().toLowerCase().contains(tag.toLowerCase())))
            // RBAC/level gate
            .filter(accessChecker::canRead)
            // Scope gate using active context (headers injected by frontend)
            .filter(ds -> accessChecker.scopeAllowed(ds,
                activeScope != null ? activeScope : "DEPT",
                activeDept))
            .map(this::toDatasetDto)
            .toList();
        Map<String, Object> data = Map.of("content", filtered, "total", p.getTotalElements());
        audit.audit("READ", "catalog.dataset", "page=" + page);
        return ApiResponses.ok(data);
    }

    @GetMapping("/datasets/{id}")
    public ApiResponse<Optional<Map<String, Object>>> getDataset(@PathVariable UUID id) {
        Optional<Map<String, Object>> ds = datasetRepo
            .findById(id)
            .filter(accessChecker::canRead)
            .map(this::toDatasetDto);
        audit.audit("READ", "catalog.dataset", id.toString());
        return ApiResponses.ok(ds);
    }

    // Access Policy CRUD (minimal)
    @GetMapping("/access-policies")
    public ApiResponse<Optional<Map<String, Object>>> getPolicy(@RequestParam UUID datasetId) {
        Optional<CatalogDataset> ds = datasetRepo.findById(datasetId);
        Optional<Map<String, Object>> resp = ds.flatMap(policyRepo::findByDataset).map(p -> Map.of(
            "datasetId", datasetId,
            "allowRoles", p.getAllowRoles(),
            "rowFilter", p.getRowFilter(),
            "defaultMasking", p.getDefaultMasking()
        ));
        audit.audit("READ", "catalog.accessPolicy", String.valueOf(datasetId));
        return ApiResponses.ok(resp);
    }

    @PutMapping("/access-policies/{datasetId}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> upsertPolicy(@PathVariable UUID datasetId, @RequestBody Map<String, Object> body) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        var p = policyRepo.findByDataset(ds).orElseGet(() -> {
            com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy np = new com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy();
            np.setDataset(ds);
            return np;
        });
        p.setAllowRoles(Objects.toString(body.get("allowRoles"), null));
        p.setRowFilter(Objects.toString(body.get("rowFilter"), null));
        p.setDefaultMasking(Objects.toString(body.get("defaultMasking"), null));
        var saved = policyRepo.save(p);
        audit.audit("UPDATE", "catalog.accessPolicy", String.valueOf(datasetId));
        return ApiResponses.ok(Map.of(
            "datasetId", datasetId,
            "allowRoles", saved.getAllowRoles(),
            "rowFilter", saved.getRowFilter(),
            "defaultMasking", saved.getDefaultMasking()
        ));
    }

    @GetMapping("/access-policies/{datasetId}/validate")
    public ApiResponse<Map<String, Object>> validatePolicy(@PathVariable UUID datasetId) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        boolean classificationOk = classificationUtils.canAccess(ds.getClassification());
        var polOpt = policyRepo.findByDataset(ds);
        boolean roleMatched = true;
        String rolesCsv = polOpt.map(com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy::getAllowRoles).orElse(null);
        if (rolesCsv != null && !rolesCsv.isBlank()) {
            String[] roles = rolesCsv.split("\\s*,\\s*");
            roleMatched = com.yuzhi.dts.platform.security.SecurityUtils.hasCurrentUserAnyOfAuthorities(roles);
        }
        boolean allowed = classificationOk && roleMatched;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", datasetId);
        result.put("allowed", allowed);
        result.put("userMaxLevel", classificationUtils.getCurrentUserMaxLevel());
        result.put("datasetLevel", ds.getClassification());
        result.put("classificationOk", classificationOk);
        result.put("roleMatched", roleMatched);
        audit.audit(allowed ? "ALLOW" : "DENY", "catalog.accessPolicy.validate", String.valueOf(datasetId));
        return ApiResponses.ok(result);
    }

    @GetMapping("/security-views/{datasetId}/preview")
    public ApiResponse<Map<String, String>> previewSecurityViews(@PathVariable UUID datasetId) {
        CatalogDataset ds = datasetRepo.findById(datasetId).orElseThrow();
        var policy = policyRepo.findByDataset(ds).orElse(null);
        var sqls = securityViewService.previewViews(ds, policy);
        audit.audit("READ", "catalog.securityView.preview", String.valueOf(datasetId));
        return ApiResponses.ok(sqls);
    }

    @GetMapping("/security-views/{datasetId}")
    public ApiResponse<List<com.yuzhi.dts.platform.domain.catalog.CatalogSecureView>> listSecurityViews(@PathVariable UUID datasetId) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        var list = secureViewRepo.findByDataset(ds);
        audit.audit("READ", "catalog.securityView", String.valueOf(datasetId));
        return ApiResponses.ok(list);
    }

    @PostMapping("/security-views/{datasetId}/generate")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> generateSecurityViews(@PathVariable UUID datasetId, @RequestBody(required = false) Map<String, Object> body) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        var policy = policyRepo.findByDataset(ds).orElse(null);
        var sqls = securityViewService.previewViews(ds, policy);
        java.util.List<String> levels = java.util.List.of("PUBLIC", "INTERNAL", "SECRET", "TOP_SECRET");
        java.util.List<com.yuzhi.dts.platform.domain.catalog.CatalogSecureView> toSave = new java.util.ArrayList<>();
        for (String lvl : levels) {
            com.yuzhi.dts.platform.domain.catalog.CatalogSecureView v = new com.yuzhi.dts.platform.domain.catalog.CatalogSecureView();
            v.setDataset(ds);
            String table = ds.getHiveTable() != null && !ds.getHiveTable().isBlank() ? ds.getHiveTable() : ds.getName();
            v.setViewName("sv_" + table + "_" + lvl.toLowerCase());
            v.setLevel(lvl);
            v.setRefresh(Objects.toString(body != null ? body.get("refresh") : null, "NONE"));
            v.setRowFilter(policy != null ? policy.getRowFilter() : null);
            toSave.add(v);
        }
        secureViewRepo.saveAll(toSave);
        audit.audit("CREATE", "catalog.securityView.generate", String.valueOf(datasetId));
        return ApiResponses.ok(Map.of("generated", toSave.size(), "sql", sqls));
    }

    @PostMapping("/security-views/{datasetId}/rebuild")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> rebuildSecurityViews(@PathVariable UUID datasetId) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        secureViewRepo.findByDataset(ds).forEach(v -> secureViewRepo.deleteById(v.getId()));
        audit.audit("DELETE", "catalog.securityView.rollback", String.valueOf(datasetId));
        return generateSecurityViews(datasetId, Map.of());
    }

    @DeleteMapping("/security-views/{datasetId}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> rollbackSecurityViews(@PathVariable UUID datasetId) {
        var ds = datasetRepo.findById(datasetId).orElseThrow();
        secureViewRepo.findByDataset(ds).forEach(v -> secureViewRepo.deleteById(v.getId()));
        audit.audit("DELETE", "catalog.securityView.rollback", String.valueOf(datasetId));
        return ApiResponses.ok(Boolean.TRUE);
    }

    private Map<String, Object> toDatasetDto(CatalogDataset d) {
        UUID domainId = d.getDomain() != null ? d.getDomain().getId() : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("domainId", domainId);
        m.put("type", d.getType());
        m.put("classification", d.getClassification());
        // ABAC fields (optional for backward compatibility)
        m.put("dataLevel", d.getDataLevel());
        m.put("scope", d.getScope());
        m.put("ownerDept", d.getOwnerDept());
        m.put("shareScope", d.getShareScope());
        m.put("owner", d.getOwner());
        m.put("hiveDatabase", d.getHiveDatabase());
        m.put("hiveTable", d.getHiveTable());
        m.put("trinoCatalog", d.getTrinoCatalog());
        m.put("tags", d.getTags());
        m.put("exposedBy", d.getExposedBy());
        return m;
    }

    @PostMapping("/datasets")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<CatalogDataset> createDataset(@Valid @RequestBody CatalogDataset dataset) {
        applySourcePolicy(dataset);
        validateAbacFields(dataset);
        ensurePrimarySourceIfRequired(dataset);
        CatalogDataset saved = datasetRepo.save(dataset);
        audit.audit("CREATE", "catalog.dataset", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PostMapping("/datasets/import")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> importDatasets(@RequestBody List<CatalogDataset> items) {
        List<CatalogDataset> prepared = new ArrayList<>(items.size());
        for (CatalogDataset item : items) {
            applySourcePolicy(item);
            ensurePrimarySourceIfRequired(item);
            prepared.add(item);
        }
        List<CatalogDataset> saved = datasetRepo.saveAll(prepared);
        audit.audit("CREATE", "catalog.dataset.import", "count=" + saved.size());
        return ApiResponses.ok(Map.of("imported", saved.size()));
    }

    @PutMapping("/datasets/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<CatalogDataset> updateDataset(@PathVariable UUID id, @Valid @RequestBody CatalogDataset patch) {
        CatalogDataset existing = datasetRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setType(patch.getType());
        // Keep dataset type normalization, but do not hard-require primary source when updating
        // so that metadata changes (e.g., ownerDept, dataLevel) can be saved even if Hive/Inceptor
        // isn’t configured in dev environments. Connectivity will still be enforced at execution time
        // (e.g., preview/sync operations).
        applySourcePolicy(existing);
        existing.setClassification(patch.getClassification());
        // ABAC fields
        existing.setDataLevel(patch.getDataLevel());
        existing.setScope(patch.getScope());
        existing.setOwnerDept(patch.getOwnerDept());
        existing.setShareScope(patch.getShareScope());
        validateAbacFields(existing);
        existing.setOwner(patch.getOwner());
        existing.setDomain(patch.getDomain());
        existing.setHiveDatabase(patch.getHiveDatabase());
        existing.setHiveTable(patch.getHiveTable());
        existing.setTrinoCatalog(patch.getTrinoCatalog());
        existing.setTags(patch.getTags());
        existing.setExposedBy(patch.getExposedBy());
        CatalogDataset saved = datasetRepo.save(existing);
        audit.audit("UPDATE", "catalog.dataset", id.toString());
        return ApiResponses.ok(saved);
    }

    private void validateAbacFields(CatalogDataset d) {
        String scope = Optional.ofNullable(d.getScope()).map(String::trim).map(String::toUpperCase).orElse("");
        if (scope.isEmpty()) return; // backward compatible
        switch (scope) {
            case "DEPT" -> {
                String dept = Optional.ofNullable(d.getOwnerDept()).map(String::trim).orElse("");
                if (dept.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当 scope=DEPT 时，ownerDept 不能为空");
                }
            }
            case "INST" -> {
                String share = Optional.ofNullable(d.getShareScope()).map(String::trim).map(String::toUpperCase).orElse("");
                if (share.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当 scope=INST 时，shareScope 不能为空");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法作用域: " + scope);
        }
    }

    @DeleteMapping("/datasets/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Boolean> deleteDataset(@PathVariable UUID id) {
        datasetRepo.deleteById(id);
        audit.audit("DELETE", "catalog.dataset", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
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
        long matches = infraDataSourceRepository.countByTypeIgnoreCase(defaultSource);
        if (matches > 0) {
            return true;
        }
        if (TYPE_INCEPTOR.equals(defaultSource)) {
            return infraDataSourceRepository.countByTypeIgnoreCase("HIVE") > 0;
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
            return TYPE_INCEPTOR;
        }
        return configured;
    }

    // Masking rules CRUD
    @GetMapping("/masking-rules")
    public ApiResponse<List<CatalogMaskingRule>> listMaskingRules() {
        List<CatalogMaskingRule> list = maskingRepo.findAll();
        audit.audit("READ", "catalog.masking", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/masking-rules")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<CatalogMaskingRule> createMasking(@Valid @RequestBody CatalogMaskingRule rule) {
        CatalogMaskingRule saved = maskingRepo.save(rule);
        audit.audit("CREATE", "catalog.masking", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/masking-rules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<List<CatalogClassificationMapping>> replaceMapping(
        @RequestBody List<CatalogClassificationMapping> items
    ) {
        mappingRepo.deleteAll();
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(items);
        audit.audit("UPDATE", "catalog.classificationMapping", "replace:" + saved.size());
        return ApiResponses.ok(saved);
    }

    @PostMapping("/classification-mapping/import")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "','" + AuthoritiesConstants.OP_ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema> createTable(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema table
    ) {
        var saved = tableRepo.save(table);
        audit.audit("CREATE", "catalog.table", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Boolean> deleteTable(@PathVariable UUID id) {
        tableRepo.deleteById(id);
        audit.audit("DELETE", "catalog.table", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/tables/import")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
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
                (c.getSensitiveTags() != null && c.getSensitiveTags().toLowerCase().contains(keyword.toLowerCase()))
            )
            .toList();
        audit.audit("READ", "catalog.column", String.valueOf(tableId));
        return ApiResponses.ok(Map.of("content", filtered, "total", filtered.size()));
    }

    @PostMapping("/columns")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema> createColumn(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema column
    ) {
        var saved = columnRepo.save(column);
        audit.audit("CREATE", "catalog.column", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/columns/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema> updateColumn(
        @PathVariable UUID id,
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema patch
    ) {
        var existing = columnRepo.findById(id).orElseThrow();
        existing.setName(patch.getName());
        existing.setDataType(patch.getDataType());
        existing.setNullable(patch.getNullable());
        existing.setTags(patch.getTags());
        existing.setSensitiveTags(patch.getSensitiveTags());
        var saved = columnRepo.save(existing);
        audit.audit("UPDATE", "catalog.column", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/columns/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule> createRowFilter(
        @Valid @RequestBody com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule rule
    ) {
        var saved = rowFilterRepo.save(rule);
        audit.audit("CREATE", "catalog.rowFilter", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/row-filter-rules/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
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
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.CATALOG_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
    public ApiResponse<Boolean> deleteRowFilter(@PathVariable UUID id) {
        rowFilterRepo.deleteById(id);
        audit.audit("DELETE", "catalog.rowFilter", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }
}
