package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogDomainAccessReadPort;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ModelSpecDomainReadAccessAdapter implements ModelSpecDomainReadAccessPort {

    private final CatalogDomainAccessReadPort domains;

    public ModelSpecDomainReadAccessAdapter(CatalogDomainAccessReadPort domains) {
        this.domains = domains;
    }

    @Override
    public boolean canRead(UUID domainId) {
        return domains.canRead(domainId);
    }

    @Override
    public Set<UUID> visibleDomainIds() {
        return Set.copyOf(domains.visibleDomainIds());
    }
}
