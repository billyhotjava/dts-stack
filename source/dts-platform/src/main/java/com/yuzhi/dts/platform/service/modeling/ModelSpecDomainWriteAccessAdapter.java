package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ModelSpecDomainWriteAccessAdapter implements ModelSpecDomainWriteAccessPort {

    private final CatalogDomainRepository domainRepository;
    private final CatalogDomainVisibilityService visibilityService;

    public ModelSpecDomainWriteAccessAdapter(
        CatalogDomainRepository domainRepository,
        CatalogDomainVisibilityService visibilityService
    ) {
        this.domainRepository = domainRepository;
        this.visibilityService = visibilityService;
    }

    @Override
    public boolean canMaintain(UUID domainId) {
        return domainId != null && domainRepository.findById(domainId).filter(visibilityService::canMaintain).isPresent();
    }
}
