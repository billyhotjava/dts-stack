package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.catalog.CatalogDomainAccessReadPort;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSpecDomainWriteAccessAdapterTest {

    @Mock
    private CatalogDomainAccessReadPort domains;

    @Test
    void modelReferenceRequiresDomainVisibilityInsteadOfDomainMaintenance() {
        UUID id = UUID.randomUUID();
        when(domains.canRead(id)).thenReturn(true);

        ModelSpecDomainWriteAccessAdapter adapter = new ModelSpecDomainWriteAccessAdapter(domains);

        assertThat(adapter.canMaintain(id)).isTrue();
        assertThat(adapter.canMaintain(UUID.randomUUID())).isFalse();
    }

    @Test
    void delegatesReadChecksOnlyForPersistedDomains() {
        UUID id = UUID.randomUUID();
        when(domains.canRead(id)).thenReturn(true);

        ModelSpecDomainReadAccessAdapter adapter = new ModelSpecDomainReadAccessAdapter(domains);

        assertThat(adapter.canRead(id)).isTrue();
        assertThat(adapter.canRead(UUID.randomUUID())).isFalse();
    }

    @Test
    void suppliesOnlyPersistedVisibleDomainIdsForRepositoryFiltering() {
        UUID id = UUID.randomUUID();
        when(domains.visibleDomainIds()).thenReturn(Set.of(id));

        ModelSpecDomainReadAccessAdapter adapter = new ModelSpecDomainReadAccessAdapter(domains);

        assertThat(adapter.visibleDomainIds()).containsExactly(id);
    }
}
