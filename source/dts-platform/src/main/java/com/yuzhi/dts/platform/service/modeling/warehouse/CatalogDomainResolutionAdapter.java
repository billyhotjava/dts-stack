package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.MISSING;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogDomainResolutionAdapter implements CatalogDomainResolutionPort {

    private final CatalogDomainRepository domainRepository;
    private final CatalogDomainVisibilityService visibilityService;

    public CatalogDomainResolutionAdapter(
        CatalogDomainRepository domainRepository,
        CatalogDomainVisibilityService visibilityService
    ) {
        this.domainRepository = domainRepository;
        this.visibilityService = visibilityService;
    }

    @Override
    public DomainResolution resolve(UUID domainId) {
        return domainRepository.findById(domainId).map(this::resolveExisting).orElseGet(() -> redacted(domainId, MISSING));
    }

    private DomainResolution resolveExisting(CatalogDomain domain) {
        if (!visibilityService.canRead(domain)) {
            return redacted(domain.getId(), FORBIDDEN);
        }
        ResolutionStatus status = domain.getLifecycleStatus() == CatalogDomainLifecycleStatus.ARCHIVED ? ARCHIVED : AVAILABLE;
        return new DomainResolution(
            domain.getId(),
            status,
            domain.getName(),
            domain.getCode(),
            domain.getOwner(),
            domain.getDescription()
        );
    }

    private static DomainResolution redacted(UUID domainId, ResolutionStatus status) {
        return new DomainResolution(domainId, status, null, null, null, null);
    }
}
