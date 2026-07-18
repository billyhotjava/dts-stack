package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
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

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogDomainResource {

    private final CatalogDomainRepository domainRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AuditService audit;
    private final CatalogDomainVisibilityService visibilityService;

    public CatalogDomainResource(
        CatalogDomainRepository domainRepo,
        CatalogDatasetRepository datasetRepo,
        AuditService audit,
        CatalogDomainVisibilityService visibilityService
    ) {
        this.domainRepo = domainRepo;
        this.datasetRepo = datasetRepo;
        this.audit = audit;
        this.visibilityService = visibilityService;
    }

    @GetMapping("/domains")
    @Transactional(readOnly = true)
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
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> createDomain(@Valid @RequestBody CatalogDomain domain) {
        if (domain.getId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New catalog domain must not include id");
        }
        if (domain.getLifecycleStatus() == null) {
            domain.setLifecycleStatus(CatalogDomainLifecycleStatus.ACTIVE);
        }
        if (domain.getAccessPolicy() == null) {
            domain.setAccessPolicy(CatalogDomainAccessPolicy.PUBLIC);
        }
        domain.setParent(resolveVisibleParent(domain.getParent()));
        CatalogDomain saved = domainRepo.save(domain);
        requireMaintainAccess(saved);
        audit.auditAction("CATALOG_DOMAIN_CREATE", AuditStage.SUCCESS, saved.getId().toString(), null);
        return ApiResponses.ok(saved);
    }

    @PutMapping("/domains/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> updateDomain(@PathVariable UUID id, @Valid @RequestBody CatalogDomain patch) {
        CatalogDomain existing = domainRepo.findById(id).orElseThrow();
        boolean restrictedBeforeUpdate = existing.getAccessPolicy() == CatalogDomainAccessPolicy.RESTRICTED;
        if (restrictedBeforeUpdate) {
            requireMaintainAccess(existing);
        }
        CatalogDomain resolvedParent = resolveVisibleParent(patch.getParent());
        existing.setName(patch.getName());
        existing.setCode(patch.getCode());
        existing.setOwner(patch.getOwner());
        existing.setDescription(patch.getDescription());
        if (patch.getLifecycleStatus() != null) {
            existing.setLifecycleStatus(patch.getLifecycleStatus());
        }
        if (patch.getAccessPolicy() != null) {
            existing.setAccessPolicy(patch.getAccessPolicy());
        }
        existing.setParent(resolvedParent);
        if (!restrictedBeforeUpdate) {
            requireMaintainAccess(existing);
        }
        CatalogDomain saved = domainRepo.save(existing);
        audit.auditAction("CATALOG_DOMAIN_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/domains/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDomain(@PathVariable UUID id) {
        CatalogDomain domain = domainRepo.findById(id).orElseThrow();
        requireMaintainAccess(domain);
        domainRepo.deleteById(id);
        audit.auditAction("CATALOG_DOMAIN_DELETE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/domains/tree")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> getDomainTree() {
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
        audit.auditAction("CATALOG_DOMAIN_TREE", AuditStage.SUCCESS, "tree", null);
        return ApiResponses.ok(roots);
    }

    @GetMapping("/domains/{id}/asset-stats")
    @Transactional(readOnly = true)
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
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> moveDomain(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        CatalogDomain d = domainRepo.findById(id).orElseThrow();
        requireMaintainAccess(d);
        Object newParentId = body.get("newParentId");
        if (newParentId == null || String.valueOf(newParentId).isBlank()) {
            d.setParent(null);
        } else {
            try {
                UUID pid = UUID.fromString(String.valueOf(newParentId));
                d.setParent(resolveVisibleParent(pid));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog domain parent", exception);
            }
        }
        CatalogDomain saved = domainRepo.save(d);
        audit.auditAction("CATALOG_DOMAIN_MOVE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(saved);
    }

    private CatalogDomain resolveVisibleParent(CatalogDomain parent) {
        return parent == null || parent.getId() == null ? null : resolveVisibleParent(parent.getId());
    }

    private CatalogDomain resolveVisibleParent(UUID parentId) {
        return visibilityService
            .findVisibleById(parentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog domain parent not found"));
    }

    private void requireMaintainAccess(CatalogDomain domain) {
        if (domain.getAccessPolicy() == CatalogDomainAccessPolicy.RESTRICTED && !visibilityService.canMaintain(domain)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Restricted catalog domain requires EDIT or MANAGE access"
            );
        }
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
