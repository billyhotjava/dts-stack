package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor;
import com.yuzhi.dts.platform.service.sql.SqlValidationService;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlValidateResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Executes quality statements against the JDBC source bound to the selected catalog dataset.
 */
@Service
public class QualityDatasetStatementExecutor {

    private static final String SAFE_IDENTIFIER = "^[a-zA-Z_][a-zA-Z0-9_]*$";
    private static final int MAX_FAILURE_SAMPLES = 1000;
    private static final Principal QUALITY_EXECUTOR = () -> "governance-quality";

    private final DefaultLakeDatasetGuard datasetGuard;
    private final InfraDataSourceRepository dataSourceRepository;
    private final JdbcSqlExecutor jdbcSqlExecutor;
    private final SqlValidationService sqlValidationService;
    private final GovQualityFailingRowRepository failingRowRepository;
    private final GovernanceProperties governanceProperties;

    public QualityDatasetStatementExecutor(
        DefaultLakeDatasetGuard datasetGuard,
        InfraDataSourceRepository dataSourceRepository,
        JdbcSqlExecutor jdbcSqlExecutor,
        SqlValidationService sqlValidationService,
        GovQualityFailingRowRepository failingRowRepository,
        GovernanceProperties governanceProperties
    ) {
        this.datasetGuard = datasetGuard;
        this.dataSourceRepository = dataSourceRepository;
        this.jdbcSqlExecutor = jdbcSqlExecutor;
        this.sqlValidationService = sqlValidationService;
        this.failingRowRepository = failingRowRepository;
        this.governanceProperties = governanceProperties;
    }

    public Validation validate(UUID datasetId, Map<String, String> statements) {
        CatalogDataset dataset = datasetGuard.requireDefaultLakeDataset(datasetId);
        InfraDataSource source = dataSourceRepository.findById(dataset.getSourceId())
            .orElseThrow(() -> new IllegalStateException("数据资产关联的数据源不存在"));
        String sourceType = resolveSourceType(source);
        BoundTable table = resolveBoundTable(dataset, sourceType);
        List<QualityExecutionOutcome.Diagnostic> diagnostics = new ArrayList<>();
        statements.forEach((key, value) -> {
            String sql = normalizeSql(value);
            SqlValidateResponse validation = sqlValidationService.validate(
                new SqlValidateRequest(sql, source.getId().toString(), null, dataset.getHiveDatabase(), null), QUALITY_EXECUTOR);
            if (!validation.executable()) {
                diagnostics.add(new QualityExecutionOutcome.Diagnostic(key, "WRITE_BLOCKED", null));
            } else {
                var scope = checkScope(sql, table, sourceType);
                if (!scope.allowed()) diagnostics.add(new QualityExecutionOutcome.Diagnostic(key, scope.reasonCode(), scope.detail()));
            }
        });
        if (statements.isEmpty()) diagnostics.add(new QualityExecutionOutcome.Diagnostic("sql", "NO_STATEMENTS", null));
        return new Validation(diagnostics.isEmpty(), QualityRuleStatements.checksum(statements), List.copyOf(diagnostics),
            QualitySqlScopeValidator.allowedFunctions());
    }

