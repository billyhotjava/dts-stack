package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogDomainResource {

    private final CatalogDomainRepository domainRepo;
    private final AuditService audit;

    public CatalogDomainResource(CatalogDomainRepository domainRepo, AuditService audit) {
        this.domainRepo = domainRepo;
        this.audit = audit;
    }

    @GetMapping("/domains")
    @Transactional(readOnly = true)
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
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogDomain> createDomain(@Valid @RequestBody CatalogDomain domain) {
        if (domain.getParent() != null && domain.getParent().getId() != null) {
            UUID pid = domain.getParent().getId();
            domain.setParent(domainRepo.findById(pid).orElse(null));
        }
        CatalogDomain saved = domainRepo.save(domain);
        audit.audit("CREATE", "catalog.domain", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/domains/{id}")
    @Transactional
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
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteDomain(@PathVariable UUID id) {
        domainRepo.deleteById(id);
        audit.audit("DELETE", "catalog.domain", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @GetMapping("/domains/tree")
    @Transactional(readOnly = true)
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
    @Transactional
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
}
