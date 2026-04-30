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
import java.util.UUID;
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
    private static final double DEFAULT_ROW_COUNT_DEVIATION_RATIO = 0.30d;

    private final InfraSecretService secretService;
    private final HiveConnectionService hiveConnectionService;
    private final DefaultDestinationSyncService destinationSyncService;
    private final ObjectMapper objectMapper;

    public OdsPrecheckProbeService(
        InfraSecretService secretService,
        HiveConnectionService hiveConnectionService,
        DefaultDestinationSyncService destinationSyncService,
        ObjectMapper objectMapper
    ) {
        this.secretService = secretService;
        this.hiveConnectionService = hiveConnectionService;
        this.destinationSyncService = destinationSyncService;
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
                probeTable(connection, plan, syncMode, incrementalColumn, queryTimeoutSeconds, props, rules);
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

        rules.addAll(probeTarget(source, plans, props));
        return rules;
    }

    private List<OdsPrecheckRuleResult> probeTarget(
        InfraDataSource source,
        List<OdsTablePlanDto> plans,
        Map<String, Object> sourceProps
    ) {
        if (boolProp(sourceProps, "precheckTargetWriteProbeDisabled", false)) {
            return List.of(
                rule(
                    "TARGET_WRITE_PROBE_DISABLED",
                    "INFO",
                    true,
                    source.getName(),
                    "目标端写入预检已按数据源配置跳过",
                    "移除 props.precheckTargetWriteProbeDisabled 后可启用目标 ODS schema 写入权限探测"
                )
            );
        }
        if (destinationSyncService == null) {
            return List.of(
                rule(
                    "TARGET_DESTINATION_CONFIG",
                    "WARN",
                    false,
                    source.getName(),
                    "目标端预检服务不可用",
                    "请确认 DefaultDestinationSyncService 已在平台服务中启用"
                )
            );
        }
        DefaultDestinationSyncService.DefaultDestinationSnapshot destination = destinationSyncService.ensureDefaultDestination();
        if (destination == null || destination.isEmpty()) {
            return List.of(
                rule(
                    "TARGET_DESTINATION_CONFIG",
                    "ERROR",
                    false,
                    source.getName(),
                    "默认目标数据湖未配置",
                    "请先配置默认数据湖目标端，否则生成的同步任务无法落 ODS"
                )
            );
        }

        Map<String, Object> targetConfig = destination.destinationConfig();
        String jdbcUrl = firstNonBlank(
            stringProp(targetConfig, "jdbcUrl"),
            stringProp(targetConfig, "jdbc_url"),
            stringProp(targetConfig, "url"),
            stringProp(targetConfig, "jdbc"),
            stringProp(targetConfig, "jdbcURL")
        );
        String writerType = firstNonBlank(
            destination.destinationDefinitionId(),
            stringProp(targetConfig, "writerType"),
            stringProp(targetConfig, "writer"),
            stringProp(targetConfig, "type")
        );
        if (!StringUtils.hasText(jdbcUrl)) {
            return List.of(
                rule(
                    "TARGET_WRITE_PERMISSION",
                    "WARN",
                    false,
                    firstNonBlank(destination.destinationName(), writerType, source.getName()),
                    "目标端未提供可解析 JDBC URL，无法执行写入权限探测",
                    "如目标端为 JDBC/数据库写入器，请补齐 jdbcUrl；非 JDBC 写入器需提供对应引擎的权限探测适配器"
                )
            );
        }

        int timeoutSeconds = clamp(intProp(sourceProps, "precheckQueryTimeoutSeconds", DEFAULT_QUERY_TIMEOUT_SECONDS), 1, MAX_QUERY_TIMEOUT_SECONDS);
        List<OdsPrecheckRuleResult> rules = new ArrayList<>();
        try (Connection connection = openTargetConnection(jdbcUrl, targetConfig, sourceProps)) {
            rules.add(
                rule(
                    "TARGET_PROBE_CONNECT",
                    "INFO",
                    true,
                    firstNonBlank(destination.destinationName(), writerType, "default-destination"),
                    "目标端 JDBC 连接可用于提交前预检",
                    "继续执行 ODS schema 写入权限探测"
                )
            );
            for (String schema : targetSchemas(plans)) {
                probeTargetSchemaWrite(connection, schema, timeoutSeconds, rules);
            }
        } catch (Exception ex) {
            LOG.debug("ODS target probe failed for {}: {}", destination.destinationName(), ex.getMessage(), ex);
            rules.add(
                rule(
                    "TARGET_PROBE_CONNECT",
                    "ERROR",
                    false,
                    firstNonBlank(destination.destinationName(), writerType, "default-destination"),
                    "目标端 JDBC 连接无法执行提交前预检：" + safeMessage(ex),
                    "请确认默认数据湖目标端 JDBC、凭据、网络和驱动配置"
                )
            );
        }
        return rules;
    }

    private void probeTargetSchemaWrite(
        Connection connection,
        String schema,
        int timeoutSeconds,
        List<OdsPrecheckRuleResult> rules
    ) {
        String normalizedSchema = StringUtils.hasText(schema) ? schema.trim() : "";
        String tempTable = "__dts_precheck_write_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String qualified = qualifiedName(connection, normalizedSchema, tempTable);
        String target = StringUtils.hasText(normalizedSchema) ? normalizedSchema : "default-schema";
        boolean created = false;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.execute("CREATE TABLE " + qualified + " (id integer)");
            created = true;
            rules.add(
                rule(
                    "TARGET_SCHEMA_WRITE_PERMISSION",
                    "ERROR",
                    true,
                    target,
                    "目标 ODS schema 可创建临时预检表：" + target,
                    "目标端具备 ODS 建表权限"
                )
            );
        } catch (SQLException ex) {
            rules.add(
                rule(
                    "TARGET_SCHEMA_WRITE_PERMISSION",
                    "ERROR",
                    false,
                    target,
                    "目标 ODS schema 写入权限探测失败：" + safeMessage(ex),
                    "请创建目标 schema，并授予同步账号 CREATE/ALTER/INSERT 权限"
                )
            );
        } finally {
            if (created) {
                dropQuietly(connection, qualified, timeoutSeconds);
            }
        }
    }

    private void probeTable(
        Connection connection,
        OdsTablePlanDto plan,
        String syncMode,
        String incrementalColumn,
        int timeoutSeconds,
        Map<String, Object> sourceProps,
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
                    "继续校验数据量基线和波动阈值"
                )
            );
            probeRowVolumeBaseline(plan, sourceName, totalRows, sourceProps, rules);
        }

        probePrimaryKey(connection, plan, tableName, sourceName, timeoutSeconds, rules);
        if ("incremental".equalsIgnoreCase(syncMode) && StringUtils.hasText(incrementalColumn)) {
            probeIncrementalColumn(connection, plan, tableName, sourceName, incrementalColumn, totalRows, timeoutSeconds, rules);
        }
    }

    private void probeRowVolumeBaseline(
        OdsTablePlanDto plan,
        String sourceName,
        Long totalRows,
        Map<String, Object> sourceProps,
        List<OdsPrecheckRuleResult> rules
    ) {
        RowCountBaseline baseline = resolveRowCountBaseline(plan, sourceName, sourceProps);
        if (baseline == null || !baseline.configured()) {
            rules.add(
                rule(
                    "SOURCE_ROW_VOLUME_BASELINE",
                    "INFO",
                    true,
                    sourceName,
                    "源表当前行数已记录为基线候选：" + totalRows,
                    "可在数据源 props.precheckRowCountBaselines 中按表配置 min/max/expected/maxDeviationRatio 启用数据量波动告警"
                )
            );
            return;
        }

        RowCountEvaluation evaluation = evaluateRowCountBaseline(totalRows, baseline, sourceProps);
        rules.add(
            rule(
                "SOURCE_ROW_VOLUME_BASELINE",
                "WARN",
                evaluation.passed(),
                sourceName,
                evaluation.message(),
                "请确认源端业务是否存在异常批量变更；如属正常波动，请调整 props.precheckRowCountBaselines 的阈值"
            )
        );
    }

    private RowCountEvaluation evaluateRowCountBaseline(
        Long totalRows,
        RowCountBaseline baseline,
        Map<String, Object> sourceProps
    ) {
        long rows = totalRows == null ? 0L : totalRows;
        Long lower = baseline.minRows();
        Long upper = baseline.maxRows();
        if (baseline.expectedRows() != null) {
            double ratio = baseline.maxDeviationRatio() == null
                ? rowCountDefaultDeviationRatio(sourceProps)
                : sanitizeRatio(baseline.maxDeviationRatio(), DEFAULT_ROW_COUNT_DEVIATION_RATIO);
            long expected = Math.max(0L, baseline.expectedRows());
            long ratioLower = Math.max(0L, (long) Math.floor(expected * (1.0d - ratio)));
            long ratioUpper = Math.max(ratioLower, (long) Math.ceil(expected * (1.0d + ratio)));
            lower = lower == null ? ratioLower : Math.max(lower, ratioLower);
            upper = upper == null ? ratioUpper : Math.min(upper, ratioUpper);
        }

        if (lower != null && rows < lower) {
            return new RowCountEvaluation(
                false,
                "源表行数低于基线下限：当前 " + rows + "，下限 " + lower + "（" + baseline.configSource() + "）"
            );
        }
        if (upper != null && rows > upper) {
            return new RowCountEvaluation(
                false,
                "源表行数高于基线上限：当前 " + rows + "，上限 " + upper + "（" + baseline.configSource() + "）"
            );
        }
        return new RowCountEvaluation(
            true,
            "源表行数处于基线范围内：当前 " + rows + baseline.describeBounds(sourceProps) + "（" + baseline.configSource() + "）"
        );
    }

    private RowCountBaseline resolveRowCountBaseline(
        OdsTablePlanDto plan,
        String sourceName,
        Map<String, Object> sourceProps
    ) {
        if (sourceProps == null || sourceProps.isEmpty()) {
            return RowCountBaseline.empty();
        }
        Object baselines = sourceProps.get("precheckRowCountBaselines");
        if (baselines instanceof Map<?, ?> map) {
            Object tableValue = lookupBaseline(map, sourceName, plan == null ? null : plan.sourceTable());
            RowCountBaseline tableBaseline = parseRowCountBaseline(tableValue, "precheckRowCountBaselines." + sourceName);
            if (tableBaseline.configured()) {
                return tableBaseline;
            }
        }
        RowCountBaseline globalBaseline = new RowCountBaseline(
            longProp(sourceProps, "precheckRowCountMin"),
            longProp(sourceProps, "precheckRowCountMax"),
            longProp(sourceProps, "precheckExpectedRowCount"),
            doubleProp(sourceProps, "precheckRowCountMaxDeviationRatio"),
            "global row-count props"
        );
        return globalBaseline.configured() ? globalBaseline : RowCountBaseline.empty();
    }

    private Object lookupBaseline(Map<?, ?> map, String sourceName, String tableName) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, sourceName);
        addCandidate(candidates, tableName);
        if (StringUtils.hasText(sourceName) && sourceName.contains(".")) {
            addCandidate(candidates, sourceName.substring(sourceName.indexOf('.') + 1));
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
            if (candidates.contains(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (StringUtils.hasText(value)) {
            candidates.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    private RowCountBaseline parseRowCountBaseline(Object value, String configSource) {
        if (value == null) {
            return RowCountBaseline.empty();
        }
        if (value instanceof Number number) {
            return new RowCountBaseline(null, null, number.longValue(), null, configSource);
        }
        if (value instanceof Map<?, ?> map) {
            return new RowCountBaseline(
                longValue(firstMapValue(map, "min", "minimum", "minRows", "lower", "lowerBound")),
                longValue(firstMapValue(map, "max", "maximum", "maxRows", "upper", "upperBound")),
                longValue(firstMapValue(map, "expected", "baseline", "expectedRows", "baselineRows")),
                doubleValue(firstMapValue(map, "maxDeviationRatio", "deviationRatio", "maxDeviationPct", "deviationPct")),
                configSource
            );
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return RowCountBaseline.empty();
        }
        if (text.contains("..") || text.contains(":")) {
            String delimiter = text.contains("..") ? "\\.\\." : ":";
            String[] parts = text.split(delimiter, -1);
            Long min = parts.length > 0 ? longValue(parts[0]) : null;
            Long max = parts.length > 1 ? longValue(parts[1]) : null;
            return new RowCountBaseline(min, max, null, null, configSource);
        }
        return new RowCountBaseline(null, null, longValue(text), null, configSource);
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString().trim())) {
                    return entry.getValue();
                }
            }
        }
        return null;
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

    private Connection openTargetConnection(String jdbcUrl, Map<String, Object> targetConfig, Map<String, Object> sourceProps) throws SQLException {
        Properties jdbcProps = new Properties();
        String username = firstNonBlank(stringProp(targetConfig, "username"), stringProp(targetConfig, "user"));
        String password = stringProp(targetConfig, "password");
        if (StringUtils.hasText(username)) {
            jdbcProps.setProperty("user", username);
        }
        if (StringUtils.hasText(password)) {
            jdbcProps.setProperty("password", password);
        }
        Object jdbcProperties = targetConfig.get("jdbcProperties");
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
            String driverClass = firstNonBlank(
                stringProp(targetConfig, "driverClass"),
                stringProp(targetConfig, "driver_class"),
                inferDriverClass(jdbcUrl)
            );
            if (StringUtils.hasText(driverClass)) {
                try {
                    if (jdbcLoader != null) {
                        Class.forName(driverClass, true, jdbcLoader);
                    } else {
                        Class.forName(driverClass);
                    }
                } catch (Throwable ex) {
                    LOG.debug("Failed to load JDBC driver class {} for ODS target precheck: {}", driverClass, ex.getMessage());
                }
            }
            try {
                DriverManager.setLoginTimeout(clamp(intProp(sourceProps, "precheckLoginTimeoutSeconds", DEFAULT_LOGIN_TIMEOUT_SECONDS), 1, MAX_QUERY_TIMEOUT_SECONDS));
            } catch (Throwable ignored) {}
            return DriverManager.getConnection(jdbcUrl.trim(), jdbcProps);
        } finally {
            Thread.currentThread().setContextClassLoader(previousCl);
        }
    }

    private void dropQuietly(Connection connection, String qualifiedTable, int timeoutSeconds) {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            statement.execute("DROP TABLE " + qualifiedTable);
        } catch (SQLException ex) {
            LOG.warn("Failed to cleanup ODS target precheck table {}: {}", qualifiedTable, ex.getMessage());
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

    private List<String> targetSchemas(List<OdsTablePlanDto> plans) {
        Set<String> schemas = new LinkedHashSet<>();
        if (plans != null) {
            for (OdsTablePlanDto plan : plans) {
                if (plan != null && StringUtils.hasText(plan.odsSchema())) {
                    schemas.add(plan.odsSchema().trim());
                }
            }
        }
        return schemas.isEmpty() ? List.of("") : List.copyOf(schemas);
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

    private Long longProp(Map<String, Object> map, String key) {
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        return longValue(map.get(key));
    }

    private Double doubleProp(Map<String, Object> map, String key) {
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        return doubleValue(map.get(key));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            try {
                return (long) Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double rowCountDefaultDeviationRatio(Map<String, Object> sourceProps) {
        return sanitizeRatio(
            doubleProp(sourceProps, "precheckRowCountDefaultDeviationRatio"),
            DEFAULT_ROW_COUNT_DEVIATION_RATIO
        );
    }

    private double sanitizeRatio(Double ratio, double fallback) {
        if (ratio == null || ratio.isNaN() || ratio.isInfinite() || ratio < 0d) {
            return fallback;
        }
        if (ratio > 1d) {
            return ratio / 100d;
        }
        return ratio;
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

    private record RowCountBaseline(
        Long minRows,
        Long maxRows,
        Long expectedRows,
        Double maxDeviationRatio,
        String configSource
    ) {
        static RowCountBaseline empty() {
            return new RowCountBaseline(null, null, null, null, null);
        }

        boolean configured() {
            return minRows != null || maxRows != null || expectedRows != null;
        }

        String describeBounds(Map<String, Object> sourceProps) {
            List<String> bounds = new ArrayList<>();
            if (minRows != null) {
                bounds.add("下限 " + minRows);
            }
            if (maxRows != null) {
                bounds.add("上限 " + maxRows);
            }
            if (expectedRows != null) {
                double ratio = maxDeviationRatio == null
                    ? DEFAULT_ROW_COUNT_DEVIATION_RATIO
                    : maxDeviationRatio > 1d ? maxDeviationRatio / 100d : maxDeviationRatio;
                if (sourceProps != null && maxDeviationRatio == null) {
                    Object override = sourceProps.get("precheckRowCountDefaultDeviationRatio");
                    if (override instanceof Number number) {
                        ratio = number.doubleValue() > 1d ? number.doubleValue() / 100d : number.doubleValue();
                    } else if (override != null) {
                        try {
                            double parsed = Double.parseDouble(override.toString().trim());
                            ratio = parsed > 1d ? parsed / 100d : parsed;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                bounds.add("期望 " + expectedRows + "±" + Math.round(ratio * 100d) + "%");
            }
            return bounds.isEmpty() ? "" : "（" + String.join("，", bounds) + "）";
        }
    }

    private record RowCountEvaluation(boolean passed, String message) {}

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
