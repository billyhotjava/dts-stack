package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.RESTRICTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernanceIndicatorDependencyVisibilityTest {

    @Mock
    private AdminDirectoryGateway adminDirectoryGateway;

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @InjectMocks
    private GovernanceIndicatorDependencyResource resource;

    @Test
    void dependenciesUseVisibleDomainsAndRedactAnInvisibleParentId() {
        CatalogDomain hiddenParent = domain("secret-parent", RESTRICTED);
        CatalogDomain visibleChild = domain("visible-child", PUBLIC);
        visibleChild.setParent(hiddenParent);
        lenient().when(domainRepository.findAll()).thenReturn(List.of(hiddenParent, visibleChild));
        lenient().when(visibilityService.findAllVisible()).thenReturn(List.of(visibleChild));
        when(adminDirectoryGateway.fetchOrgTree()).thenReturn(List.of());

        Map<String, Object> payload = resource.dependencies(null, false).getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) payload.get("domainTree");
        assertThat(tree).singleElement().satisfies(node -> {
            assertThat(node).containsEntry("id", visibleChild.getId());
            assertThat(node).containsEntry("name", "visible-child");
            assertThat(node).containsEntry("parentId", null);
        });
        assertThat(tree.toString()).doesNotContain("secret-parent", hiddenParent.getId().toString());
    }

    private static CatalogDomain domain(
        String name,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setName(name);
        domain.setCode(name);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
