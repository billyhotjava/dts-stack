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

    public Execution execute(GovQualityRun run, Map<String, String> statements) {
        if (run == null || run.getDatasetId() == null) {
            throw new IllegalArgumentException("质量运行未绑定数据资产");
        }
        CatalogDataset dataset = datasetGuard.requireDefaultLakeDataset(run.getDatasetId());
        UUID sourceId = dataset.getSourceId();
        if (sourceId == null) {
            throw new IllegalStateException("数据资产未关联可执行的数据源");
        }
        InfraDataSource dataSource = dataSourceRepository
            .findById(sourceId)
            .orElseThrow(() -> new IllegalStateException("数据资产关联的数据源不存在"));
        if (!StringUtils.hasText(dataSource.getJdbcUrl())) {
            throw new IllegalStateException("数据资产关联的数据源未配置 JDBC 连接");
        }

        String sourceType = resolveSourceType(dataSource);
        BoundTable boundTable = resolveBoundTable(dataset, sourceType);
        String tableName = boundTable != null ? boundTable.qualifiedName() : null;
        String persistedTableName = StringUtils.hasText(tableName) ? tableName : dataset.getName();
        List<StatementExecutionResult> results = new ArrayList<>();
        List<GovQualityFailingRow> samples = new ArrayList<>();
        List<String> failingStatements = new ArrayList<>();
        Integer rowsTotal = null;
        int failingRowCount = 0;

        try (Connection connection = jdbcSqlExecutor.getConnection(dataSource)) {
            configureReadOnlyTransaction(connection, sourceType);
            try {
                rowsTotal = countRows(connection, tableName);
                for (Map.Entry<String, String> entry : statements.entrySet()) {
                    String sql = normalizeSql(entry.getValue());
                    if (!StringUtils.hasText(sql)) {
                        results.add(
                            new StatementExecutionResult(
                                entry.getKey(),
                                entry.getValue(),
                                StatementExecutionResult.Status.SKIPPED,
                                "检测语句为空"
                            )
                        );
                        continue;
                    }
                    SqlValidateResponse validation = sqlValidationService.validate(
                        new SqlValidateRequest(sql, sourceId.toString(), null, dataset.getHiveDatabase(), null),
                        QUALITY_EXECUTOR
                    );
                    if (!validation.executable()) {
                        results.add(
                            new StatementExecutionResult(
                                entry.getKey(),
                                sql,
                                StatementExecutionResult.Status.FAILED,
                                "质量检测 SQL 未通过只读安全校验",
                                "WRITE_BLOCKED"
                            )
                        );
                        continue;
                    }
                    QualitySqlScopeValidator.ScopeCheck scope = checkScope(sql, boundTable, sourceType);
                    if (!scope.allowed()) {
                        results.add(
                            new StatementExecutionResult(
                                entry.getKey(),
                                sql,
                                StatementExecutionResult.Status.FAILED,
                                scope.message(),
                                "DATASET_SCOPE_BLOCKED"
                            )
                        );
                        continue;
                    }
                    try {
                        long statementFailureCount = countFailures(connection, sql);
                        if (statementFailureCount == 0) {
                            results.add(
                                new StatementExecutionResult(
                                    entry.getKey(),
                                    sql,
                                    StatementExecutionResult.Status.SUCCEEDED,
                                    "未发现异常数据"
                                )
                            );
                            continue;
                        }
                        failingStatements.add(sql);
                        collectFailureSamples(connection, sql, run, persistedTableName, samples);
                        results.add(
                            new StatementExecutionResult(
                                entry.getKey(),
                                sql,
                                StatementExecutionResult.Status.FAILED,
                                "发现 " + statementFailureCount + " 条不符合规则的数据"
                            )
                        );
                    } catch (SQLException ex) {
                        results.add(
                            new StatementExecutionResult(
                                entry.getKey(),
                                sql,
                                StatementExecutionResult.Status.FAILED,
                                sqlErrorMessage(ex),
                                safeSqlState(ex)
                            )
                        );
                    }
                }
                try {
                    failingRowCount = countDistinctFailureRows(connection, failingStatements, rowsTotal);
                } catch (SQLException exception) {
                    results.add(
                        new StatementExecutionResult(
                            "__failed_rows__",
                            "",
                            StatementExecutionResult.Status.FAILED,
                            "质量检测结果必须返回稳定的 id 字段：" + sqlErrorMessage(exception),
                            "RESULT_ID_REQUIRED"
                        )
                    );
                }
            } finally {
                rollbackReadOnlyTransaction(connection, sourceType);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法建立只读质量检测会话：" + sqlErrorMessage(ex), ex);
        }

        if (!samples.isEmpty()) {
            failingRowRepository.saveAll(samples);
        }
        return new Execution(results, rowsTotal, failingRowCount);
    }

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

    public record Execution(List<StatementExecutionResult> results, Integer rowsTotal, int failingRowCount) {}
}
