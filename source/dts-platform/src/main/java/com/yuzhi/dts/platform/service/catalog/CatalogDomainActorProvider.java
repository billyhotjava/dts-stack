package com.yuzhi.dts.platform.service.catalog;

import java.util.List;

@FunctionalInterface
public interface CatalogDomainActorProvider {

    CatalogDomainActor currentActor();

    record CatalogDomainActor(String username, List<String> roles, String departmentCode) {
        public CatalogDomainActor {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }
}
