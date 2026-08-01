package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned adapter; JPA entities never cross the public port. */
@Component
@Transactional(readOnly = true)
public class JpaCatalogDomainAccessReadAdapter implements CatalogDomainAccessReadPort {

    private final CatalogDomainRepository domains;
    private final CatalogDomainVisibilityService visibility;

    public JpaCatalogDomainAccessReadAdapter(
        CatalogDomainRepository domains,
        CatalogDomainVisibilityService visibility
    ) {
        this.domains = domains;
        this.visibility = visibility;
    }

    @Override
    public boolean canRead(UUID domainId) {
        return domainId != null && domains.findById(domainId).filter(visibility::canRead).isPresent();
    }

    @Override
    public boolean canMaintain(UUID domainId) {
        return domainId != null && domains.findById(domainId).filter(visibility::canMaintain).isPresent();
    }

    @Override
    public Set<UUID> visibleDomainIds() {
        return Set.copyOf(
            visibility.findAllVisible().stream().map(CatalogDomain::getId).filter(Objects::nonNull).toList()
        );
    }

    @Override
    public DomainSnapshot resolve(UUID domainId) {
        if (domainId == null) {
            return DomainSnapshot.redacted(null, DomainStatus.MISSING);
        }
        return domains.findById(domainId).map(this::resolveExisting).orElseGet(() -> DomainSnapshot.redacted(domainId, DomainStatus.MISSING));
    }

    private DomainSnapshot resolveExisting(CatalogDomain domain) {
        if (!visibility.canRead(domain)) {
            return DomainSnapshot.redacted(domain.getId(), DomainStatus.FORBIDDEN);
        }
        DomainStatus status = domain.getLifecycleStatus() == CatalogDomainLifecycleStatus.ARCHIVED
            ? DomainStatus.ARCHIVED
            : DomainStatus.AVAILABLE;
        return new DomainSnapshot(
            domain.getId(),
            status,
            domain.getName(),
            domain.getCode(),
            domain.getOwner(),
            domain.getDescription()
        );
    }
}