    public Execution execute(GovQualityRun run, Map<String, String> statements) {
        if (run == null || run.getDatasetId() == null) throw new IllegalArgumentException("质量运行未绑定数据资产");
        Validation validation = validate(run.getDatasetId(), statements);
        List<StatementExecutionResult> results = new ArrayList<>();
        List<QualityExecutionOutcome.Diagnostic> diagnostics = new ArrayList<>(validation.diagnostics());
        if (!validation.valid()) {
            for (var diagnostic : diagnostics) results.add(new StatementExecutionResult(diagnostic.statementKey(), "",
                StatementExecutionResult.Status.FAILED, "检测 SQL 未通过校验", "WRITE_BLOCKED".equals(diagnostic.reasonCode()) ? "WRITE_BLOCKED" : "DATASET_SCOPE_BLOCKED"));
            return new Execution(results, null, null, new QualityExecutionOutcome(1, "UNKNOWN", "FAILED", "UNAVAILABLE", null, diagnostics));
        }
        CatalogDataset dataset = datasetGuard.requireDefaultLakeDataset(run.getDatasetId());
        InfraDataSource source = dataSourceRepository.findById(dataset.getSourceId()).orElseThrow();
        String sourceType = resolveSourceType(source);
        BoundTable table = resolveBoundTable(dataset, sourceType);
        List<GovQualityFailingRow> samples = new ArrayList<>();
        List<String> failingStatements = new ArrayList<>();
        Integer rowsTotal = null;
        Integer failingRows = null;
        long occurrences = 0;
        int completed = 0;
        boolean executionFailed = false;
        String statistics = "EXACT";
        try (Connection connection = jdbcSqlExecutor.getConnection(source)) {
            configureReadOnlyTransaction(connection, sourceType);
            try {
                rowsTotal = countRows(connection, table.qualifiedName());
                if (rowsTotal == null) statistics = "UNAVAILABLE";
                for (var entry : statements.entrySet()) {
                    String sql = normalizeSql(entry.getValue());
                    try {
                        long count = countFailures(connection, sql);
                        completed++;
                        occurrences = Math.addExact(occurrences, count);
                        if (count == 0) {
                            results.add(new StatementExecutionResult(entry.getKey(), sql, StatementExecutionResult.Status.SUCCEEDED, "未发现异常数据"));
                        } else {
                            // Record the business verdict before any optional sample/statistics operation.
                            failingStatements.add(sql);
                            results.add(new StatementExecutionResult(entry.getKey(), sql, StatementExecutionResult.Status.FAILED,
                                "发现 " + count + " 条不符合规则的数据", "QUALITY_VIOLATION"));
                            try {
                                collectFailureSamples(connection, sql, run, table.qualifiedName(), samples);
                            } catch (SQLException error) {
                                executionFailed = true;
                                diagnostics.add(new QualityExecutionOutcome.Diagnostic(entry.getKey(), "SAMPLE_FAILED", null));
                                results.add(new StatementExecutionResult(entry.getKey() + ":sample", "", StatementExecutionResult.Status.FAILED,
                                    "违规结论已保留，失败样本采集未完成", "SAMPLE_FAILED"));
                            }
                        }
                    } catch (SQLException | ArithmeticException error) {
                        executionFailed = true;
                        String code = error instanceof SQLException sqlError ? QualityRunOutcomeSemantics.normalizeErrorCode(safeSqlState(sqlError)) : "STATISTICS_OVERFLOW";
                        diagnostics.add(new QualityExecutionOutcome.Diagnostic(entry.getKey(), code, null));
                        results.add(new StatementExecutionResult(entry.getKey(), "", StatementExecutionResult.Status.FAILED, "检测语句执行未完成", code));
                    }
                }
                if (completed == statements.size()) {
                    if (failingStatements.size() <= 1) {
                        failingRows = occurrences <= Integer.MAX_VALUE ? (int) occurrences : null;
                    } else {
                        try { failingRows = countDistinctFailureRows(connection, failingStatements, rowsTotal); }
                        catch (SQLException error) {
                            statistics = "UNDEDUPLICATED";
                            diagnostics.add(new QualityExecutionOutcome.Diagnostic("statistics", "STATISTICS_UNDEDUPLICATED", null));
                        }
                    }
                }
            } finally { rollbackReadOnlyTransaction(connection, sourceType); }
        } catch (SQLException error) {
            executionFailed = true;
            diagnostics.add(new QualityExecutionOutcome.Diagnostic("connection", "CONNECTION_ERROR", null));
            results.add(new StatementExecutionResult("connection", "", StatementExecutionResult.Status.FAILED, "只读检测会话未能完成", "CONNECTION_ERROR"));
        }
        if (run.getId() != null && !samples.isEmpty()) {
            try { failingRowRepository.saveAll(samples); }
            catch (RuntimeException error) {
                // A persistence failure must roll back normally; the caller retains the execution outcome.
                throw error;
            }
        }
        if (failingRows == null && "EXACT".equals(statistics)) statistics = "UNAVAILABLE";
        String quality = occurrences > 0 ? "VIOLATION" : completed == statements.size() && !executionFailed ? "PASSED" : "UNKNOWN";
        return new Execution(List.copyOf(results), rowsTotal, failingRows, new QualityExecutionOutcome(1, quality,
            executionFailed ? "FAILED" : "OK", statistics, completed > 0 ? occurrences : null, diagnostics));
    }

    public record Validation(boolean valid, String checksum, List<QualityExecutionOutcome.Diagnostic> diagnostics, String allowedFunctions) {}

