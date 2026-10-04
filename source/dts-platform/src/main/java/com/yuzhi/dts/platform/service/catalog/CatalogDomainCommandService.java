package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogDomainCommandService {

    private final CatalogDomainRepository domainRepository;
    private final AuditService auditService;
    private final CatalogDomainVisibilityService visibilityService;
    private final ArchitectureDictionaryWriteGuard writeGuard;

    public CatalogDomainCommandService(
        CatalogDomainRepository domainRepository,
        AuditService auditService,
        CatalogDomainVisibilityService visibilityService,
        ArchitectureDictionaryWriteGuard writeGuard
    ) {
        this.domainRepository = domainRepository;
        this.auditService = auditService;
        this.visibilityService = visibilityService;
        this.writeGuard = writeGuard;
    }

    @Transactional
    public CatalogDomain create(CatalogDomain domain) {
        writeGuard.requireWriteAccess();
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
        CatalogDomain saved = domainRepository.save(domain);
        requireMaintainAccess(saved);
        auditService.auditActionStrict("CATALOG_DOMAIN_CREATE", AuditStage.SUCCESS, saved.getId().toString(), null);
        return saved;
    }

    @Transactional
    public CatalogDomain update(UUID id, CatalogDomain patch) {
        writeGuard.requireWriteAccess();
        CatalogDomain existing = domainRepository.findById(id).orElseThrow();
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
        CatalogDomain saved = domainRepository.save(existing);
        auditService.auditActionStrict("CATALOG_DOMAIN_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        writeGuard.requireWriteAccess();
        CatalogDomain domain = domainRepository.findById(id).orElseThrow();
        requireMaintainAccess(domain);
        if (domainRepository.existsByParentId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog domain has child domains and cannot be deleted");
        }
        domainRepository.deleteById(id);
        auditService.auditActionStrict("CATALOG_DOMAIN_DELETE", AuditStage.SUCCESS, id.toString(), null);
    }

    @Transactional
    public CatalogDomain move(UUID id, Object newParentId) {
        writeGuard.requireWriteAccess();
        CatalogDomain domain = domainRepository.findById(id).orElseThrow();
        requireMaintainAccess(domain);
        if (newParentId == null || String.valueOf(newParentId).isBlank()) {
            domain.setParent(null);
        } else {
            UUID parentId;
            try {
                parentId = UUID.fromString(String.valueOf(newParentId));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog domain parent", exception);
            }
            domain.setParent(resolveVisibleParent(parentId));
        }
        CatalogDomain saved = domainRepository.save(domain);
        auditService.auditActionStrict("CATALOG_DOMAIN_MOVE", AuditStage.SUCCESS, id.toString(), null);
        return saved;
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
}
