package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDomainResourceAuditTest {

    private final CatalogDomainRepository domainRepository = mock(CatalogDomainRepository.class);
    private final CatalogDatasetRepository datasetRepository = mock(CatalogDatasetRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CatalogDomainResource resource = new CatalogDomainResource(domainRepository, datasetRepository, auditService);

    @Test
    void createUpdateDeleteAndMoveUseDomainActionCodes() {
        UUID id = UUID.randomUUID();
        CatalogDomain domain = domain(id, "合同域", "contract");
        when(domainRepository.save(any(CatalogDomain.class))).thenReturn(domain);
        when(domainRepository.findById(id)).thenReturn(Optional.of(domain));

        resource.createDomain(domain(null, "合同域", "contract"));
        verify(auditService).auditAction(eq("CATALOG_DOMAIN_CREATE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        resource.updateDomain(id, domain(null, "合同域2", "contract2"));
        verify(auditService).auditAction(eq("CATALOG_DOMAIN_UPDATE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        resource.deleteDomain(id);
        verify(auditService).auditAction(eq("CATALOG_DOMAIN_DELETE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        resource.moveDomain(id, java.util.Map.of("newParentId", ""));
        verify(auditService).auditAction(eq("CATALOG_DOMAIN_MOVE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));
    }

    private CatalogDomain domain(UUID id, String name, String code) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        domain.setName(name);
        domain.setCode(code);
        return domain;
    }
}
