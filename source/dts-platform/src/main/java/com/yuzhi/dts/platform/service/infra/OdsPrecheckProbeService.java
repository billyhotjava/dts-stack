package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsPrecheckRuleResult;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsTablePlanDto;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OdsPrecheckProbeService {

    private static final Logger LOG = LoggerFactory.getLogger(OdsPrecheckProbeService.class);
    private static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 60;

    private final InfraSecretService secretService;
    private final HiveConnectionService hiveConnectionService;
    private final ObjectMapper objectMapper;

    public OdsPrecheckProbeService(
        InfraSecretService secretService,
        HiveConnectionService hiveConnectionService,
        ObjectMapper objectMapper
    ) {
        this.secretService = secretService;
        this.hiveConnectionService = hiveConnectionService;
        this.objectMapper = objectMapper;
    }

    public List<OdsPrecheckRuleResult> probe(
        InfraDataSource source,
        List<OdsTablePlanDto> plans,
        String syncMode,
        String incrementalColumn
    ) {
        if (source == null || plans == null || plans.isEmpty()) {
            return List.of();
        }
        Map<String, Object> props = readProps(source.getProps());
        if (boolProp(props, "precheckProbeDisabled", false)) {
            return List.of(
                rule(
                    "SOURCE_PROBE_DISABLED",
                    "INFO",
                    true,
                    source.getName(),
                    "源端深度预检已按数据源配置跳过",
                    "移除 props.precheckProbeDisabled 后可启用源端查询、行数和质量探测"
                )
            );
        }

        int queryTimeoutSeconds = clamp(intProp(props, "precheckQueryTimeoutSeconds", DEFAULT_QUERY_TIMEOUT_SECONDS), 1, MAX_QUERY_TIMEOUT_SECONDS);
        List<OdsPrecheckRuleResult> rules = new ArrayList<>();
        try (Connection connection = openConnection(source, props)) {
            rules.add(
                rule(
                    "SOURCE_PROBE_CONNECT",
                    "INFO",
                    true,
                    source.getName(),
                    "源端 JDBC 连接可用于提交前预检",
                    "继续执行源表查询权限和数据质量探测"
                )
            );
            for (OdsTablePlanDto plan : plans) {
                probeTable(connection, plan, syncMode, incrementalColumn, queryTimeoutSeconds, rules);
            }
        } catch (Exception ex) {
            LOG.debug("ODS source probe failed for {}: {}", source.getName(), ex.getMessage(), ex);
            rules.add(
                rule(
                    "SOURCE_PROBE_CONNECT",
                    "ERROR",
                    false,
                    source.getName(),
                    "源端 JDBC 连接无法执行提交前预检：" + safeMessage(ex),
                    "请重新测试连接，确认凭据、网络、驱动和源端账号权限"
                )
            );
        }
        return rules;
    }

    private void probeTable(
        Connection connection,
        OdsTablePlanDto plan,
        String syncMode,
        String incrementalColumn,
        int timeoutSeconds,
        List<OdsPrecheckRuleResult> rules
    ) {
        if (plan == null || !StringUtils.hasText(plan.sourceTable())) {
            return;
        }
        String sourceName = physicalName(plan.sourceSchema(), plan.sourceTable());
        String tableName = qualifiedName(connection, plan.sourceSchema(), plan.sourceTable());
        boolean canQuery = canSelect(connection, tableName, timeoutSeconds);
        rules.add(
            rule(
                "SOURCE_QUERY_PERMISSION",
                "ERROR",
                canQuery,
                sourceName,
                "源表可被当前账号查询：" + sourceName,
                "请为源端账号授予 SELECT 权限，或调整数据源凭据"
            )
        );
        if (!canQuery) {
            return;
        }

        Long totalRows = queryLong(connection, "SELECT COUNT(*) FROM " + tableName, timeoutSeconds);
        if (totalRows == null) {
            rules.add(
                rule(
                    "SOURCE_ROW_COUNT",
                    "WARN",
                    false,
                    sourceName,
                    "源表行数探测未完成：" + sourceName,
                    "可调大 props.precheckQueryTimeoutSeconds，或在大表场景关闭深度探测"
                )
            );
        } else {
            rules.add(
                rule(
                    "SOURCE_ROW_COUNT",
                    "INFO",
                    true,
                    sourceName,
                    "源表当前行数：" + totalRows,
                    "后续可基于运行历史形成行数波动基线"
                )
            );
        }

        probePrimaryKey(connection, plan, tableName, sourceName, timeoutSeconds, rules);
        if ("incremental".equalsIgnoreCase(syncMode) && StringUtils.hasText(incrementalColumn)) {
            probeIncrementalColumn(connection, plan, tableName, sourceName, incrementalColumn, totalRows, timeoutSeconds, rules);
        }
    }

    private void probePrimaryKey(
        Connection connection,
        OdsTablePlanDto plan,
        String tableName,
        String sourceName,
        int timeoutSeconds,
        List<OdsPrecheckRuleResult> rules
    ) {
        List<String> primaryKeys = normalizeColumns(plan.primaryKeys());
        if (primaryKeys.isEmpty()) {
            return;
        }
        List<String> quotedKeys = primaryKeys.stream().map(column -> quoteIdentifier(connection, column)).toList();
        String nullCondition = String.join(" OR ", quotedKeys.stream().map(column -> column + " IS NULL").toList());
        Long nullRows = queryLong(connection, "SELECT COUNT(*) FROM " + tableName + " WHERE " + nullCondition, timeoutSeconds);
        if (nullRows == null) {
            rules.add(
                rule(
                    "PRIMARY_KEY_NULL_RATE",
                    "WARN",
                    false,
                    sourceName,
                    "主键空值探测未完成：" + String.join(", ", primaryKeys),
                    "请人工确认主键字段非空，或调大预检查询超时时间"
                )
            );
        } else {
            rules.add(
                rule(
                    "PRIMARY_KEY_NULL_RATE",
                    "WARN",
                    nullRows.longValue() == 0L,
                    sourceName,
                    nullRows == 0L ? "主键字段无空值：" + String.join(", ", primaryKeys) : "主键字段存在空值行：" + nullRows,
                    "请确认业务主键质量；空主键会影响幂等写入、审计追踪和删除识别"
                )
            );
        }

        String groupColumns = String.join(", ", quotedKeys);
        String duplicateSql =
            "SELECT COUNT(*) FROM (SELECT " +
            groupColumns +
            " FROM " +
            tableName +
            " GROUP BY " +
            groupColumns +
            " HAVING COUNT(*) > 1) dts_pk_duplicate_groups";
        Long duplicateGroups = queryLong(connection, duplicateSql, timeoutSeconds);
        if (duplicateGroups == null) {
            rules.add(
                rule(
                    "PRIMARY_KEY_UNIQUENESS",
                    "WARN",
                    false,
                    sourceName,
                    "主键唯一性探测未完成：" + String.join(", ", primaryKeys),
                    "请人工确认主键唯一，或调大预检查询超时时间"
                )
            );
        } else {
            rules.add(
                rule(
                    "PRIMARY_KEY_UNIQUENESS",
                    "WARN",
                    duplicateGroups.longValue() == 0L,
                    sourceName,
                    duplicateGroups == 0L ? "主键唯一性探测通过：" + String.join(", ", primaryKeys) : "主键存在重复分组：" + duplicateGroups,
                    "请修复源端主键重复，或改用更稳定的业务唯一键"
                )
            );
        }
    }

    private void probeIncrementalColumn(
        Connection connection,
        OdsTablePlanDto plan,
        String tableName,
        String sourceName,
        String incrementalColumn,
        Long totalRows,
        int timeoutSeconds,
        List<OdsPrecheckRuleResult> rules
    ) {
        String actualColumn = findColumn(plan, incrementalColumn);
        if (!StringUtils.hasText(actualColumn)) {
            rules.add(
                rule(
                    "INCREMENTAL_COLUMN_PRESENT",
                    "ERROR",
                    false,
                    sourceName,
                    "增量字段不在源表字段清单中：" + incrementalColumn,
                    "请重新执行 Schema Discover，或改为全量模式"
                )
            );
            return;
        }
        String quoted = quoteIdentifier(connection, actualColumn);
        Long nullRows = queryLong(connection, "SELECT COUNT(*) FROM " + tableName + " WHERE " + quoted + " IS NULL", timeoutSeconds);
        if (nullRows == null) {
            rules.add(
                rule(
                    "INCREMENTAL_NON_NULL",
                    "WARN",
                    false,
                    sourceName,
                    "增量字段非空率探测未完成：" + actualColumn,
                    "请人工确认增量字段稳定非空，或调大预检查询超时时间"
                )
            );
            return;
        }
        boolean noNulls = nullRows.longValue() == 0L;
        boolean allNull = totalRows != null && totalRows.longValue() > 0L && nullRows.longValue() >= totalRows.longValue();
        rules.add(
            rule(
                "INCREMENTAL_NON_NULL",
                allNull ? "ERROR" : "WARN",
                noNulls,
                sourceName,
                noNulls ? "增量字段无空值：" + actualColumn : "增量字段存在空值行：" + nullRows + " / " + (totalRows == null ? "未知" : totalRows),
                "请修复增量字段空值；空值行可能无法被时间戳增量稳定捕获"
            )
        );
    }

    private boolean canSelect(Connection connection, String tableName, int timeoutSeconds) {
        String sql = "SELECT * FROM " + tableName + " WHERE 1 = 0";
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.setMaxRows(1);
            try (ResultSet ignored = statement.executeQuery(sql)) {
                return true;
            }
        } catch (SQLException ex) {
            LOG.debug("Source permission probe failed for {}: {}", tableName, ex.getMessage());
            return false;
        }
    }

    private Long queryLong(Connection connection, String sql, int timeoutSeconds) {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet rs = statement.executeQuery(sql)) {
                if (rs.next()) {
                    Object value = rs.getObject(1);
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    if (value != null) {
                        return Long.parseLong(value.toString());
                    }
                }
            }
        } catch (Exception ex) {
            LOG.debug("ODS source probe query failed: {} ({})", sql, ex.getMessage());
        }
        return null;
    }

    private Connection openConnection(InfraDataSource source, Map<String, Object> props) throws SQLException {
        String url = source.getJdbcUrl() == null ? null : source.getJdbcUrl().trim();
        if (!StringUtils.hasText(url)) {
            throw new SQLException("missing JDBC URL");
        }
        Map<String, Object> secrets = secretService != null ? secretService.readSecrets(source) : Collections.emptyMap();
        String password = firstNonBlank(stringProp(secrets, "password"), stringProp(props, "password"));
        Properties jdbcProps = new Properties();
        if (StringUtils.hasText(source.getUsername())) {
            jdbcProps.setProperty("user", source.getUsername().trim());
        }
        if (StringUtils.hasText(password)) {
            jdbcProps.setProperty("password", password);
        }
        Object jdbcProperties = props.get("jdbcProperties");
        if (jdbcProperties instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                String value = entry.getValue() == null ? null : entry.getValue().toString();
                if (StringUtils.hasText(key) && value != null) {
                    jdbcProps.setProperty(key.trim(), value);
                }
            }
        }

        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        ClassLoader jdbcLoader = hiveConnectionService != null ? hiveConnectionService.getJdbcDriverLoader() : null;
        if (jdbcLoader != null) {
            Thread.currentThread().setContextClassLoader(jdbcLoader);
        }
        try {
            String driverClass = firstNonBlank(stringProp(props, "driverClass"), stringProp(props, "driver_class"), inferDriverClass(url));
            if (StringUtils.hasText(driverClass)) {
                try {
                    if (jdbcLoader != null) {
                        Class.forName(driverClass, true, jdbcLoader);
                    } else {
                        Class.forName(driverClass);
                    }
                } catch (Throwable ex) {
                    LOG.debug("Failed to load JDBC driver class {} for ODS precheck: {}", driverClass, ex.getMessage());
                }
            }
            try {
                DriverManager.setLoginTimeout(clamp(intProp(props, "precheckLoginTimeoutSeconds", DEFAULT_LOGIN_TIMEOUT_SECONDS), 1, MAX_QUERY_TIMEOUT_SECONDS));
            } catch (Throwable ignored) {}
            return DriverManager.getConnection(url, jdbcProps);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    private String qualifiedName(Connection connection, String schema, String table) {
        String quotedTable = quoteIdentifier(connection, table);
        if (!StringUtils.hasText(schema)) {
            return quotedTable;
        }
        return quoteIdentifier(connection, schema) + "." + quotedTable;
    }

    private String quoteIdentifier(Connection connection, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        String quote = null;
        try {
            quote = connection.getMetaData().getIdentifierQuoteString();
        } catch (SQLException ignored) {}
        if (!StringUtils.hasText(quote)) {
            return value;
        }
        quote = quote.trim();
        if (!StringUtils.hasText(quote)) {
            return value;
        }
        return quote + value.replace(quote, quote + quote) + quote;
    }

    private List<String> normalizeColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String column : columns) {
            if (StringUtils.hasText(column)) {
                seen.add(column.trim());
            }
        }
        return List.copyOf(seen);
    }

    private String findColumn(OdsTablePlanDto plan, String columnName) {
        if (plan == null || plan.columns() == null || !StringUtils.hasText(columnName)) {
            return null;
        }
        for (var column : plan.columns()) {
            if (column != null && column.sourceName() != null && column.sourceName().equalsIgnoreCase(columnName.trim())) {
                return column.sourceName();
            }
        }
        return null;
    }

    private OdsPrecheckRuleResult rule(String code, String level, boolean passed, String target, String message, String suggestion) {
        String normalizedLevel = StringUtils.hasText(level) ? level.trim().toUpperCase(Locale.ROOT) : "INFO";
        String status = passed ? "PASS" : "ERROR".equals(normalizedLevel) ? "FAIL" : "WARN";
        return new OdsPrecheckRuleResult(code, normalizedLevel, status, target, message, suggestion);
    }

    private String physicalName(String schema, String table) {
        if (!StringUtils.hasText(schema)) {
            return StringUtils.hasText(table) ? table.trim() : "";
        }
        return schema.trim() + "." + (StringUtils.hasText(table) ? table.trim() : "");
    }

    private Map<String, Object> readProps(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private String stringProp(Map<String, Object> map, String key) {
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private boolean boolProp(Map<String, Object> map, String key, boolean fallback) {
        String value = stringProp(map, key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private int intProp(Map<String, Object> map, String key, int fallback) {
        String value = stringProp(map, key);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String inferDriverClass(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) return null;
        String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (url.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (url.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (url.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (url.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (url.startsWith("jdbc:hive2:") || url.startsWith("jdbc:inceptor") || url.startsWith("jdbc:inceptor2:")) {
            return "org.apache.hive.jdbc.HiveDriver";
        }
        return null;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return "未知错误";
        }
        return ex.getMessage().replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
