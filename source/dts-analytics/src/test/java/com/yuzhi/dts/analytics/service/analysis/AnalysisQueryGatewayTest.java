package com.yuzhi.dts.analytics.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.QueryCacheService;
import com.yuzhi.dts.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AnalysisQueryGatewayTest {

    @Test
    void executesThroughFacadeTruncatesAndWritesPolicyScopedCache() throws Exception {
        QueryExecutionFacade executionFacade = mock(QueryExecutionFacade.class);
        QueryCacheService cacheService = mock(QueryCacheService.class);
        AnalyticsAuditForwarderService auditService = mock(AnalyticsAuditForwarderService.class);
        AnalysisSqlCompiler compiler = mock(AnalysisSqlCompiler.class);
        GovernedAnalysisDatasetContractProvider contractProvider = mock(GovernedAnalysisDatasetContractProvider.class);
        AnalyticsDatabaseBindingResolver bindingResolver = mock(AnalyticsDatabaseBindingResolver.class);
        AnalysisQueryGateway gateway = new AnalysisQueryGateway(
            contractProvider,
            bindingResolver,
            compiler,
            new AnalysisPolicyPlanner(),
            executionFacade,
            cacheService,
            new AnalysisQueryBudget(3, 20, 100),
            auditService,
            new ObjectMapper()
        );
        GovernedAnalysisDatasetContract contract = contract();
        AnalysisQuerySpec spec = spec(contract);
        DatasetQueryService.DatasetConstraints constraints = new DatasetQueryService.DatasetConstraints(3, 30, "UTC");
        AnalysisSqlCompiler.CompiledAnalysisQuery compiled = new AnalysisSqlCompiler.CompiledAnalysisQuery(
            11L,
            "select project_code from governed_dataset limit 3",
            List.of(),
            constraints,
            2,
            spec
        );
        when(contractProvider.get(contract.datasetId(), 1, "checksum")).thenReturn(contract);
        when(bindingResolver.requireDatabaseId(contract.sourceDatasourceId())).thenReturn(11L);
        when(compiler.compile(eq(spec), eq(contract), eq(11L), any())).thenReturn(compiled);
        when(executionFacade.prepare(any(), any(), any(), any())).thenReturn(new QueryExecutionFacade.PreparedQuery(
            11L,
            "native",
            compiled.sql(),
            List.of(),
            null,
            constraints
        ));
        when(cacheService.get(eq(11L), any(), eq(7L))).thenReturn(Optional.empty());
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("P1"));
        rows.add(List.of("P2"));
        rows.add(List.of("P3"));
        when(executionFacade.executeWithCompliance(any())).thenReturn(new DatasetQueryService.DatasetResult(
            rows,
            List.of(Map.of("name", "project_code")),
            List.of(),
            "UTC"
        ));
        AnalyticsUser actor = actor();

        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/api/analysis/preview");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        AnalysisQueryGateway.AnalysisQueryResult result;
        try {
            result = gateway.preview(
                actor,
                spec,
                new AnalysisRequestContext("D1", "INTERNAL", "ROLE_ANALYST", "request-1", "/api/analysis/preview", null)
            );
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.contractChecksum()).isEqualTo("checksum");
        verify(executionFacade).executeWithCompliance(any(QueryExecutionFacade.PreparedQuery.class));
        verify(cacheService).put(eq(11L), any(), eq(7L), any());
        verify(auditService).record(any());
        assertThat(servletRequest.getAttribute(AnalysisRequestContext.SPECIALIZED_AUDIT_RECORDED_ATTRIBUTE))
            .isEqualTo(Boolean.TRUE);
    }

    @Test
    void cancellationIsScopedToTheOwningActorAndInterruptsTheActiveQuery() throws Exception {
        QueryExecutionFacade executionFacade = mock(QueryExecutionFacade.class);
        QueryCacheService cacheService = mock(QueryCacheService.class);
        AnalysisSqlCompiler compiler = mock(AnalysisSqlCompiler.class);
        GovernedAnalysisDatasetContractProvider contractProvider = mock(GovernedAnalysisDatasetContractProvider.class);
        AnalyticsDatabaseBindingResolver bindingResolver = mock(AnalyticsDatabaseBindingResolver.class);
        AnalysisQueryGateway gateway = new AnalysisQueryGateway(
            contractProvider, bindingResolver, compiler, new AnalysisPolicyPlanner(), executionFacade, cacheService,
            new AnalysisQueryBudget(3, 20, 100), mock(AnalyticsAuditForwarderService.class), new ObjectMapper()
        );
        CountDownLatch entered = new CountDownLatch(1);
        when(cacheService.get(any(Long.class), any(), any(Long.class))).thenReturn(Optional.empty());
        when(executionFacade.executeWithCompliance(any())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                Thread.sleep(30_000);
                return new DatasetQueryService.DatasetResult(List.of(), List.of(), List.of(), "UTC");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new java.sql.SQLException("cancelled", "57014", interrupted);
            }
        });
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
            11L, "native", "select 1", List.of(), null,
            new DatasetQueryService.DatasetConstraints(2, 30, "UTC")
        );
        AnalyticsUser actor = actor();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(() -> gateway.executePrepared(
                actor,
                prepared,
                new AnalysisRequestContext("D1", "INTERNAL", "ROLE_ANALYST", "cancel-1", "/api/analysis/1/query", null),
                "checksum",
                1
            ));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThat(gateway.cancelQuery("cancel-1", actor)).isTrue();
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .hasCauseInstanceOf(AnalysisQueryCancelledException.class);
            assertThat(gateway.cancelQuery("cancel-1", actor)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private AnalyticsUser actor() {
        AnalyticsUser actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setEmail("analyst@example.test");
        actor.setActive(true);
        return actor;
    }

    private GovernedAnalysisDatasetContract contract() {
        return new GovernedAnalysisDatasetContract(
            UUID.randomUUID(), 1, "PUBLISHED", UUID.randomUUID(), "select project_code from ads_demo",
            List.of(new GovernedAnalysisDatasetContract.Dimension("project_code", "项目", "text", List.of(), null, List.of("EQ"))),
            List.of(), List.of(), List.of("classification:s1"), "DATA_INTERNAL", "r1", "checksum"
        );
    }

    private AnalysisQuerySpec spec(GovernedAnalysisDatasetContract contract) {
        return new AnalysisQuerySpec(
            "dts.analysis/v1",
            new AnalysisQuerySpec.DatasetRef(contract.datasetId(), 1, "r1", "checksum"),
            List.of(new AnalysisQuerySpec.DimensionSelection("project_code", null)),
            List.of(), List.of(), List.of(), null, List.of(), 2,
            new AnalysisQuerySpec.Visualization("table", Map.of())
        );
    }
}
