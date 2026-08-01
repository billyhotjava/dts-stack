package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ACTIVE;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus.ARCHIVED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.AVAILABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.FORBIDDEN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.ResolutionStatus.MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import com.yuzhi.dts.platform.service.catalog.JpaCatalogDomainAccessReadAdapter;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogDomainResolutionAdapterTest {

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    private CatalogDomainResolutionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CatalogDomainResolutionAdapter(
            new JpaCatalogDomainAccessReadAdapter(domainRepository, visibilityService)
        );
    }

    @Test
    void resolvesActivePublicDomainWithoutObjectAuthorization() {
        CatalogDomain domain = domain(ACTIVE, PUBLIC);
        when(domainRepository.findById(domain.getId())).thenReturn(Optional.of(domain));
        when(visibilityService.canRead(domain)).thenReturn(true);

        DomainResolution resolution = adapter.resolve(domain.getId());

        assertThat(resolution.status()).isEqualTo(AVAILABLE);
        assertThat(resolution.name()).isEqualTo("项目管理");
        assertThat(resolution.code()).isEqualTo("project_management");
        verify(visibilityService).canRead(domain);
    }

    @Test
    void resolvesMissingDomainWithoutInventingCatalogContent() {
        UUID domainId = UUID.randomUUID();
        when(domainRepository.findById(domainId)).thenReturn(Optional.empty());

        DomainResolution resolution = adapter.resolve(domainId);

        assertThat(resolution.status()).isEqualTo(MISSING);
        assertThat(resolution.name()).isNull();
        assertThat(resolution.code()).isNull();
    }

    @Test
    void resolvesPublicArchivedDomainFromLifecycleFact() {
        CatalogDomain domain = domain(ARCHIVED, PUBLIC);
        when(domainRepository.findById(domain.getId())).thenReturn(Optional.of(domain));
        when(visibilityService.canRead(domain)).thenReturn(true);

        DomainResolution resolution = adapter.resolve(domain.getId());

        assertThat(resolution.status()).isEqualTo(CatalogDomainResolutionPort.ResolutionStatus.ARCHIVED);
        assertThat(resolution.name()).isEqualTo("项目管理");
    }

    @Test
    void redactsRestrictedDomainWhenAuthorizationIsDenied() {
        CatalogDomain domain = domain(ACTIVE, RESTRICTED);
        when(domainRepository.findById(domain.getId())).thenReturn(Optional.of(domain));
        when(visibilityService.canRead(domain)).thenReturn(false);

        DomainResolution resolution = adapter.resolve(domain.getId());

        assertThat(resolution.status()).isEqualTo(FORBIDDEN);
        assertThat(resolution.domainId()).isEqualTo(domain.getId());
        assertThat(resolution.name()).isNull();
        assertThat(resolution.code()).isNull();
        assertThat(resolution.owner()).isNull();
        assertThat(resolution.description()).isNull();
    }

    @Test
    void resolvesRestrictedDomainAfterRealPermissionGrant() {
        CatalogDomain domain = domain(ACTIVE, RESTRICTED);
        when(domainRepository.findById(domain.getId())).thenReturn(Optional.of(domain));
        when(visibilityService.canRead(domain)).thenReturn(true);

        DomainResolution resolution = adapter.resolve(domain.getId());

        assertThat(resolution.status()).isEqualTo(AVAILABLE);
        assertThat(resolution.name()).isEqualTo("项目管理");
        assertThat(resolution.code()).isEqualTo("project_management");
    }

    private static CatalogDomain domain(
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus lifecycleStatus,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setName("项目管理");
        domain.setCode("project_management");
        domain.setOwner("owner-1");
        domain.setDescription("项目管理分类");
        domain.setLifecycleStatus(lifecycleStatus);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
