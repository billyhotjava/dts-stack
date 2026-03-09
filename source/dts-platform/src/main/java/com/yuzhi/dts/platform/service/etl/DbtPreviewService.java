package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtPreviewService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtPreviewService.class);

    /** Hard ceiling to prevent OOM on large tables. */
    private static final int MAX_ROWS = 500;

    /** Query timeout in seconds. */
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final DbtManifestService manifestService;
    private final InfraDataSourceRepository dataSourceRepository;
    private final InfraSecretService secretService;

    public DbtPreviewService(
        DbtProperties properties,
        DbtConfigService configService,
        DbtManifestService manifestService,
        InfraDataSourceRepository dataSourceRepository,
        InfraSecretService secretService
    ) {
        this.properties = properties;
        this.configService = configService;
        this.manifestService = manifestService;
        this.dataSourceRepository = dataSourceRepository;
        this.secretService = secretService;
    }

    /**
     * Preview data from a dbt model's materialized table/view.
     *
     * <ol>
     *   <li>Read dbt config to locate the target data-source.</li>
     *   <li>Look up the model in the manifest to resolve database, schema, and alias.</li>
     *   <li>Open a dedicated JDBC connection to the target warehouse.</li>
     *   <li>Execute {@code SELECT * FROM schema.alias LIMIT n}.</li>
     *   <li>Return column names + rows.</li>
     * </ol>
     */
    public PreviewResult preview(String modelName, int limit) {
        if (!properties.isEnabled()) {
            return PreviewResult.error("dbt 未启用");
        }

        int effectiveLimit = Math.max(1, Math.min(limit, MAX_ROWS));

        // 1. Resolve model metadata from manifest
        DbtManifestService.DbtModelResult modelResult = manifestService.listModels();
        if (!modelResult.enabled()) {
            return PreviewResult.error("dbt 未启用");
        }
        DbtManifestService.DbtModelSummary model = modelResult.models().stream()
            .filter(m -> modelName.equals(m.name()) || modelName.equals(m.uniqueId()))
            .findFirst()
            .orElse(null);
        if (model == null) {
            return PreviewResult.error("模型 '" + modelName + "' 在 manifest 中不存在");
        }

        // 2. Resolve the target data-source JDBC connection
        DbtConfigService.DbtConfigView configView = configService.loadConfig();
        if (configView.config() == null || configView.config().targetDataSourceId() == null) {
            return PreviewResult.error("未配置目标数仓");
        }
        UUID targetId = configView.config().targetDataSourceId();
        InfraDataSource source = dataSourceRepository.findById(targetId).orElse(null);
        if (source == null) {
            return PreviewResult.error("目标数仓数据源不存在");
        }
        String jdbcUrl = source.getJdbcUrl();
        if (!StringUtils.hasText(jdbcUrl)) {
            return PreviewResult.error("目标数仓 JDBC 地址为空");
        }
        String username = source.getUsername();
        Map<String, Object> secrets = secretService.readSecrets(source);
        String password = secrets != null ? stringVal(secrets.get("password")) : null;

        // 3. Build the fully-qualified table reference
        String schemaName = StringUtils.hasText(model.schema()) ? model.schema() : configView.config().schema();
        String tableName = StringUtils.hasText(model.alias()) ? model.alias() : model.name();
        String qualifiedTable = buildQualifiedTable(schemaName, tableName);

        // 4. Build the query with appropriate LIMIT syntax
        String sql = buildLimitQuery(qualifiedTable, effectiveLimit, source.getType());

        // 5. Execute and collect results
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    return PreviewResult.ok(qualifiedTable, columns, rows);
                }
            }
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            LOG.warn("Preview query failed for model '{}': {}", modelName, msg);
            if (msg != null && (msg.contains("does not exist") || msg.contains("doesn't exist") || msg.contains("not found"))) {
                return PreviewResult.error("表 " + qualifiedTable + " 不存在，请先执行 dbt run 进行物化");
            }
            return PreviewResult.error("查询失败: " + msg);
        }
    }

    private String buildQualifiedTable(String schema, String table) {
        if (StringUtils.hasText(schema)) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    /**
     * Build a SELECT with appropriate LIMIT syntax.
     * MySQL uses {@code LIMIT n}, PostgreSQL uses {@code LIMIT n} as well.
     */
    private String buildLimitQuery(String qualifiedTable, int limit, String dbType) {
        return "SELECT * FROM " + qualifiedTable + " LIMIT " + limit;
    }

    /**
     * Quote an identifier with double-quotes to handle reserved words and special characters.
     * For MySQL the JDBC driver typically accepts ANSI-quoted identifiers when the
     * {@code ANSI_QUOTES} mode is active; otherwise back-ticks would be needed.
     * We use double-quotes which are standard SQL and work for PostgreSQL out of the box.
     */
    private String quoteIdentifier(String identifier) {
        if (identifier == null) return "\"\"";
        // Prevent SQL injection: strip any embedded double-quotes
        return "\"" + identifier.replace("\"", "") + "\"";
    }

    private String stringVal(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    // ---- Result types ----

    public record PreviewResult(
        boolean success,
        String tableName,
        List<String> columns,
        List<Map<String, Object>> rows,
        int totalReturned,
        String error
    ) {
        public static PreviewResult ok(String tableName, List<String> columns, List<Map<String, Object>> rows) {
            return new PreviewResult(true, tableName, columns, rows, rows.size(), null);
        }

        public static PreviewResult error(String message) {
            return new PreviewResult(false, null, List.of(), List.of(), 0, message);
        }
    }
}
