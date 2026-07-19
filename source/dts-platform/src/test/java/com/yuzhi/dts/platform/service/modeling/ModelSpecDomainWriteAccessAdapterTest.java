package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecDomainWriteAccessAdapterTest {

    @Mock
    private CatalogDomainRepository repository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @Test
    void delegatesMaintenanceChecksOnlyForPersistedDomains() {
        UUID id = UUID.randomUUID();
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(domain));
        when(visibilityService.canMaintain(domain)).thenReturn(true);

        ModelSpecDomainWriteAccessAdapter adapter = new ModelSpecDomainWriteAccessAdapter(repository, visibilityService);

        assertThat(adapter.canMaintain(id)).isTrue();
        assertThat(adapter.canMaintain(UUID.randomUUID())).isFalse();
    }

    @Test
    void delegatesReadChecksOnlyForPersistedDomains() {
        UUID id = UUID.randomUUID();
        CatalogDomain domain = new CatalogDomain();
        domain.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(domain));
        when(visibilityService.canRead(domain)).thenReturn(true);

        ModelSpecDomainReadAccessAdapter adapter = new ModelSpecDomainReadAccessAdapter(repository, visibilityService);

        assertThat(adapter.canRead(id)).isTrue();
        assertThat(adapter.canRead(UUID.randomUUID())).isFalse();
    }
}
