package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CatalogDomainCommandServiceTest {

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

    @BeforeEach
    void setUp() {
        when(domainRepository.save(any(CatalogDomain.class))).thenAnswer(invocation -> {
            CatalogDomain domain = invocation.getArgument(0);
            if (domain.getId() == null) {
                domain.setId(UUID.randomUUID());
            }
            return domain;
        });
    }

    @Test
    void createOwnsDefaultsPersistenceAndStrictAudit() {
        CatalogDomain request = new CatalogDomain();
        request.setName("研发项目");
        request.setCode("rd_project");

        CatalogDomain saved = service.create(request);

        assertThat(saved.getLifecycleStatus()).isEqualTo(CatalogDomainLifecycleStatus.ACTIVE);
        assertThat(saved.getAccessPolicy()).isEqualTo(CatalogDomainAccessPolicy.PUBLIC);
        verify(writeGuard).requireWriteAccess();
        verify(domainRepository).save(request);
        verify(auditService).auditActionStrict(
            eq("CATALOG_DOMAIN_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(saved.getId().toString()),
            eq(null)
        );
    }

    @Test
    void updateDeleteAndMoveUseOneGuardAndStrictDomainActions() {
        UUID id = UUID.randomUUID();
        CatalogDomain existing = domain(id, CatalogDomainAccessPolicy.PUBLIC);
        CatalogDomain patch = domain(null, CatalogDomainAccessPolicy.PUBLIC);
        when(domainRepository.findById(id)).thenReturn(Optional.of(existing));

        service.update(id, patch);
        service.move(id, null);
        service.delete(id);

        verify(writeGuard, times(3)).requireWriteAccess();
        verify(auditService).auditActionStrict("CATALOG_DOMAIN_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        verify(auditService).auditActionStrict("CATALOG_DOMAIN_MOVE", AuditStage.SUCCESS, id.toString(), null);
        verify(auditService).auditActionStrict("CATALOG_DOMAIN_DELETE", AuditStage.SUCCESS, id.toString(), null);
    }

    @Test
    void guardDenialStopsPersistenceBeforeAnyMutation() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
            .when(writeGuard)
            .requireWriteAccess();

        assertThatThrownBy(() -> service.create(domain(null, CatalogDomainAccessPolicy.PUBLIC)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(domainRepository, never()).save(any());
        verify(auditService, never()).auditActionStrict(any(), any(), any(), any());
    }

    @Test
    void restrictedDomainStillRequiresExistingObjectPermissionEvidence() {
        CatalogDomain restricted = domain(null, CatalogDomainAccessPolicy.RESTRICTED);
        when(visibilityService.canMaintain(any(CatalogDomain.class))).thenReturn(false);

        assertThatThrownBy(() -> service.create(restricted))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");

        verify(auditService, never()).auditActionStrict(any(), any(), any(), any());
    }

    private static CatalogDomain domain(UUID id, CatalogDomainAccessPolicy accessPolicy) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        domain.setName("研发项目");
        domain.setCode("rd_project");
        domain.setLifecycleStatus(CatalogDomainLifecycleStatus.ACTIVE);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
