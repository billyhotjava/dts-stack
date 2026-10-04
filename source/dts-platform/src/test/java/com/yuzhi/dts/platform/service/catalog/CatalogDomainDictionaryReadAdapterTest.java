package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogDomainDictionaryReadAdapterTest {

    @Test
    void returnsImmutableStableProjectionWithoutExposingJpaEntities() {
        CatalogDomainRepository repository = mock(CatalogDomainRepository.class);
        CatalogDomain parent = domain("FIN", "财务业务", null, Instant.parse("2026-08-09T08:00:00Z"));
        CatalogDomain child = domain(
            "FIN_BUDGET",
            "预算域",
            parent,
            Instant.parse("2026-08-09T09:00:00Z")
        );
        when(repository.findByCodeLowerIn(anyCollection())).thenReturn(List.of(child));
        CatalogDomainDictionaryReadAdapter adapter = new CatalogDomainDictionaryReadAdapter(repository);

        List<CatalogDomainDictionaryReadPort.DomainRecord> result = adapter.findByCodeCandidates(
            List.of("fin_budget")
        );

        assertThat(result).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(child.getId());
            assertThat(record.code()).isEqualTo("FIN_BUDGET");
            assertThat(record.parentId()).isEqualTo(parent.getId());
            assertThat(record.status()).isEqualTo(CatalogDomainLifecycleStatus.ACTIVE);
            assertThat(record.version()).isEqualTo(Instant.parse("2026-08-09T09:00:00Z"));
        });
    }

    @Test
    void codeExistenceUsesTheCatalogOwnedReadBoundary() {
        CatalogDomainRepository repository = mock(CatalogDomainRepository.class);
        when(repository.existsByCodeIgnoreCase("FIN")).thenReturn(true);

        assertThat(new CatalogDomainDictionaryReadAdapter(repository).existsByCode("FIN")).isTrue();
    }

    private static CatalogDomain domain(String code, String name, CatalogDomain parent, Instant version) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setCode(code);
        domain.setName(name);
        domain.setOwner("经营管理部");
        domain.setParent(parent);
        domain.setLifecycleStatus(CatalogDomainLifecycleStatus.ACTIVE);
        domain.setLastModifiedDate(version);
        return domain;
    }
}
