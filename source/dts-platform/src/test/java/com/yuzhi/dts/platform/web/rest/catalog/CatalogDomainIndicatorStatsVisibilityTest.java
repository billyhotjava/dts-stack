package com.yuzhi.dts.platform.web.rest.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogDomainIndicatorStatsVisibilityTest {

    @Mock
    private CatalogDomainRepository domainRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @Mock
    private GovIndicatorDefinitionRepository indicatorRepository;

    @InjectMocks
    private CatalogDomainIndicatorStatsResource resource;

    @Test
    void hiddenDomainCannotBeUsedToDiscoverIndicatorCounts() {
        UUID id = UUID.randomUUID();
        CatalogDomain hidden = new CatalogDomain();
        hidden.setId(id);
        hidden.setCode("secret-domain-code");
        lenient().when(domainRepository.findById(id)).thenReturn(Optional.of(hidden));
        lenient().when(visibilityService.findVisibleById(id)).thenReturn(Optional.empty());

        Map<String, Object> stats = resource.getIndicatorStats(id).getData();

        assertThat(stats).containsEntry("total", 0L).containsEntry("published", 0L).containsEntry("draft", 0L);
        verify(indicatorRepository, never()).countByDomainIgnoreCase("secret-domain-code");
        verify(indicatorRepository, never()).countByDomainIgnoreCaseAndStatusUpper("secret-domain-code", "PUBLISHED");
        verify(indicatorRepository, never()).countByDomainIgnoreCaseAndStatusUpper("secret-domain-code", "DRAFT");
    }

    @Test
    void visibleDomainKeepsTheExistingIndicatorStatsShape() {
        UUID id = UUID.randomUUID();
        CatalogDomain visible = new CatalogDomain();
        visible.setId(id);
        visible.setCode("public-domain");
        lenient().when(domainRepository.findById(id)).thenReturn(Optional.of(visible));
        lenient().when(visibilityService.findVisibleById(id)).thenReturn(Optional.of(visible));
        when(indicatorRepository.countByDomainIgnoreCase("public-domain")).thenReturn(5L);
        when(indicatorRepository.countByDomainIgnoreCaseAndStatusUpper("public-domain", "PUBLISHED"))
            .thenReturn(3L);
        when(indicatorRepository.countByDomainIgnoreCaseAndStatusUpper("public-domain", "DRAFT"))
            .thenReturn(2L);

        assertThat(resource.getIndicatorStats(id).getData())
            .containsEntry("total", 5L)
            .containsEntry("published", 3L)
            .containsEntry("draft", 2L);
    }
}
