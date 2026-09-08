package com.yuzhi.dts.platform.service.modeling.warehouse;

import java.util.UUID;

public interface CatalogDomainResolutionPort {

    DomainResolution resolve(UUID domainId);

    enum ResolutionStatus {
        AVAILABLE,
        MISSING,
        ARCHIVED,
        FORBIDDEN,
    }

    record DomainResolution(
        UUID domainId,
        ResolutionStatus status,
        String name,
        String code,
        String owner,
        String description,
        boolean businessCategory,
        UUID parentId
    ) {
        public DomainResolution(UUID domainId, ResolutionStatus status, String name, String code, String owner, String description) {
            this(domainId, status, name, code, owner, description, false, null);
        }
    }
}
