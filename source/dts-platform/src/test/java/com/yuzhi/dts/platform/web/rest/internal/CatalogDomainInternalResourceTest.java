package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogDomainInternalResourceTest {

    @Test
    void resolvesCatalogDomainsAndReportsMissingRefs() {
        CatalogDomainRepository repository = mock(CatalogDomainRepository.class);
        CatalogDomain domain = domain("flower_rental", "花卉租赁");
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(domain));

        CatalogDomainInternalResource resource = new CatalogDomainInternalResource(repository);
        CatalogDomainInternalResource.ResolveResponse response = resource
            .resolve(new CatalogDomainInternalResource.ResolveRequest(List.of("domain:flower_rental", "domain:missing")))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.domains()).extracting(CatalogDomainInternalResource.DomainContract::ref)
            .containsExactly("domain:flower_rental");
        assertThat(response.missing()).containsExactly("domain:missing");
        assertThat(response.ambiguous()).isEmpty();

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findByCodeLowerIn(captor.capture());
        assertThat(captor.getValue()).contains("domain:flower_rental", "flower_rental", "domain:missing", "missing");
    }

    @Test
    void reportsAmbiguousDomainAliasRefs() {
        CatalogDomainRepository repository = mock(CatalogDomainRepository.class);
        CatalogDomain qualified = domain("domain.flower_rental", "花卉租赁");
        CatalogDomain shortCode = domain("flower_rental", "花卉租赁短码");
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(qualified, shortCode));

        CatalogDomainInternalResource resource = new CatalogDomainInternalResource(repository);
        CatalogDomainInternalResource.ResolveResponse response = resource
            .resolve(new CatalogDomainInternalResource.ResolveRequest(List.of("domain:flower_rental")))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.domains()).isEmpty();
        assertThat(response.missing()).isEmpty();
        assertThat(response.ambiguous()).containsExactly("domain:flower_rental");
    }

    private static CatalogDomain domain(String code, String name) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setCode(code);
        domain.setName(name);
        domain.setOwner("经营管理部");
        return domain;
    }
}
