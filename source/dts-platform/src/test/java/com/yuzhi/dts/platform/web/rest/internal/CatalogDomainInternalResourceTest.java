package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainDictionaryReadPort;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainDictionaryReadPort.DomainRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogDomainInternalResourceTest {

    @Test
    void resolvesCatalogDomainsAndReportsMissingRefs() {
        CatalogDomainDictionaryReadPort domains = mock(CatalogDomainDictionaryReadPort.class);
        DomainRecord domain = domain("flower_rental", "花卉租赁");
        when(domains.findByCodeCandidates(anyCollection())).thenReturn(List.of(domain));

        CatalogDomainInternalResource resource = new CatalogDomainInternalResource(domains);
        CatalogDomainInternalResource.ResolveResponse response = resource
            .resolve(new CatalogDomainInternalResource.ResolveRequest(List.of("domain:flower_rental", "domain:missing")))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.domains()).extracting(CatalogDomainInternalResource.DomainContract::ref)
            .containsExactly("domain:flower_rental");
        assertThat(response.missing()).containsExactly("domain:missing");
        assertThat(response.ambiguous()).isEmpty();

        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(domains).findByCodeCandidates(captor.capture());
        assertThat(captor.getValue()).contains("domain:flower_rental", "flower_rental", "domain:missing", "missing");
    }

    @Test
    void reportsAmbiguousDomainAliasRefs() {
        CatalogDomainDictionaryReadPort domains = mock(CatalogDomainDictionaryReadPort.class);
        DomainRecord qualified = domain("domain.flower_rental", "花卉租赁");
        DomainRecord shortCode = domain("flower_rental", "花卉租赁短码");
        when(domains.findByCodeCandidates(anyCollection())).thenReturn(List.of(qualified, shortCode));

        CatalogDomainInternalResource resource = new CatalogDomainInternalResource(domains);
        CatalogDomainInternalResource.ResolveResponse response = resource
            .resolve(new CatalogDomainInternalResource.ResolveRequest(List.of("domain:flower_rental")))
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.domains()).isEmpty();
        assertThat(response.missing()).isEmpty();
        assertThat(response.ambiguous()).containsExactly("domain:flower_rental");
    }

    private static DomainRecord domain(String code, String name) {
        return new DomainRecord(
            UUID.randomUUID(),
            code,
            name,
            "经营管理部",
            null,
            CatalogDomainLifecycleStatus.ACTIVE,
            Instant.parse("2026-08-09T10:00:00Z")
        );
    }
}
