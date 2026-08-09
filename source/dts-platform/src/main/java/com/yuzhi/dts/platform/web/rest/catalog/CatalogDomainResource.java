package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetStatsProjectionView;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainCommandService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
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

import static com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogDomainResource {

    private final CatalogDatasetRepository datasetRepo;
    private final AuditService audit;
    private final CatalogDomainVisibilityService visibilityService;
    private final CatalogAssetRegistrationService assetRegistrationService;
    private final CatalogResourceHelper helper;
    private final CatalogDomainCommandService commandService;

    public CatalogDomainResource(
        CatalogDatasetRepository datasetRepo,
        AuditService audit,
        CatalogDomainVisibilityService visibilityService,
        CatalogAssetRegistrationService assetRegistrationService,
        CatalogResourceHelper helper,
        CatalogDomainCommandService commandService
    ) {
        this.datasetRepo = datasetRepo;
        this.audit = audit;
        this.visibilityService = visibilityService;
        this.assetRegistrationService = assetRegistrationService;
        this.helper = helper;
        this.commandService = commandService;
    }

    @GetMapping("/domains")
    @Transactional
    public ApiResponse<Map<String, Object>> listDomains(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdDate"), Sort.Order.asc("id")));
        String k = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        Page<CatalogDomain> p = visibilityService.findVisiblePage(k, pageable);
        Set<UUID> requestedParentIds = Set.copyOf(
            p
                .getContent()
                .stream()
                .map(CatalogDomain::getParent)
                .filter(Objects::nonNull)
                .map(CatalogDomain::getId)
                .filter(Objects::nonNull)
                .toList()
        );
        Set<UUID> visibleParentIds = visibilityService.findVisibleIds(requestedParentIds);
        List<CatalogDomainListItem> content = p
            .getContent()
            .stream()
            .map(domain -> toListItem(domain, visibleParentIds))
            .toList();
        Map<String, Object> data = Map.of("content", content, "total", p.getTotalElements());
        audit.auditAction("CATALOG_DOMAIN_LIST", AuditStage.SUCCESS, "page=" + page, null);
        return ApiResponses.ok(data);
    }

    @PostMapping("/domains")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<CatalogDomain> createDomain(@Valid @RequestBody CatalogDomain domain) {
        return ApiResponses.ok(commandService.create(domain));
    }

    @PutMapping("/domains/{id}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<CatalogDomain> updateDomain(@PathVariable UUID id, @Valid @RequestBody CatalogDomain patch) {
        return ApiResponses.ok(commandService.update(id, patch));
    }

    @DeleteMapping("/domains/{id}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<Boolean> deleteDomain(@PathVariable UUID id) {
        commandService.delete(id);
        return ApiResponses.ok(Boolean.TRUE);
    }

    /**
     * 主题域树。{@code withStats=true} 时额外返回域级资产统计，供资产地图左侧范围导航使用。
     *
     * <p>统计只读增量投影，返回旧导航字段并补充 asOf/freshness/approximate 证据。
     * 缺省不带统计，既有调用方零影响；投影重建或过期时不回退为请求内资产全表扫描。
     */
    @GetMapping("/domains/tree")
    @Transactional
    public ApiResponse<Object> getDomainTree(
        @RequestParam(name = "withStats", defaultValue = "false") boolean withStats,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> roots = buildDomainTree();
        if (!withStats) {
            audit.auditAction("CATALOG_DOMAIN_TREE", AuditStage.SUCCESS, "tree", null);
            return ApiResponses.ok(roots);
        }
        CatalogAssetStatsProjectionView stats = CatalogAssetStatsProjectionView.from(assetRegistrationService.stats(null));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tree", roots);
        payload.put("stats", stats);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("freshness", stats.freshness().name());
        auditPayload.put("approximate", stats.approximate());
        auditPayload.put("projectionState", stats.projectionState());
        audit.auditAction("CATALOG_DOMAIN_TREE", AuditStage.SUCCESS, "tree-with-stats", auditPayload);
        return ApiResponses.ok(payload);
    }

    private List<Map<String, Object>> buildDomainTree() {
        List<CatalogDomain> all = visibilityService.findAllVisible();
        Map<UUID, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (CatalogDomain d : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getName());
            m.put("code", d.getCode());
            m.put("owner", d.getOwner());
            m.put("description", d.getDescription());
            m.put("lifecycleStatus", d.getLifecycleStatus());
            m.put("accessPolicy", d.getAccessPolicy());
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
                m.put("parentId", null);
                roots.add(m);
            }
        }
        return roots;
    }

    @GetMapping("/domains/{id}/asset-stats")
    @Transactional
    public ApiResponse<Map<String, Object>> getDomainAssetStats(@PathVariable UUID id) {
        CatalogDomain domain = visibilityService
            .findVisibleById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog domain not found"));
        long datasetCount = datasetRepo.countByDomain(domain);
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("datasetCount", datasetCount);
        stats.put("indicatorCount", null);
        stats.put("qualityRuleCount", null);
        audit.auditAction("CATALOG_DOMAIN_ASSET_STATS_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(stats);
    }

    @PostMapping("/domains/{id}/move")
    @PreAuthorize(WRITE_EXPRESSION)
    public ApiResponse<CatalogDomain> moveDomain(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ApiResponses.ok(commandService.move(id, body.get("newParentId")));
    }

    private static CatalogDomainListItem toListItem(CatalogDomain domain, Set<UUID> visibleParentIds) {
        UUID parentId = domain.getParent() == null ? null : domain.getParent().getId();
        CatalogDomainParentRef parent = parentId != null && visibleParentIds.contains(parentId)
            ? new CatalogDomainParentRef(parentId)
            : null;
        return new CatalogDomainListItem(
            domain.getCreatedBy(),
            domain.getCreatedDate(),
            domain.getLastModifiedBy(),
            domain.getLastModifiedDate(),
            domain.getId(),
            domain.getName(),
            domain.getCode(),
            domain.getOwner(),
            domain.getDescription(),
            parent,
            domain.getLifecycleStatus(),
            domain.getAccessPolicy()
        );
    }

    public record CatalogDomainParentRef(UUID id) {}

    public record CatalogDomainListItem(
        String createdBy,
        java.time.Instant createdDate,
        String lastModifiedBy,
        java.time.Instant lastModifiedDate,
        UUID id,
        String name,
        String code,
        String owner,
        String description,
        CatalogDomainParentRef parent,
        CatalogDomainLifecycleStatus lifecycleStatus,
        CatalogDomainAccessPolicy accessPolicy
    ) {}
}
