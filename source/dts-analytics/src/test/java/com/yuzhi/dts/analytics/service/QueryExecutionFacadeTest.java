package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryExecutionFacadeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void prepare_nativeSelect_allowsReadOnlyQuery() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select * from test_table where id = {{id}}" }
                }
                """);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "parameters": { "id": 42 }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                service.prepare(datasetQuery, requestBody, null, DatasetQueryService.DatasetConstraints.defaults());

        assertThat(prepared.databaseId()).isEqualTo(1L);
        assertThat(prepared.type()).isEqualTo("native");
        assertThat(prepared.sql()).isEqualTo("select * from test_table where id = ?");
        assertThat(prepared.bindings()).containsExactly(42);
    }

    @Test
    void prepare_nativeUpdate_rejected() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "update test_table set name = 'a'" }
                }
                """);

        assertThatThrownBy(() -> service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only SELECT/WITH read-only SQL is allowed");
    }

    @Test
    void prepare_nativeMultiStatements_rejected() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select 1; select 2" }
                }
                """);

        assertThatThrownBy(() -> service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple SQL statements are not allowed");
    }

    @Test
    void prepare_nativeDangerousKeywordInStringLiteral_allowed() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select 'drop table demo' as msg" }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null);

        assertThat(prepared.sql()).isEqualTo("select 'drop table demo' as msg");
    }

    @Test
    void prepare_nativeTrailingSemicolonWithComment_allowed() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select 1; -- trailing terminator in comment context" }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null);

        assertThat(prepared.sql()).contains("select 1;");
    }

    @Test
    void prepare_nativeDollarTemplateTag_renderedWithBindings() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select * from test_table where day = ${day}" }
                }
                """);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "parameters": { "day": "2026-02-20" }
                }
                """);

        QueryExecutionFacade.PreparedQuery prepared =
                service.prepare(datasetQuery, requestBody, null, DatasetQueryService.DatasetConstraints.defaults());

        assertThat(prepared.sql()).isEqualTo("select * from test_table where day = ?");
        assertThat(prepared.bindings()).containsExactly("2026-02-20");
    }

    @Test
    void prepare_nativeTemplateWithoutParameters_rejected() throws Exception {
        QueryExecutionFacade service = service();
        JsonNode datasetQuery = objectMapper.readTree("""
                {
                  "database": 1,
                  "type": "native",
                  "native": { "query": "select * from test_table where id = {{id}}" }
                }
                """);

        assertThatThrownBy(() -> service.prepare(datasetQuery, objectMapper.createObjectNode(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required SQL template parameters");
    }

    @Test
    void checkNativeSqlSafety_identifiesReadOnlyAndDangerousKeyword() {
        QueryExecutionFacade.NativeSqlSafety safe = QueryExecutionFacade.checkNativeSqlSafety("select 1");
        assertThat(safe.readOnlySql()).isTrue();
        assertThat(safe.multipleStatements()).isFalse();
        assertThat(safe.dangerousKeywordMatched()).isFalse();

        QueryExecutionFacade.NativeSqlSafety blocked =
                QueryExecutionFacade.checkNativeSqlSafety("select * from t; delete from t");
        assertThat(blocked.readOnlySql()).isTrue();
        assertThat(blocked.multipleStatements()).isTrue();
    }

    @Test
    void executeWithCompliance_retriesOnceWhenSqlCanBeAutofixed() throws Exception {
        RetryDatasetQueryService datasetQueryService = new RetryDatasetQueryService();
        QueryExecutionFacade service = executionService(datasetQueryService);

        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                1L,
                "native",
                "select 1, from test_table;",
                List.of(),
                null,
                DatasetQueryService.DatasetConstraints.defaults());

        DatasetQueryService.DatasetResult result = service.executeWithCompliance(prepared);

        assertThat(result.rows()).hasSize(1);
        assertThat(datasetQueryService.executedSql).hasSize(2);
        assertThat(datasetQueryService.executedSql.get(0)).isEqualTo("select 1, from test_table;");
        assertThat(datasetQueryService.executedSql.get(1)).isEqualTo("select 1 from test_table");
    }

    @Test
    void executeWithCompliance_doesNotRetryWhenErrorNotRetryable() {
        RetryDatasetQueryService datasetQueryService = new RetryDatasetQueryService();
        datasetQueryService.failWithConnectionRefused = true;
        QueryExecutionFacade service = executionService(datasetQueryService);

        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                1L,
                "native",
                "select 1 from test_table",
                List.of(),
                null,
                DatasetQueryService.DatasetConstraints.defaults());

        assertThatThrownBy(() -> service.executeWithCompliance(prepared))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("connection refused");
        assertThat(datasetQueryService.executedSql).hasSize(1);
    }

    @Test
    void executeWithComplianceOutcome_recordsAutoFixRetryAttempts() throws Exception {
        RetryDatasetQueryService datasetQueryService = new RetryDatasetQueryService();
        QueryExecutionFacade service = executionService(datasetQueryService);

        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                1L,
                "native",
                "select 1, from test_table;",
                List.of(),
                null,
                DatasetQueryService.DatasetConstraints.defaults());

        QueryExecutionFacade.ExecutionOutcome outcome = service.executeWithComplianceOutcome(prepared);

        assertThat(outcome.result().rows()).hasSize(1);
        assertThat(outcome.attempts()).hasSize(2);
        assertThat(outcome.attempts().get(0).success()).isFalse();
        assertThat(outcome.attempts().get(0).errorCategory()).isEqualTo("syntax");
        assertThat(outcome.attempts().get(0).retryPlanned()).isTrue();
        assertThat(outcome.attempts().get(1).success()).isTrue();
        assertThat(outcome.attempts().get(1).fromAutoFix()).isTrue();
    }

    @Test
    void executeWithCompliance_retriesOnCommaBeforeClosingParenthesis() throws Exception {
        RetryDatasetQueryService datasetQueryService = new RetryDatasetQueryService();
        QueryExecutionFacade service = executionService(datasetQueryService);

        QueryExecutionFacade.PreparedQuery prepared = new QueryExecutionFacade.PreparedQuery(
                1L,
                "native",
                "select sum(amount, ) from test_table",
                List.of(),
                null,
                DatasetQueryService.DatasetConstraints.defaults());

        DatasetQueryService.DatasetResult result = service.executeWithCompliance(prepared);

        assertThat(result.rows()).hasSize(1);
        assertThat(datasetQueryService.executedSql).hasSize(2);
        assertThat(datasetQueryService.executedSql.get(1)).isEqualTo("select sum(amount) from test_table");
    }

    private QueryExecutionFacade service() {
        NativeQueryTemplateService nativeQueryTemplateService = new NativeQueryTemplateService();
        return new QueryExecutionFacade(
                null,
                null,
                nativeQueryTemplateService,
                null);
    }

    private QueryExecutionFacade executionService(RetryDatasetQueryService datasetQueryService) {
        NativeQueryTemplateService nativeQueryTemplateService = new NativeQueryTemplateService();
        return new QueryExecutionFacade(
                datasetQueryService,
                null,
                nativeQueryTemplateService,
                new ScreenComplianceService(objectMapper));
    }

    private static final class RetryDatasetQueryService extends DatasetQueryService {
        private final List<String> executedSql = new ArrayList<>();
        private boolean failWithConnectionRefused;

        private RetryDatasetQueryService() {
            super(null);
        }

        @Override
        public DatasetResult runNative(long databaseId, String sql, DatasetConstraints constraints, List<Object> bindings)
                throws SQLException {
            executedSql.add(sql);
            if (failWithConnectionRefused) {
                throw new SQLException("connection refused");
            }
            if (sql.contains(", from") || sql.contains(", )") || sql.contains(",,") || sql.contains("，") || sql.contains("（")) {
                throw new SQLException("syntax error at or near \"from\"");
            }
            return new DatasetResult(List.of(List.of(1)), List.of(), List.of(), "UTC");
        }
    }
}
