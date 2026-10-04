package com.yuzhi.dts.platform.service.workbench;

import static com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogDomainVisibilityService.DomainCodeVisibility;
import com.yuzhi.dts.platform.service.permission.DashboardAccessGuard;
import com.yuzhi.dts.platform.service.permission.DashboardCallerResolver;
import com.yuzhi.dts.platform.service.workbench.dto.DomainAggregateRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchCatalogDomainVisibilityTest {

    @Mock
    private WorkbenchRoleResolver roleResolver;

    @Mock
    private BiReportLinkRepository reportRepository;

    @Mock
    private BiReportVisitRepository visitRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private CatalogDomainVisibilityService visibilityService;

    @Mock
    private WorkbenchLeaderOverviewProperties properties;

    @Mock
    private TopReportsFallbackService fallbackService;

    @Mock
    private DashboardAccessGuard dashboardAccessGuard;

    @Mock
    private DashboardCallerResolver dashboardCallerResolver;

    @InjectMocks
    private WorkbenchLeaderOverviewService service;

    @Test
    void domainMatrixSuppressesRegisteredHiddenDomainsButKeepsUnregisteredBusinessLabels() {
        CatalogDomain visible = domain("public", "公开分类", PUBLIC);
        when(visibilityService.resolveCodes(Set.of("public", "secret", "legacy-free-label")))
            .thenReturn(new DomainCodeVisibility(Map.of("public", visible.getName()), Set.of("secret")));
        when(visitRepository.aggregateByBizDomain(any(), any(), any(), any()))
            .thenReturn(
                List.of(
                    new DomainAggregateRow("public", 10L),
                    new DomainAggregateRow("secret", 8L),
                    new DomainAggregateRow("legacy-free-label", 6L)
                )
            );
        when(properties.getDomainMatrixTop()).thenReturn(6);
        Instant now = Instant.now();

        var matrix = service.computeDomainMatrix(
            "ALL",
            null,
            null,
            new WorkbenchLeaderOverviewService.TimeWindow(now.minusSeconds(3600), now, now.minusSeconds(7200), now.minusSeconds(3600))
        );

        assertThat(matrix)
            .extracting(cell -> cell.domainName())
            .containsExactly("公开分类", "legacy-free-label");
        assertThat(matrix).extracting(cell -> cell.visits()).containsExactly(10L, 6L);
        assertThat(matrix.toString()).doesNotContain("secret");
        assertThat(matrix.toString()).doesNotContain("受限分类机密名");
    }

    @Test
    void domainMatrixExcludesHiddenVisitsBeforeTopAndOtherAggregation() {
        when(visibilityService.resolveCodes(Set.of("public", "secret", "free-a", "free-b")))
            .thenReturn(new DomainCodeVisibility(Map.of("public", "公开分类"), Set.of("secret")));
        when(visitRepository.aggregateByBizDomain(any(), any(), any(), any()))
            .thenReturn(
                List.of(
                    new DomainAggregateRow("secret", 100L),
                    new DomainAggregateRow("public", 90L),
                    new DomainAggregateRow("free-a", 80L),
                    new DomainAggregateRow("free-b", 70L)
                )
            );
        when(properties.getDomainMatrixTop()).thenReturn(2);
        Instant now = Instant.now();

        var matrix = service.computeDomainMatrix(
            "ALL",
            null,
            null,
            new WorkbenchLeaderOverviewService.TimeWindow(now.minusSeconds(3600), now, now.minusSeconds(7200), now.minusSeconds(3600))
        );

        assertThat(matrix).extracting(cell -> cell.domain()).containsExactly("public", "free-a", "__OTHER__");
        assertThat(matrix).extracting(cell -> cell.visits()).containsExactly(90L, 80L, 70L);
        assertThat(matrix.toString()).doesNotContain("secret", "100");
    }

    private static CatalogDomain domain(
        String code,
        String name,
        com.yuzhi.dts.platform.domain.catalog.CatalogDomainAccessPolicy accessPolicy
    ) {
        CatalogDomain domain = new CatalogDomain();
        domain.setId(UUID.randomUUID());
        domain.setCode(code);
        domain.setName(name);
        domain.setAccessPolicy(accessPolicy);
        return domain;
    }
}
