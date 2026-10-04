package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogDomainAccessReadPort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ModelSpecDomainWriteAccessAdapter implements ModelSpecDomainWriteAccessPort {

    private final CatalogDomainAccessReadPort domains;

    public ModelSpecDomainWriteAccessAdapter(CatalogDomainAccessReadPort domains) {
        this.domains = domains;
    }

    @Override
    public boolean canMaintain(UUID domainId) {
        // Referencing a visible data domain does not require editing the catalog domain itself.
        return domains.canRead(domainId);
    }
}
