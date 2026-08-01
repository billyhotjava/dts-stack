package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.MISSING;

import com.yuzhi.dts.platform.service.catalog.CatalogDomainAccessReadPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogDomainResolutionAdapter implements CatalogDomainResolutionPort {

    private final CatalogDomainAccessReadPort domains;

    public CatalogDomainResolutionAdapter(CatalogDomainAccessReadPort domains) {
        this.domains = domains;
    }

    @Override
    public DomainResolution resolve(UUID domainId) {
        CatalogDomainAccessReadPort.DomainSnapshot snapshot = domains.resolve(domainId);
        ResolutionStatus status = switch (snapshot.status()) {
            case AVAILABLE -> AVAILABLE;
            case ARCHIVED -> ARCHIVED;
            case FORBIDDEN -> FORBIDDEN;
            case MISSING -> MISSING;
        };
        if (status == FORBIDDEN || status == MISSING) {
            return redacted(snapshot.id(), status);
        }
        return new DomainResolution(
            snapshot.id(),
            status,
            snapshot.name(),
            snapshot.code(),
            snapshot.owner(),
            snapshot.description()
        );
    }

    private static DomainResolution redacted(UUID domainId, ResolutionStatus status) {
        return new DomainResolution(domainId, status, null, null, null, null);
    }
}
