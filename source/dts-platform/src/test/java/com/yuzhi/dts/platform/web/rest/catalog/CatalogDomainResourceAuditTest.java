package com.yuzhi.dts.platform.web.rest.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainCommandService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDomainResourceAuditTest {

    private final CatalogDomainRepository domainRepository = mock(CatalogDomainRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CatalogDomainVisibilityService visibilityService = mock(CatalogDomainVisibilityService.class);
    private final ArchitectureDictionaryWriteGuard writeGuard = mock(ArchitectureDictionaryWriteGuard.class);
    private final CatalogDomainCommandService service = new CatalogDomainCommandService(
        domainRepository,
        auditService,
        visibilityService,
        writeGuard
    );

    @Test
    void createUpdateDeleteAndMoveUseDomainActionCodes() {
        UUID id = UUID.randomUUID();
        CatalogDomain domain = domain(id, "合同域", "contract");
        when(domainRepository.save(any(CatalogDomain.class))).thenReturn(domain);
        when(domainRepository.findById(id)).thenReturn(Optional.of(domain));

        service.create(domain(null, "合同域", "contract"));
        verify(auditService).auditActionStrict(eq("CATALOG_DOMAIN_CREATE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        service.update(id, domain(null, "合同域2", "contract2"));
        verify(auditService).auditActionStrict(eq("CATALOG_DOMAIN_UPDATE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        service.delete(id);
        verify(auditService).auditActionStrict(eq("CATALOG_DOMAIN_DELETE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));

        service.move(id, null);
        verify(auditService).auditActionStrict(eq("CATALOG_DOMAIN_MOVE"), eq(AuditStage.SUCCESS), eq(id.toString()), eq(null));
    }

    private CatalogDomain domain(UUID id, String name, String code) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        domain.setName(name);
        domain.setCode(code);
        return domain;
    }
}