    private Integer countRows(Connection connection, String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return null;
        }
        try (Statement statement = connection.createStatement()) {
            applyTimeout(statement);
            try (ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + tableName)) {
                return resultSet.next() ? toInt(resultSet.getLong(1)) : null;
            }
        } catch (SQLException ignored) {
            return null;
        }
    }

    private long countFailures(Connection connection, String sql) throws SQLException {
        String countSql = "SELECT count(*) FROM (" + sql + ") quality_failures";
        try (PreparedStatement statement = connection.prepareStatement(countSql)) {
            applyTimeout(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Math.max(0, resultSet.getLong(1)) : 0;
            }
        }
    }

    private int countDistinctFailureRows(
        Connection connection,
        List<String> failingStatements,
        Integer rowsTotal
    ) throws SQLException {
        if (failingStatements.isEmpty()) {
            return 0;
        }
        String unionSql = failingStatements
            .stream()
            .map(sql -> "SELECT quality_statement.id AS row_id FROM (" + sql + ") quality_statement")
            .reduce((left, right) -> left + " UNION " + right)
            .orElseThrow();
        String countSql = "SELECT count(*) FROM (" + unionSql + ") quality_failed_rows";
        try (PreparedStatement statement = connection.prepareStatement(countSql)) {
            applyTimeout(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? boundedFailingRows(resultSet.getLong(1), rowsTotal) : 0;
            }
        }
    }

    private void collectFailureSamples(
        Connection connection,
        String sql,
        GovQualityRun run,
        String tableName,
        List<GovQualityFailingRow> samples
    ) throws SQLException {
        int remaining = MAX_FAILURE_SAMPLES - samples.size();
        if (remaining <= 0) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setFetchSize(Math.min(remaining, 500));
            statement.setMaxRows(remaining);
            applyTimeout(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                int rowNumber = samples.size();
                while (resultSet.next() && samples.size() < MAX_FAILURE_SAMPLES) {
                    rowNumber++;
                    GovQualityFailingRow row = new GovQualityFailingRow();
                    row.setRun(run);
                    row.setRuleId(run.getRule() != null ? run.getRule().getId() : null);
                    row.setTableName(truncate(tableName, 200));
                    row.setRowId(truncate(valueAsText(readColumn(resultSet, "id"), String.valueOf(rowNumber)), 64));
                    row.setColumnName(truncate(valueAsText(readColumn(resultSet, "fail_column"), null), 200));
                    row.setActualValue(valueAsText(readColumn(resultSet, "actual_value"), null));
                    row.setFailReason(truncate(valueAsText(readColumn(resultSet, "fail_reason"), "不符合质量规则"), 500));
                    row.setCreatedDate(Instant.now());
                    samples.add(row);
                }
            }
        }
    }

    private BoundTable resolveBoundTable(CatalogDataset dataset, String sourceType) {
        if (!isLowerCaseDialect(sourceType)) {
            return null;
        }
        String rawTable = StringUtils.hasText(dataset.getHiveTable()) ? dataset.getHiveTable().trim() : null;
        if (rawTable == null) {
            return null;
        }
        String[] parts = rawTable.split("\\.", -1);
        if (parts.length < 1 || parts.length > 2) {
            return null;
        }
        String table = canonicalLowerIdentifier(parts[parts.length - 1]);
        String embeddedSchema = parts.length == 2 ? canonicalLowerIdentifier(parts[0]) : null;
        String datasetSchema = StringUtils.hasText(dataset.getHiveDatabase())
            ? canonicalLowerIdentifier(dataset.getHiveDatabase())
            : null;
        if (
            table == null ||
            (parts.length == 2 && embeddedSchema == null) ||
            (StringUtils.hasText(dataset.getHiveDatabase()) && datasetSchema == null) ||
            (embeddedSchema != null && datasetSchema != null && !embeddedSchema.equals(datasetSchema))
        ) {
            return null;
        }
        String schema = embeddedSchema != null ? embeddedSchema : datasetSchema;
        return schema != null ? new BoundTable(schema, table, schema + "." + table) : null;
    }

    private QualitySqlScopeValidator.ScopeCheck checkScope(String sql, BoundTable boundTable, String sourceType) {
        if (boundTable == null) {
            return QualitySqlScopeValidator.ScopeCheck.denied("INVALID_BOUND_TABLE", "检测资产未解析出物理库表");
        }
        return QualitySqlScopeValidator.checkScope(sql, boundTable.schema(), boundTable.table(), sourceType);
    }

    private String resolveSourceType(InfraDataSource dataSource) {
        String jdbcUrl = dataSource.getJdbcUrl();
        if (StringUtils.hasText(jdbcUrl) && jdbcUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        return StringUtils.hasText(dataSource.getType()) ? dataSource.getType().trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    private boolean isLowerCaseDialect(String sourceType) {
        return sourceType != null && List.of("POSTGRES", "POSTGRESQL", "HIVE", "INCEPTOR").contains(sourceType);
    }

    private String canonicalLowerIdentifier(String value) {
        String identifier = safeIdentifier(value);
        return identifier != null && identifier.equals(identifier.toLowerCase(java.util.Locale.ROOT)) ? identifier : null;
    }

    private String safeIdentifier(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : null;
        return normalized != null && normalized.matches(SAFE_IDENTIFIER) ? normalized : null;
    }

    private String normalizeSql(String sql) {
        String normalized = StringUtils.hasText(sql) ? sql.trim() : "";
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private void configureReadOnlyTransaction(Connection connection, String sourceType) throws SQLException {
        if ("HIVE".equals(sourceType) || "INCEPTOR".equals(sourceType)) {
            return;
        }
        connection.setReadOnly(true);
        if (connection.getAutoCommit()) {
            connection.setAutoCommit(false);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET TRANSACTION READ ONLY");
            if ("POSTGRES".equals(sourceType) || "POSTGRESQL".equals(sourceType)) {
                statement.execute("SET LOCAL standard_conforming_strings = on");
                statement.execute("SET LOCAL search_path = pg_catalog");
            }
        } catch (SQLException exception) {
            throw new SQLException(
                "数据源不支持只读事务，已拒绝执行质量检测",
                exception.getSQLState(),
                exception.getErrorCode(),
                exception
            );
        }
    }

    private void rollbackReadOnlyTransaction(Connection connection, String sourceType) {
        if (!"POSTGRES".equals(sourceType) && !"POSTGRESQL".equals(sourceType)) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {}
    }

    private void applyTimeout(Statement statement) {
        Duration timeout = governanceProperties.getQuality().getTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return;
        }
        long seconds = Math.max(1, timeout.toSeconds());
        try {
            statement.setQueryTimeout((int) Math.min(Integer.MAX_VALUE, seconds));
        } catch (SQLException ignored) {
            // Some JDBC drivers do not implement query timeouts.
        }
    }

    private Object readColumn(ResultSet resultSet, String label) {
        try {
            return resultSet.getObject(label);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private String valueAsText(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String sqlErrorMessage(SQLException exception) {
        String sqlState = safeSqlState(exception);
        return sqlState == null
            ? "质量检测 SQL 执行失败"
            : "质量检测 SQL 执行失败（SQLSTATE: " + sqlState + "）";
    }

    private String safeSqlState(SQLException exception) {
        if (exception == null || !StringUtils.hasText(exception.getSQLState())) {
            return null;
        }
        String state = exception.getSQLState().trim().toUpperCase(java.util.Locale.ROOT);
        return state.matches("[A-Z0-9]{1,10}") ? state : null;
    }

    private int toInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    private int boundedFailingRows(long value, Integer rowsTotal) {
        int bounded = toInt(value);
        return rowsTotal == null ? bounded : Math.min(Math.max(0, rowsTotal), bounded);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record BoundTable(String schema, String table, String qualifiedName) {}

    public record Execution(List<StatementExecutionResult> results, Integer rowsTotal, Integer failingRowCount, QualityExecutionOutcome outcome) {
        public Execution(List<StatementExecutionResult> results, Integer rowsTotal, int failingRowCount) {
            this(results, rowsTotal, failingRowCount, legacyOutcome(results, rowsTotal, failingRowCount));
        }
        private static QualityExecutionOutcome legacyOutcome(List<StatementExecutionResult> results, Integer rowsTotal, int failingRowCount) {
            String error = QualityRunOutcomeSemantics.dominantFailureCategory(results);
            boolean violated = failingRowCount > 0 || results.stream().anyMatch(r -> "QUALITY_VIOLATION".equals(r.errorCode()) || r.message().contains("不符合规则"));
            return new QualityExecutionOutcome(1, violated ? "VIOLATION" : error == null ? "PASSED" : "UNKNOWN",
                error == null || "QUALITY_VIOLATION".equals(error) ? "OK" : "FAILED", rowsTotal == null ? "UNAVAILABLE" : "EXACT", (long) failingRowCount, List.of());
        }
    }
}
