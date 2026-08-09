package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelWorkbenchCatalogRepository;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogPage;
import com.yuzhi.dts.platform.service.modeling.ModelWorkbenchCatalogContract.CatalogQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelWorkbenchCatalogQueryServiceTest {

    @Mock
    private ModelWorkbenchCatalogRepository repository;

    @Mock
    private ModelSpecDomainReadAccessPort domainReadAccess;

    private ModelWorkbenchCatalogQueryService service;

    @BeforeEach
    void setUp() {
        service = new ModelWorkbenchCatalogQueryService(repository, domainReadAccess);
    }

    @Test
    void normalizesFiltersAndDelegatesOneVisibleDomainWindow() {
        UUID planId = UUID.randomUUID();
        UUID domainId = UUID.randomUUID();
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(domainId));
        CatalogPage expected = new CatalogPage(List.of(), 0, 1, 10, 0);
        when(repository.page(eq("default"), eq(Set.of(domainId)), org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        CatalogPage result = service.query(
            "default",
            new CatalogQuery(1, 10, "  Date  ", planId, domainId, "dimension", Layer.DWD, "draft")
        );

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<CatalogQuery> query = ArgumentCaptor.forClass(CatalogQuery.class);
        verify(repository).page(eq("default"), eq(Set.of(domainId)), query.capture());
        assertThat(query.getValue()).isEqualTo(
            new CatalogQuery(1, 10, "Date", planId, domainId, "DIMENSION", Layer.DWD, "DRAFT")
        );
    }

    @Test
    void rejectsAnUnboundedPageBeforeQueryingTheRepository() {
        assertThatThrownBy(() -> service.query("default", new CatalogQuery(0, 101, null, null, null, null, null, null)))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_WORKBENCH_PAGE_WINDOW_INVALID");

        verify(repository, never()).page(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsAnEmptyPageWhenTheRequestedDomainIsNotVisible() {
        UUID requestedDomain = UUID.randomUUID();
        when(domainReadAccess.visibleDomainIds()).thenReturn(Set.of(UUID.randomUUID()));

        CatalogPage result = service.query(
            "default",
            new CatalogQuery(2, 10, null, null, requestedDomain, null, null, null)
        );

        assertThat(result).isEqualTo(new CatalogPage(List.of(), 0, 2, 10, 0));
        verify(repository, never()).page(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
