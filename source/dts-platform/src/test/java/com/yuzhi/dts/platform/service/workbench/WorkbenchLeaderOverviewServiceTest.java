package com.yuzhi.dts.platform.service.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.service.workbench.dto.DomainAggregateRow;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse;
import com.yuzhi.dts.platform.service.workbench.dto.ReportVisitAggregateRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchLeaderOverviewServiceTest {

    @Mock BiReportLinkRepository reportRepo;
    @Mock BiReportVisitRepository visitRepo;
    @Mock CatalogDatasetRepository datasetRepo;
    @Mock CatalogDomainRepository catalogDomainRepo;
    @Mock TopReportsFallbackService fallbackService;

    private WorkbenchLeaderOverviewService service;

    @BeforeEach
    void setUp() {
        service = new WorkbenchLeaderOverviewService(
            new WorkbenchRoleResolver(),
            reportRepo,
            visitRepo,
            datasetRepo,
            catalogDomainRepo,
            new WorkbenchLeaderOverviewProperties(),
            fallbackService
        );
        // Make stubs lenient so tests that only care about scope/deptCode
        // still work when downstream queries return defaults.
        lenient().when(reportRepo.countForAll(any(), any(), any())).thenReturn(0L);
        lenient().when(reportRepo.countForDept(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(reportRepo.countDistinctReportsVisitedByUser(any(), any(), any())).thenReturn(0L);
        lenient().when(visitRepo.countVisitsForAll(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(visitRepo.countVisitsForDept(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(visitRepo.countVisitsForUser(any(), any(), any())).thenReturn(0L);
        lenient().when(datasetRepo.countAssets(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(datasetRepo.countAssetsByClassifications(any(), any(), anyList())).thenReturn(0L);
        lenient().when(visitRepo.findTopRecentByUser(anyString(), any(), any())).thenReturn(List.of());
        lenient().when(visitRepo.aggregateTopReportsForDept(anyString(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(visitRepo.aggregateTopReportsAll(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(datasetRepo.findTopByClassification(any(), any(), any())).thenReturn(List.of());
        lenient().when(datasetRepo.findTopForUser(anyString(), any())).thenReturn(List.of());
        lenient().when(fallbackService.tryFetchFallback(any(), any())).thenReturn(List.of());
        lenient().when(visitRepo.aggregateByBizDomain(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(catalogDomainRepo.findAll()).thenReturn(List.of());
    }

    // =========================================================== scope/dept/timerange downgrade (existing wave-1 tests)

    @Test
    void build_emp_requesting_ALL_downgrades_to_MINE() {
        LeaderOverviewResponse res = service.build(
            "alice", List.of("ROLE_EMPLOYEE"), "D001", "ALL", "D999", null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("MINE");
        assertThat(res.effectiveDeptCode()).isNull();
    }

    @Test
    void build_dept_leader_requesting_ALL_downgrades_to_DEPT_with_own_dept() {
        LeaderOverviewResponse res = service.build(
            "bob", List.of("ROLE_DEPT_LEADER"), "D001", "ALL", "D999", null, "QUARTER"
        );
        assertThat(res.scope()).isEqualTo("DEPT");
        assertThat(res.effectiveDeptCode()).isEqualTo("D001");
    }

    @Test
    void build_emp_requesting_DEPT_downgrades_to_MINE() {
        LeaderOverviewResponse res = service.build(
            "carol", List.of("ROLE_EMPLOYEE"), "D001", "DEPT", null, null, "YEAR"
        );
        assertThat(res.scope()).isEqualTo("MINE");
        assertThat(res.effectiveDeptCode()).isNull();
    }

    @Test
    void build_inst_leader_requesting_ALL_with_deptCode_keeps_ALL_and_dept() {
        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", "D500", null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("ALL");
        assertThat(res.effectiveDeptCode()).isEqualTo("D500");
    }

    @Test
    void build_ALL_rejects_deptCode_with_wildcard_and_normalizes_to_null() {
        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", "%", null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("ALL");
        assertThat(res.effectiveDeptCode()).isNull();

        LeaderOverviewResponse res2 = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", "D5%0", null, "MONTH"
        );
        assertThat(res2.effectiveDeptCode()).isNull();
    }

    @Test
    void build_default_scope_follows_role() {
        LeaderOverviewResponse instLeader = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", null, null, null, null
        );
        assertThat(instLeader.scope()).isEqualTo("ALL");
        assertThat(instLeader.timeRange()).isEqualTo("MONTH");

        LeaderOverviewResponse deptLeader = service.build(
            "bob", List.of("ROLE_DEPT_LEADER"), "D001", null, null, null, null
        );
        assertThat(deptLeader.scope()).isEqualTo("DEPT");
        assertThat(deptLeader.effectiveDeptCode()).isEqualTo("D001");

        LeaderOverviewResponse emp = service.build(
            "alice", List.of("ROLE_EMPLOYEE"), "D001", null, null, null, null
        );
        assertThat(emp.scope()).isEqualTo("MINE");
        assertThat(emp.effectiveDeptCode()).isNull();
    }

    @Test
    void build_DEPT_ignores_requested_deptCode_and_uses_user_deptCode() {
        LeaderOverviewResponse res = service.build(
            "bob", List.of("ROLE_DEPT_LEADER"), "D001", "DEPT", "D999", null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("DEPT");
        assertThat(res.effectiveDeptCode()).isEqualTo("D001");
    }

    @Test
    void build_normalizes_lowercase_timeRange_and_unknown_defaults() {
        LeaderOverviewResponse quarter = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "quarter"
        );
        assertThat(quarter.timeRange()).isEqualTo("QUARTER");

        LeaderOverviewResponse unknown = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "garbage"
        );
        assertThat(unknown.timeRange()).isEqualTo("MONTH");
    }

    // =========================================================== T03: Kpi

    @Test
    void computeKpis_prev_period_zero_yields_null_mom() {
        when(visitRepo.countVisitsForAll(any(), any(), any(), any())).thenReturn(42L, 0L);

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.kpis().visitsInPeriod()).isEqualTo(42L);
        assertThat(res.kpis().visitsMoM()).isNull();
    }

    @Test
    void computeKpis_zero_assets_yields_null_ratio() {
        when(datasetRepo.countAssets(any(), any(), any(), any())).thenReturn(0L, 0L);
        when(datasetRepo.countAssetsByClassifications(any(), any(), anyList())).thenReturn(0L, 0L);

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.kpis().assetsTotal()).isZero();
        assertThat(res.kpis().assetsS1Ratio()).isNull();
    }

    @Test
    void computeKpis_computes_mom_correctly_when_prev_non_zero() {
        // period=50, prev=40 → (50-40)/40 = 0.25
        when(visitRepo.countVisitsForAll(any(), any(), any(), any())).thenReturn(50L, 40L);

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.kpis().visitsMoM()).isNotNull();
        assertThat(res.kpis().visitsMoM().doubleValue()).isEqualTo(0.25);
    }

    // P1-3 — DEPT scope KPI flow with concrete value asserts. Previously
    // tests verified only that aggregateTopReportsForDept was called; the
    // KPI numbers themselves were never asserted, so a regression that
    // mis-routed DEPT to ALL would have been invisible.
    @Test
    void computeKpis_DEPT_returns_dept_scoped_visit_counts_and_mom() {
        // countVisitsForDept is called twice (current period, previous period).
        when(visitRepo.countVisitsForDept(eq("D001"), any(), any(), any()))
            .thenReturn(120L, 60L);
        when(reportRepo.countForDept(eq("D001"), any(), any(), any()))
            .thenReturn(15L, 3L);

        LeaderOverviewResponse res = service.build(
            "bob", List.of("ROLE_DEPT_LEADER"), "D001", "DEPT", null, null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("DEPT");
        assertThat(res.effectiveDeptCode()).isEqualTo("D001");
        assertThat(res.kpis().visitsInPeriod()).isEqualTo(120L);
        // (120-60)/60 = 1.0 (100% growth)
        assertThat(res.kpis().visitsMoM()).isNotNull();
        assertThat(res.kpis().visitsMoM().doubleValue()).isEqualTo(1.0);
        assertThat(res.kpis().reportsTotal()).isEqualTo(15L);
        assertThat(res.kpis().reportsNewInPeriod()).isEqualTo(3L);
        // ALL/MINE-only repos must not be touched by the DEPT path.
        verify(visitRepo, never()).countVisitsForAll(any(), any(), any(), any());
        verify(visitRepo, never()).countVisitsForUser(any(), any(), any());
        verify(reportRepo, never()).countForAll(any(), any(), any());
    }

    // P1-3 — ALL+bizDomain drill-down asserts the bizDomain is forwarded.
    @Test
    void computeKpis_ALL_with_bizDomain_passes_filter_to_all_aggregates() {
        when(visitRepo.countVisitsForAll(any(), eq("finance"), any(), any())).thenReturn(7L, 0L);
        when(reportRepo.countForAll(eq("finance"), any(), any())).thenReturn(2L);

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, "finance", "MONTH"
        );
        assertThat(res.scope()).isEqualTo("ALL");
        assertThat(res.kpis().visitsInPeriod()).isEqualTo(7L);
        assertThat(res.kpis().reportsTotal()).isEqualTo(2L);
        verify(visitRepo).countVisitsForAll(any(), eq("finance"), any(), any());
    }

    // P0-5 — assetScopeFallback flag flips for MINE only.
    @Test
    void computeKpis_MINE_marks_asset_scope_fallback_true() {
        LeaderOverviewResponse mine = service.build(
            "alice", List.of("ROLE_EMPLOYEE"), "D001", null, null, null, "MONTH"
        );
        assertThat(mine.scope()).isEqualTo("MINE");
        assertThat(mine.kpis().assetScopeFallback()).isTrue();

        LeaderOverviewResponse all = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(all.kpis().assetScopeFallback()).isFalse();
    }

    // =========================================================== T04: TOP reports

    @Test
    void computeTopReports_MINE_uses_findTopRecentByUser() {
        UUID rid = UUID.randomUUID();
        when(visitRepo.findTopRecentByUser(eq("alice"), any(), any()))
            .thenReturn(List.of(new ReportVisitAggregateRow(
                rid, "Monthly Report", 5L, "finance", "TOP_SECRET", Instant.parse("2026-04-01T00:00:00Z"),
                "/bi/screens/42/preview", "DTS_BI"
            )));

        LeaderOverviewResponse res = service.build(
            "alice", List.of("ROLE_EMPLOYEE"), "D001", null, null, null, "MONTH"
        );
        assertThat(res.scope()).isEqualTo("MINE");
        assertThat(res.topReports()).hasSize(1);
        assertThat(res.topReports().get(0).id()).isEqualTo(rid.toString());
        assertThat(res.topReports().get(0).classification()).isEqualTo("S1");  // mapped
        verify(visitRepo).findTopRecentByUser(eq("alice"), any(), any());
        verify(visitRepo, never()).aggregateTopReportsForDept(any(), any(), any(), any(), any());
        verify(visitRepo, never()).aggregateTopReportsAll(any(), any(), any(), any());
    }

    @Test
    void computeTopReports_DEPT_uses_aggregateForDept() {
        service.build("bob", List.of("ROLE_DEPT_LEADER"), "D001", "DEPT", null, null, "MONTH");
        verify(visitRepo).aggregateTopReportsForDept(eq("D001"), any(), any(), any(), any());
        verify(visitRepo, never()).aggregateTopReportsAll(any(), any(), any(), any());
    }

    @Test
    void computeTopReports_ALL_without_dept_uses_aggregateAll() {
        service.build("dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH");
        verify(visitRepo).aggregateTopReportsAll(any(), any(), any(), any());
        verify(visitRepo, never()).aggregateTopReportsForDept(any(), any(), any(), any(), any());
    }

    @Test
    void computeTopReports_ALL_with_dept_drilldown_uses_aggregateForDept() {
        service.build("dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", "D500", null, "MONTH");
        verify(visitRepo).aggregateTopReportsForDept(eq("D500"), any(), any(), any(), any());
        verify(visitRepo, never()).aggregateTopReportsAll(any(), any(), any(), any());
    }

    // =========================================================== T05: TOP assets

    @Test
    void computeTopAssets_maps_classification_to_api_codes() {
        CatalogDataset d = new CatalogDataset();
        d.setId(UUID.randomUUID());
        d.setName("Sensitive Dataset");
        d.setClassification("TOP_SECRET");
        CatalogDomain dom = new CatalogDomain();
        dom.setCode("finance");
        dom.setName("Finance");
        d.setDomain(dom);
        when(datasetRepo.findTopByClassification(any(), any(), any())).thenReturn(List.of(d));

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.topAssets()).hasSize(1);
        assertThat(res.topAssets().get(0).classification()).isEqualTo("S1");
        assertThat(res.topAssets().get(0).bizDomain()).isEqualTo("finance");
    }

    @Test
    void computeTopAssets_MINE_falls_back_to_findTopForUser() {
        service.build("alice", List.of("ROLE_EMPLOYEE"), "D001", null, null, null, "MONTH");
        verify(datasetRepo).findTopForUser(eq("alice"), any());
        verify(datasetRepo, never()).findTopByClassification(any(), any(), any());
    }

    // =========================================================== T06: Domain matrix

    @Test
    void computeDomainMatrix_empty_when_scope_DEPT() {
        LeaderOverviewResponse res = service.build(
            "bob", List.of("ROLE_DEPT_LEADER"), "D001", "DEPT", null, null, "MONTH"
        );
        assertThat(res.domainMatrix()).isEmpty();
        verify(visitRepo, never()).aggregateByBizDomain(any(), any(), any(), any());
    }

    @Test
    void computeDomainMatrix_empty_when_scope_MINE() {
        LeaderOverviewResponse res = service.build(
            "alice", List.of("ROLE_EMPLOYEE"), "D001", null, null, null, "MONTH"
        );
        assertThat(res.domainMatrix()).isEmpty();
    }

    @Test
    void computeDomainMatrix_top_6_plus_other_when_more_than_6() {
        when(visitRepo.aggregateByBizDomain(any(), any(), any(), any())).thenReturn(
            List.of(
                new DomainAggregateRow("a", 100L),
                new DomainAggregateRow("b", 90L),
                new DomainAggregateRow("c", 80L),
                new DomainAggregateRow("d", 70L),
                new DomainAggregateRow("e", 60L),
                new DomainAggregateRow("f", 50L),
                new DomainAggregateRow("g", 40L),
                new DomainAggregateRow("h", 30L)
            )
        );

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.domainMatrix()).hasSize(7);
        assertThat(res.domainMatrix().get(0).domain()).isEqualTo("a");
        assertThat(res.domainMatrix().get(0).visits()).isEqualTo(100L);
        assertThat(res.domainMatrix().get(6).domain()).isEqualTo("__OTHER__");
        assertThat(res.domainMatrix().get(6).domainName()).isEqualTo("其他");
        assertThat(res.domainMatrix().get(6).visits()).isEqualTo(70L); // 40+30
    }

    @Test
    void computeDomainMatrix_handles_null_bizDomain_as_uncategorized() {
        when(visitRepo.aggregateByBizDomain(any(), any(), any(), any())).thenReturn(
            List.of(new DomainAggregateRow(null, 10L))
        );

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.domainMatrix()).hasSize(1);
        assertThat(res.domainMatrix().get(0).domain()).isEqualTo("__UNCATEGORIZED__");
        assertThat(res.domainMatrix().get(0).domainName()).isEqualTo("未分类");
    }

    @Test
    void computeDomainMatrix_catalog_domain_repo_failure_uses_code() {
        when(catalogDomainRepo.findAll()).thenThrow(new RuntimeException("db down"));
        when(visitRepo.aggregateByBizDomain(any(), any(), any(), any())).thenReturn(
            List.of(new DomainAggregateRow("finance", 10L))
        );

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.domainMatrix()).hasSize(1);
        // Name falls back to the code when the domain repo is unavailable.
        assertThat(res.domainMatrix().get(0).domain()).isEqualTo("finance");
        assertThat(res.domainMatrix().get(0).domainName()).isEqualTo("finance");
    }

    @Test
    void computeDomainMatrix_uses_domain_name_when_repo_succeeds() {
        CatalogDomain dom = new CatalogDomain();
        dom.setCode("finance");
        dom.setName("财务");
        when(catalogDomainRepo.findAll()).thenReturn(List.of(dom));
        when(visitRepo.aggregateByBizDomain(any(), any(), any(), any())).thenReturn(
            List.of(new DomainAggregateRow("finance", 10L))
        );

        LeaderOverviewResponse res = service.build(
            "dan", List.of("ROLE_INST_LEADER"), "D001", "ALL", null, null, "MONTH"
        );
        assertThat(res.domainMatrix().get(0).domainName()).isEqualTo("财务");
    }
}
