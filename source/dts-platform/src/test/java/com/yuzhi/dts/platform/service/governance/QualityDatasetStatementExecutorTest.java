package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor;
import com.yuzhi.dts.platform.service.sql.SqlValidationService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QualityDatasetStatementExecutorTest {

    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000010");
    private static final UUID SOURCE_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    @Test
    void executesAgainstTheBoundAssetsJdbcSourceAndPersistsReturnedFailureRows() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement readOnlyStatement = mock(Statement.class);
        Statement countStatement = mock(Statement.class);
        PreparedStatement failureCountStatement = mock(PreparedStatement.class);
        PreparedStatement sampleStatement = mock(PreparedStatement.class);
        PreparedStatement distinctCountStatement = mock(PreparedStatement.class);
        ResultSet tableCount = resultSetWithSingleLong(25L);
        ResultSet failureCount = resultSetWithSingleLong(1L);
        ResultSet sampleRows = mock(ResultSet.class);
        ResultSet distinctCount = resultSetWithSingleLong(1L);

        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        dataset.setName("ods_budget_v2");
        dataset.setType("POSTGRESQL");
        dataset.setSourceId(SOURCE_ID);
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("ods_budget_v2");
        InfraDataSource source = new InfraDataSource();
        source.setId(SOURCE_ID);
        source.setName("数据仓库 (biadmin)");
        source.setType("POSTGRESQL");
        source.setStatus("ACTIVE");
        source.setJdbcUrl("jdbc:postgresql://dts-pg:5432/biadmin");

        GovRule rule = new GovRule();
        rule.setId(UUID.fromString("10000000-0000-0000-0000-000000000010"));
        GovQualityRun run = new GovQualityRun();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000010"));
        run.setRule(rule);
        run.setDatasetId(DATASET_ID);

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(readOnlyStatement, countStatement);
        when(countStatement.executeQuery("SELECT count(*) FROM public.ods_budget_v2")).thenReturn(tableCount);
        when(connection.prepareStatement("SELECT count(*) FROM (SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL) quality_failures"))
            .thenReturn(failureCountStatement);
        when(failureCountStatement.executeQuery()).thenReturn(failureCount);
        when(connection.prepareStatement("SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL")).thenReturn(sampleStatement);
        when(connection.prepareStatement(
                "SELECT count(*) FROM (SELECT quality_statement.id AS row_id FROM (SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL) quality_statement) quality_failed_rows"
            ))
            .thenReturn(distinctCountStatement);
        when(sampleStatement.executeQuery()).thenReturn(sampleRows);
        when(distinctCountStatement.executeQuery()).thenReturn(distinctCount);
        when(sampleRows.next()).thenReturn(true, false);
        when(sampleRows.getObject("id")).thenReturn(7L);
        when(sampleRows.getObject("fail_column")).thenThrow(new java.sql.SQLException("missing"));
        when(sampleRows.getObject("actual_value")).thenThrow(new java.sql.SQLException("missing"));
        when(sampleRows.getObject("fail_reason")).thenThrow(new java.sql.SQLException("missing"));

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        QualityDatasetStatementExecutor.Execution execution = executor.execute(
            run,
            Map.of("sql", "SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL")
        );

        assertThat(execution.rowsTotal()).isEqualTo(25);
        assertThat(execution.failingRowCount()).isEqualTo(1);
        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.message()).contains("1 条");
        });
        ArgumentCaptor<List<GovQualityFailingRow>> rows = ArgumentCaptor.forClass(List.class);
        verify(failingRowRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getTableName()).isEqualTo("public.ods_budget_v2");
            assertThat(row.getRowId()).isEqualTo("7");
            assertThat(row.getFailReason()).isEqualTo("不符合质量规则");
        });
        verify(jdbcSqlExecutor).getConnection(source);
        verify(readOnlyStatement).execute("SET TRANSACTION READ ONLY");
        verify(readOnlyStatement).execute("SET LOCAL standard_conforming_strings = on");
        verify(readOnlyStatement).execute("SET LOCAL search_path = pg_catalog");
    }

    @Test
    void countsOverlappingFailuresAcrossStatementsOnlyOnce() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement readOnlyStatement = mock(Statement.class);
        Statement countStatement = mock(Statement.class);
        ResultSet tableCount = resultSetWithSingleLong(25L);
        String firstSql = "SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL";
        String secondSql = "SELECT id FROM public.ods_budget_v2 WHERE project_name IS NULL";
        PreparedStatement firstCount = mock(PreparedStatement.class);
        PreparedStatement secondCount = mock(PreparedStatement.class);
        PreparedStatement firstSample = mock(PreparedStatement.class);
        PreparedStatement secondSample = mock(PreparedStatement.class);
        PreparedStatement distinctCountStatement = mock(PreparedStatement.class);
        ResultSet firstRows = mock(ResultSet.class);
        ResultSet secondRows = mock(ResultSet.class);
        ResultSet firstCountRows = resultSetWithSingleLong(1L);
        ResultSet secondCountRows = resultSetWithSingleLong(1L);
        ResultSet distinctCountRows = resultSetWithSingleLong(1L);

        CatalogDataset dataset = dataset();
        InfraDataSource source = dataSource();
        GovQualityRun run = run();
        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(readOnlyStatement, countStatement);
        when(countStatement.executeQuery("SELECT count(*) FROM public.ods_budget_v2")).thenReturn(tableCount);
        when(connection.prepareStatement("SELECT count(*) FROM (" + firstSql + ") quality_failures")).thenReturn(firstCount);
        when(connection.prepareStatement("SELECT count(*) FROM (" + secondSql + ") quality_failures")).thenReturn(secondCount);
        when(firstCount.executeQuery()).thenReturn(firstCountRows);
        when(secondCount.executeQuery()).thenReturn(secondCountRows);
        when(connection.prepareStatement(firstSql)).thenReturn(firstSample);
        when(connection.prepareStatement(secondSql)).thenReturn(secondSample);
        when(firstSample.executeQuery()).thenReturn(firstRows);
        when(secondSample.executeQuery()).thenReturn(secondRows);
        when(firstRows.next()).thenReturn(true, false);
        when(secondRows.next()).thenReturn(true, false);
        when(firstRows.getObject("id")).thenReturn(7L);
        when(secondRows.getObject("id")).thenReturn(7L);
        String distinctSql =
            "SELECT count(*) FROM (SELECT quality_statement.id AS row_id FROM (" +
            firstSql +
            ") quality_statement UNION SELECT quality_statement.id AS row_id FROM (" +
            secondSql +
            ") quality_statement) quality_failed_rows";
        when(connection.prepareStatement(distinctSql)).thenReturn(distinctCountStatement);
        when(distinctCountStatement.executeQuery()).thenReturn(distinctCountRows);

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );
        Map<String, String> statements = new LinkedHashMap<>();
        statements.put("project_no", firstSql);
        statements.put("project_name", secondSql);

        QualityDatasetStatementExecutor.Execution execution = executor.execute(run, statements);

        assertThat(execution.rowsTotal()).isEqualTo(25);
        assertThat(execution.failingRowCount()).isEqualTo(1);
        assertThat(execution.results()).hasSize(2).allSatisfy(result ->
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED)
        );
    }

    @Test
    void reportsMissingStableRowIdsAsAnExecutionError() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement readOnlyStatement = mock(Statement.class);
        Statement countStatement = mock(Statement.class);
        PreparedStatement failureCountStatement = mock(PreparedStatement.class);
        PreparedStatement sampleStatement = mock(PreparedStatement.class);
        PreparedStatement distinctCountStatement = mock(PreparedStatement.class);
        ResultSet sampleRows = mock(ResultSet.class);
        ResultSet tableCount = resultSetWithSingleLong(25L);
        ResultSet failureCount = resultSetWithSingleLong(1L);
        String sql = "SELECT project_no FROM public.ods_budget_v2 WHERE project_no IS NULL";
        String distinctSql =
            "SELECT count(*) FROM (SELECT quality_statement.id AS row_id FROM (" +
            sql +
            ") quality_statement) quality_failed_rows";
        CatalogDataset dataset = dataset();
        InfraDataSource source = dataSource();

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(readOnlyStatement, countStatement);
        when(countStatement.executeQuery("SELECT count(*) FROM public.ods_budget_v2")).thenReturn(tableCount);
        when(connection.prepareStatement("SELECT count(*) FROM (" + sql + ") quality_failures")).thenReturn(
            failureCountStatement
        );
        when(failureCountStatement.executeQuery()).thenReturn(failureCount);
        when(connection.prepareStatement(sql)).thenReturn(sampleStatement);
        when(sampleStatement.executeQuery()).thenReturn(sampleRows);
        when(sampleRows.next()).thenReturn(false);
        when(connection.prepareStatement(distinctSql)).thenReturn(distinctCountStatement);
        when(distinctCountStatement.executeQuery()).thenThrow(new java.sql.SQLException("column id does not exist"));

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        QualityDatasetStatementExecutor.Execution execution = executor.execute(run(), Map.of("sql", sql));

        assertThat(execution.failingRowCount()).isZero();
        assertThat(execution.results()).anySatisfy(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("RESULT_ID_REQUIRED");
        });
    }

    @Test
    void rejectsWriteStatementsBeforeTheyReachJdbc() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement readOnlyStatement = mock(Statement.class);
        Statement countStatement = mock(Statement.class);
        ResultSet tableCount = resultSetWithSingleLong(25L);
        CatalogDataset dataset = dataset();
        InfraDataSource source = dataSource();

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(readOnlyStatement, countStatement);
        when(countStatement.executeQuery("SELECT count(*) FROM public.ods_budget_v2")).thenReturn(tableCount);

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        QualityDatasetStatementExecutor.Execution execution = executor.execute(
            run(),
            Map.of("sql", "DELETE FROM ods_budget_v2")
        );

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("WRITE_BLOCKED");
        });
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlThatReferencesAnotherTable() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT b.id FROM public.ods_budget_v2 b JOIN customer_secret s ON s.id = b.id";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlThatReferencesAnotherTableThroughACommaJoin() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql =
            "SELECT b.id, s.secret AS actual_value FROM public.ods_budget_v2 b, private.customer_secret s";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlThatUsesMultipleStatements() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT id FROM public.ods_budget_v2; SELECT secret FROM private.customer_secret";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlThatReadsThroughATableFunction() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT b.id FROM public.ods_budget_v2 b CROSS JOIN LATERAL dblink('remote', 'select 1') x";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsCatalogQualifiedReferencesToTheBoundTable() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT id FROM another_catalog.public.ods_budget_v2";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlThatReferencesTheBoundTableInAnotherSchema() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT id FROM private.ods_budget_v2";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsUnqualifiedOrCaseDifferentReferencesToTheBoundTable() throws Exception {
        for (String sql : List.of(
            "SELECT id FROM ods_budget_v2",
            "SELECT id FROM \"PUBLIC\".\"ODS_BUDGET_V2\""
        )) {
            StatementScopeHarness harness = statementScopeHarness();

            QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

            assertThat(execution.results()).singleElement().satisfies(result -> {
                assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
                assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
            });
            verify(harness.connection(), never()).prepareStatement(anyString());
        }
    }

    @Test
    void rejectsDynamicScalarFunctionsBeforeJdbcExecution() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql =
            "SELECT query_to_xml('SELECT secret FROM private.customer_secret', true, false, '') " +
            "FROM public.ods_budget_v2";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void rejectsQualitySqlWithoutAPhysicalTableReference() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT 1 AS id";

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.FAILED);
            assertThat(result.errorCode()).isEqualTo("DATASET_SCOPE_BLOCKED");
        });
        verify(harness.connection(), never()).prepareStatement(anyString());
    }

    @Test
    void acceptsCteQualitySqlWithAnExactlyQuotedReferenceToTheBoundTable() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = """
            WITH invalid_rows AS (
                SELECT id FROM "public"."ods_budget_v2" WHERE project_no IS NULL
            )
            SELECT id FROM invalid_rows
            """.trim();
        stubSuccessfulStatement(harness.connection(), sql);

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result ->
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.SUCCEEDED)
        );
    }

    @Test
    void acceptsAQualitySqlSelfJoinOnTheBoundTable() throws Exception {
        StatementScopeHarness harness = statementScopeHarness();
        String sql = "SELECT left_row.id FROM public.ods_budget_v2 left_row " +
            "JOIN public.ods_budget_v2 right_row ON right_row.id = left_row.id";
        stubSuccessfulStatement(harness.connection(), sql);

        QualityDatasetStatementExecutor.Execution execution = harness.executor().execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result ->
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.SUCCEEDED)
        );
    }

    @Test
    void rejectsExecutionWhenTheDatasourceCannotEstablishAReadOnlyTransaction() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        CatalogDataset dataset = dataset();
        InfraDataSource source = dataSource();

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("read only unsupported"))
            .when(connection)
            .setReadOnly(true);
        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        assertThatThrownBy(() ->
                executor.execute(run(), Map.of("sql", "SELECT id FROM public.ods_budget_v2 WHERE project_no IS NULL"))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("只读质量检测会话");
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void executesHiveWithoutUnsupportedJdbcTransactionCalls() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement countStatement = mock(Statement.class);
        PreparedStatement failureCountStatement = mock(PreparedStatement.class);

        CatalogDataset dataset = dataset();
        dataset.setType("HIVE");
        dataset.setHiveDatabase("default");
        InfraDataSource source = dataSource();
        source.setType("INCEPTOR");
        source.setJdbcUrl("jdbc:hive2://inceptor:10000/default");
        String sql = "SELECT id FROM default.ods_budget_v2 WHERE project_no IS NULL";

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(countStatement);
        ResultSet tableCount = mock(ResultSet.class);
        ResultSet failureCount = mock(ResultSet.class);
        when(tableCount.next()).thenReturn(true, false);
        when(tableCount.getLong(1)).thenReturn(25L);
        when(failureCount.next()).thenReturn(true, false);
        when(failureCount.getLong(1)).thenReturn(0L);
        when(countStatement.executeQuery("SELECT count(*) FROM default.ods_budget_v2"))
            .thenReturn(tableCount);
        when(connection.prepareStatement("SELECT count(*) FROM (" + sql + ") quality_failures"))
            .thenReturn(failureCountStatement);
        when(failureCountStatement.executeQuery()).thenReturn(failureCount);

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        QualityDatasetStatementExecutor.Execution execution = executor.execute(run(), Map.of("sql", sql));

        assertThat(execution.results()).singleElement().satisfies(result ->
            assertThat(result.status()).isEqualTo(StatementExecutionResult.Status.SUCCEEDED)
        );
        verify(connection, never()).setReadOnly(true);
        verify(connection, never()).getAutoCommit();
        verify(connection, never()).setAutoCommit(false);
        verify(connection, never()).rollback();
        verify(connection, times(1)).createStatement();
    }

    @Test
    void neverExposesJdbcSqlOrCredentialText() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        CatalogDataset dataset = dataset();
        InfraDataSource source = dataSource();
        String sensitive = "SELECT token FROM credentials password=top-secret-token";

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset);
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenThrow(new java.sql.SQLException(sensitive, "08001"));

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );

        assertThatThrownBy(() -> executor.execute(run(), Map.of("sql", "SELECT id FROM public.ods_budget_v2")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("质量检测会话")
            .hasMessageContaining("SQLSTATE: 08001")
            .hasMessageNotContaining("SELECT token")
            .hasMessageNotContaining("password")
            .hasMessageNotContaining("top-secret-token");
    }

    private static CatalogDataset dataset() {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        dataset.setName("ods_budget_v2");
        dataset.setType("POSTGRESQL");
        dataset.setSourceId(SOURCE_ID);
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("ods_budget_v2");
        return dataset;
    }

    private static InfraDataSource dataSource() {
        InfraDataSource source = new InfraDataSource();
        source.setId(SOURCE_ID);
        source.setName("数据仓库 (biadmin)");
        source.setType("POSTGRESQL");
        source.setStatus("ACTIVE");
        source.setJdbcUrl("jdbc:postgresql://dts-pg:5432/biadmin");
        return source;
    }

    private static GovQualityRun run() {
        GovRule rule = new GovRule();
        rule.setId(UUID.fromString("10000000-0000-0000-0000-000000000010"));
        GovQualityRun run = new GovQualityRun();
        run.setId(UUID.fromString("50000000-0000-0000-0000-000000000010"));
        run.setRule(rule);
        run.setDatasetId(DATASET_ID);
        return run;
    }

    private static ResultSet resultSetWithSingleLong(long value) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong(1)).thenReturn(value);
        return resultSet;
    }

    private static StatementScopeHarness statementScopeHarness() throws Exception {
        DefaultLakeDatasetGuard datasetGuard = mock(DefaultLakeDatasetGuard.class);
        InfraDataSourceRepository dataSourceRepository = mock(InfraDataSourceRepository.class);
        JdbcSqlExecutor jdbcSqlExecutor = mock(JdbcSqlExecutor.class);
        GovQualityFailingRowRepository failingRowRepository = mock(GovQualityFailingRowRepository.class);
        Connection connection = mock(Connection.class);
        Statement readOnlyStatement = mock(Statement.class);
        Statement countStatement = mock(Statement.class);
        InfraDataSource source = dataSource();

        when(datasetGuard.requireDefaultLakeDataset(DATASET_ID)).thenReturn(dataset());
        when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(jdbcSqlExecutor.getConnection(source)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(readOnlyStatement, countStatement);
        ResultSet countResult = resultSetWithSingleLong(25L);
        when(countStatement.executeQuery("SELECT count(*) FROM public.ods_budget_v2")).thenReturn(countResult);

        QualityDatasetStatementExecutor executor = new QualityDatasetStatementExecutor(
            datasetGuard,
            dataSourceRepository,
            jdbcSqlExecutor,
            new SqlValidationService(),
            failingRowRepository,
            new GovernanceProperties()
        );
        return new StatementScopeHarness(executor, connection);
    }

    private static void stubSuccessfulStatement(Connection connection, String sql) throws Exception {
        PreparedStatement failureCountStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement("SELECT count(*) FROM (" + sql + ") quality_failures")).thenReturn(
            failureCountStatement
        );
        ResultSet failureCountResult = resultSetWithSingleLong(0L);
        when(failureCountStatement.executeQuery()).thenReturn(failureCountResult);
    }

    private record StatementScopeHarness(QualityDatasetStatementExecutor executor, Connection connection) {}
}
