package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetConstraints;
import com.yuzhi.dts.copilot.analytics.service.DatasetQueryService.DatasetResult;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryExecutionFacadeTest {

    @Mock
    private DatasetQueryService datasetQueryService;

    @Mock
    private MbqlToSqlService mbqlToSqlService;

    @Mock
    private NativeQueryTemplateService nativeQueryTemplateService;

    @Mock
    private ScreenComplianceService screenComplianceService;

    @Mock
    private FederatedNativeSqlQualifier federatedNativeSqlQualifier;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldQualifyFederatedNativeSqlDuringPrepare() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService,
                mbqlToSqlService,
                nativeQueryTemplateService,
                screenComplianceService,
                federatedNativeSqlQualifier);
        String sql = "SELECT COUNT(*) FROM public.xycyl_ads_flowerbiz_sale_summary";
        String qualifiedSql = "SELECT COUNT(*) FROM postgres.public.xycyl_ads_flowerbiz_sale_summary";
        when(federatedNativeSqlQualifier.qualify(9L, sql)).thenReturn(qualifiedSql);
        JsonNode datasetQuery = objectMapper.readTree(
                """
                {
                  "database": 9,
                  "type": "native",
                  "native": {
                    "query": "SELECT COUNT(*) FROM public.xycyl_ads_flowerbiz_sale_summary"
                  }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                facade.prepare(datasetQuery, datasetQuery, null, DatasetConstraints.defaults());

        assertThat(prepared.sql()).isEqualTo(qualifiedSql);
    }

    @Test
    void shouldRetryPostgresToDateDateArgumentErrorWithDateCast() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService, mbqlToSqlService, nativeQueryTemplateService, screenComplianceService);
        DatasetConstraints constraints = DatasetConstraints.defaults();
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                2L,
                "native",
                """
                SELECT COUNT(*) AS "待处理单数"
                FROM public.xycyl_ads_flowerbiz_pending
                WHERE to_char(TO_DATE("申请时间", 'YYYY-MM-DD'), 'YYYY-MM') = '2026-03'
                """,
                List.of(),
                null,
                constraints);
        DatasetResult fixedResult = new DatasetResult(
                List.of(List.of(8L)),
                List.of(Map.of("name", "待处理单数", "display_name", "待处理单数", "base_type", "type/BigInteger")),
                List.of(),
                "Asia/Shanghai");
        SQLException postgresError = new SQLException(
                "ERROR: function to_date(date, unknown) does not exist\n"
                        + " Hint: No function matches the given name and argument types. You might need to add explicit type casts.");

        when(datasetQueryService.runNative(eq(2L), anyString(), eq(constraints), any()))
                .thenThrow(postgresError)
                .thenReturn(fixedResult);
        when(screenComplianceService.applyMasking(fixedResult)).thenReturn(fixedResult);

        List<QueryExecutionFacade.ExecutionAttempt> attempts = new ArrayList<>();
        DatasetResult result = facade.executeWithCompliance(prepared, attempts::add);

        assertThat(result).isSameAs(fixedResult);
        verify(datasetQueryService, times(2)).runNative(eq(2L), sqlCaptor.capture(), eq(constraints), any());
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("to_char(\"申请时间\"::date, 'YYYY-MM')")
                .doesNotContain("TO_DATE(\"申请时间\"");
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).retryPlanned()).isTrue();
        assertThat(attempts.get(0).rewrittenSql()).contains("\"申请时间\"::date");
        assertThat(attempts.get(1).success()).isTrue();
        assertThat(attempts.get(1).fromAutoFix()).isTrue();
    }

    @Test
    void shouldApplySameToDateAutoFixOnRawExecutionPath() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService, mbqlToSqlService, nativeQueryTemplateService, screenComplianceService);
        DatasetConstraints constraints = DatasetConstraints.defaults();
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                8L,
                "native",
                """
                SELECT COUNT(*) AS "待处理单数"
                FROM public.xycyl_ads_flowerbiz_pending
                WHERE to_char(TO_DATE("申请时间", 'YYYY-MM-DD'), 'YYYY-MM') = '2026-03'
                """,
                List.of(),
                null,
                constraints);
        DatasetResult fixedResult = new DatasetResult(
                List.of(List.of(8L)),
                List.of(Map.of("name", "待处理单数", "display_name", "待处理单数", "base_type", "type/BigInteger")),
                List.of(),
                "Asia/Shanghai");
        SQLException postgresError = new SQLException(
                "ERROR: function to_date(date, unknown) does not exist\n"
                        + " Hint: No function matches the given name and argument types.");

        when(datasetQueryService.runNative(eq(8L), anyString(), eq(constraints), any()))
                .thenThrow(postgresError)
                .thenReturn(fixedResult);

        DatasetResult result = facade.executeRaw(prepared);

        assertThat(result).isSameAs(fixedResult);
        verify(datasetQueryService, times(2)).runNative(eq(8L), sqlCaptor.capture(), eq(constraints), any());
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("to_char(\"申请时间\"::date, 'YYYY-MM')")
                .doesNotContain("TO_DATE(\"申请时间\"");
    }

    @Test
    void shouldBlockFinanceCaliberViolationBeforeRawExecution() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService, mbqlToSqlService, nativeQueryTemplateService, screenComplianceService);
        DatasetConstraints constraints = DatasetConstraints.defaults();
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                9L,
                "native",
                """
                SELECT SUM(s.receivable_amount) AS income_amount
                FROM mysql.rs_cloud_flower.a_sale_account s
                JOIN mysql.rs_cloud_flower.t_flower_biz_info b ON b.id = s.biz_id
                WHERE b.finish_time >= DATE '2026-01-01'
                """,
                List.of(),
                null,
                constraints);

        assertThatThrownBy(() -> facade.executeRaw(prepared))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAL-SETTLEMENT-CHAIN")
                .hasMessageContaining("坏账");
        verify(datasetQueryService, never()).runNative(anyLong(), anyString(), any(), any());
    }

    @Test
    void shouldRetryTrinoPostgresCastSyntaxErrorWithAnsiCast() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService, mbqlToSqlService, nativeQueryTemplateService, screenComplianceService);
        DatasetConstraints constraints = DatasetConstraints.defaults();
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                9L,
                "native",
                """
                SELECT ROUND(SUM(r."回收成本金额")::numeric, 2) AS "回收成本",
                       SUM(r."实际或计划回收数") AS "回收数量"
                FROM postgres.public.xycyl_ads_flowerbiz_recovery_detail r
                WHERE r."业务时间" >= ?::date AND r."业务时间" <= ?::date
                """,
                List.of("2026-01-01", "2026-12-31"),
                null,
                constraints);
        DatasetResult fixedResult = new DatasetResult(
                List.of(List.of("0.00")),
                List.of(Map.of("name", "回收成本", "display_name", "回收成本", "base_type", "type/Decimal")),
                List.of(),
                "Asia/Shanghai");
        SQLException trinoError = new SQLException(
                "Query failed (#20260603_120323_00056_4rh24): line 1:128: mismatched input ':'. "
                        + "Expecting: '%', '*', '+', '-', '.', '/', 'AND'");

        when(datasetQueryService.runNative(eq(9L), anyString(), eq(constraints), any()))
                .thenThrow(trinoError)
                .thenReturn(fixedResult);
        when(screenComplianceService.applyMasking(fixedResult)).thenReturn(fixedResult);

        List<QueryExecutionFacade.ExecutionAttempt> attempts = new ArrayList<>();
        DatasetResult result = facade.executeWithCompliance(prepared, attempts::add);

        assertThat(result).isSameAs(fixedResult);
        verify(datasetQueryService, times(2)).runNative(eq(9L), sqlCaptor.capture(), eq(constraints), any());
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("ROUND(SUM(TRY_CAST(r.\"回收成本金额\" AS DOUBLE)), 2)")
                .contains("SUM(TRY_CAST(r.\"实际或计划回收数\" AS DOUBLE)) AS \"回收数量\"")
                .contains("r.\"业务时间\" >= CAST(? AS date)")
                .contains("r.\"业务时间\" <= CAST(? AS date)")
                .doesNotContain("::");
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).retryPlanned()).isTrue();
        assertThat(attempts.get(1).fromAutoFix()).isTrue();
    }

    @Test
    void shouldRetryTrinoToCharDateFormatErrorWithDateFormat() throws Exception {
        QueryExecutionFacade facade = new QueryExecutionFacade(
                datasetQueryService, mbqlToSqlService, nativeQueryTemplateService, screenComplianceService);
        DatasetConstraints constraints = DatasetConstraints.defaults();
        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                9L,
                "native",
                """
                SELECT to_char(r."业务时间", 'YYYY-MM-DD') AS "业务日期"
                FROM postgres.public.xycyl_ads_flowerbiz_recovery_detail r
                LIMIT 1
                """,
                List.of(),
                null,
                constraints);
        DatasetResult fixedResult = new DatasetResult(
                List.of(List.of("2026-05-01")),
                List.of(Map.of("name", "业务日期", "display_name", "业务日期", "base_type", "type/Text")),
                List.of(),
                "Asia/Shanghai");
        SQLException trinoError = new SQLException(
                "Query failed (#20260603_120829_00060_4rh24): Failed to tokenize string [Y] at offset [0]");

        when(datasetQueryService.runNative(eq(9L), anyString(), eq(constraints), any()))
                .thenThrow(trinoError)
                .thenReturn(fixedResult);
        when(screenComplianceService.applyMasking(fixedResult)).thenReturn(fixedResult);

        DatasetResult result = facade.executeWithCompliance(prepared);

        assertThat(result).isSameAs(fixedResult);
        verify(datasetQueryService, times(2)).runNative(eq(9L), sqlCaptor.capture(), eq(constraints), any());
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("date_format(CAST(r.\"业务时间\" AS timestamp), '%Y-%m-%d')")
                .doesNotContain("to_char");
    }
}
