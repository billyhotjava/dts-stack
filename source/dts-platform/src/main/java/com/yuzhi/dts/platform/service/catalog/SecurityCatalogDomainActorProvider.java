package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import org.springframework.stereotype.Component;

@Component
public class SecurityCatalogDomainActorProvider implements CatalogDomainActorProvider {

    @Override
    public CatalogDomainActor currentActor() {
        return new CatalogDomainActor(
            SecurityUtils.getCurrentUserLogin().orElse(null),
            SecurityUtils.getCurrentUserAuthorities(),
            SecurityUtils.getCurrentUserDept().orElse(null)
        );
    }
}
