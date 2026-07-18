package com.yuzhi.dts.platform.service.catalog;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainActorProvider.CatalogDomainActor;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.AccessibleAssetsResult;
import com.yuzhi.dts.platform.service.permission.AssetPermissionService.PermissionResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CatalogDomainVisibilityServiceTest {

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private AssetPermissionService permissionService;

    private final CatalogDomainActor actor = new CatalogDomainActor("alice", List.of("ROLE_EMPLOYEE"), "D01");
    private CatalogDomainVisibilityService service;

    @BeforeEach
    void setUp() {
        service = new CatalogDomainVisibilityService(domainRepository, permissionService, () -> actor);
    }

    @Test
    void publicDomainIsVisibleWithoutObjectAuthorization() {
        CatalogDomain domain = domain(PUBLIC);

        assertThat(service.canRead(domain)).isTrue();
        verify(permissionService, never()).check(any(), anyList(), any(), any(), any());
    }

    @Test
    void restrictedDomainUsesTheAuthenticatedActorAndRealPermissionFacts() {
        CatalogDomain domain = domain(RESTRICTED);
        when(permissionService.check("alice", actor.roles(), "D01", "CATALOG_DOMAIN", domain.getId().toString()))
            .thenReturn(PermissionResult.denied(), PermissionResult.allowed("READ", "explicit_grant"));

        assertThat(service.canRead(domain)).isFalse();
        assertThat(service.canRead(domain)).isTrue();
    }

    @Test
    void transientRestrictedDomainWithoutAnIdFailsClosed() {
        CatalogDomain domain = new CatalogDomain();
        domain.setName("未持久化受限分类");
        domain.setAccessPolicy(RESTRICTED);

        assertThat(service.canRead(domain)).isFalse();
        verify(permissionService, never()).check(any(), anyList(), any(), any(), any());
    }

    @Test
    void restrictedMaintenanceRequiresEditOrManageRatherThanRead() {
        CatalogDomain domain = domain(RESTRICTED);
        when(permissionService.check("alice", actor.roles(), "D01", "CATALOG_DOMAIN", domain.getId().toString()))
            .thenReturn(
                PermissionResult.allowed("READ", "explicit_grant"),
                PermissionResult.allowed("EDIT", "explicit_grant"),
                PermissionResult.allowed("MANAGE", "dept_ownership")
            );

        assertThat(service.canMaintain(domain)).isFalse();
        assertThat(service.canMaintain(domain)).isTrue();
        assertThat(service.canMaintain(domain)).isTrue();
    }

    @Test
    void visiblePageIsFilteredInTheRepositorySoItsTotalCannotCountHiddenDomains() {
        PageRequest pageable = PageRequest.of(0, 10);
        CatalogDomain publicDomain = domain(PUBLIC);
        when(permissionService.listAccessibleAssetIds(eq("alice"), eq(actor.roles()), eq("D01"), eq("CATALOG_DOMAIN"), any()))
            .thenReturn(new AccessibleAssetsResult(List.of(), 0, "FILTERED"));
        when(domainRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(publicDomain), pageable, 1));

        var result = service.findVisiblePage(" project ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(publicDomain);
        verify(domainRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void instituteWideAccessKeepsRestrictedDomainsVisible() {
        PageRequest pageable = PageRequest.of(0, 10);
        CatalogDomain restricted = domain(RESTRICTED);
        when(permissionService.listAccessibleAssetIds(eq("alice"), eq(actor.roles()), eq("D01"), eq("CATALOG_DOMAIN"), any()))
            .thenReturn(AccessibleAssetsResult.all());
        when(domainRepository.findAll(any(Specification.class), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(restricted), pageable, 1));

        var result = service.findVisiblePage(null, pageable);

        assertThat(result.getContent()).containsExactly(restricted);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void visibleCountUsesTheSameAuthorizationScopeAsVisibleLists() throws Exception {
        when(permissionService.listAccessibleAssetIds(eq("alice"), eq(actor.roles()), eq("D01"), eq("CATALOG_DOMAIN"), any()))
            .thenReturn(new AccessibleAssetsResult(List.of(), 0, "FILTERED"));
        when(domainRepository.count(any(Specification.class))).thenReturn(2L);

        long result = service.countVisible();

        assertThat(result).isEqualTo(2L);
        verify(domainRepository).count(any(Specification.class));
    }

    @Test
    void codeResolutionSeparatesRegisteredHiddenDomainsFromUnregisteredLabels() {
        CatalogDomain visible = domain(PUBLIC);
        visible.setCode("PUBLIC");
        visible.setName("公开分类");
        CatalogDomain hidden = domain(RESTRICTED);
        hidden.setCode("Secret");
        hidden.setName("受限分类机密名");
        when(domainRepository.findByCodeLowerIn(Set.of("public", "secret", "legacy-free-label")))
            .thenReturn(List.of(visible, hidden));
        when(permissionService.listAccessibleAssetIds(eq("alice"), eq(actor.roles()), eq("D01"), eq("CATALOG_DOMAIN"), any()))
            .thenReturn(new AccessibleAssetsResult(List.of(), 0, "FILTERED"));
        when(domainRepository.findAll(any(Specification.class))).thenReturn(List.of(visible));

        var resolution = service.resolveCodes(Set.of(" PUBLIC ", "SECRET", "legacy-free-label"));

        assertThat(resolution.visibleNames()).isEqualTo(Map.of("public", "公开分类"));
        assertThat(resolution.hiddenCodes()).containsExactly("secret");
    }

    private static CatalogDomain domain(com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setName("项目管理");
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
