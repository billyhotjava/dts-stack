package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AddaxJobService {

    private static final Logger LOG = LoggerFactory.getLogger(AddaxJobService.class);
    private static final String ADDAX_CONTAINER_DIR = "/opt/addax/jobs";
    private static final String TABLE_PLACEHOLDER = "${table}";
    private static final List<String> CONNECTION_OVERRIDE_KEYS = List.of(
        "jdbcUrl",
        "url",
        "host",
        "port",
        "username",
        "password",
        "database",
        "db",
        "driver",
        "driverClass",
        "driverVersion",
        "jdbcProperties",
        "connection"
    );
    private static final Map<String, String> JDBC_PREFIX_DRIVERS = Map.ofEntries(
        Map.entry("jdbc:dm:", "dm.jdbc.driver.DmDriver"),
        Map.entry("jdbc:postgresql:", "org.postgresql.Driver"),
        Map.entry("jdbc:mysql:", "com.mysql.cj.jdbc.Driver"),
        Map.entry("jdbc:mariadb:", "org.mariadb.jdbc.Driver"),
        Map.entry("jdbc:oracle:", "oracle.jdbc.OracleDriver"),
        Map.entry("jdbc:sqlserver:", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
        Map.entry("jdbc:clickhouse:", "com.clickhouse.jdbc.ClickHouseDriver"),
        Map.entry("jdbc:hive2:", "org.apache.hive.jdbc.HiveDriver"),
        Map.entry("jdbc:db2:", "com.ibm.db2.jcc.DB2Driver"),
        Map.entry("jdbc:sqlite:", "org.sqlite.JDBC")
    );

    private final AddaxProperties properties;
    private final IngestionSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public AddaxJobService(AddaxProperties properties, IngestionSettingsService settingsService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public record AddaxJobResult(String jobName, String jobPath, Map<String, Object> jobConfig) {}

    public AddaxJobResult createJob(
        String taskName,
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig
    ) {
        Map<String, Object> resolvedJob = resolveJobConfig(readerType, readerConfig, writerType, writerConfig, jobConfig);
        String jobDir = resolveJobDir();
        String jobName = buildJobName(taskName);
        Path dir = Paths.get(jobDir);
        try {
            Files.createDirectories(dir);
            Path jobPath = dir.resolve(jobName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), resolvedJob);
            return new AddaxJobResult(jobName, jobPath.toString(), resolvedJob);
        } catch (Exception ex) {
            LOG.warn("Failed to write Addax job {}: {}", jobName, ex.getMessage());
            throw new IllegalStateException("生成 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> resolveJobConfig(
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig
    ) {
        if (jobConfig != null && !jobConfig.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>(jobConfig);
            normalizeAddaxJobConfig(normalized);
            return normalized;
        }
        if (!StringUtils.hasText(readerType) || !StringUtils.hasText(writerType)) {
            throw new IllegalArgumentException("缺少 Addax Reader/Writer 类型");
        }
        Map<String, Object> resolvedReader = ensureDriver(readerType, safeMap(readerConfig));
        Map<String, Object> resolvedWriter = ensureDriver(writerType, safeMap(writerConfig));
        replaceWriterTablePlaceholders(resolvedReader, resolvedWriter);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reader", Map.of("name", readerType, "parameter", resolvedReader));
        content.put("writer", Map.of("name", writerType, "parameter", resolvedWriter));

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("setting", Map.of("speed", Map.of("channel", 1)));
        job.put("content", List.of(content));
        return Map.of("job", job);
    }

    private String resolveJobDir() {
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
        String jobDir = settings.getString("jobDir", properties.getJobDir());
        if (!StringUtils.hasText(jobDir)) {
            throw new IllegalStateException("未配置 Addax 作业目录");
        }
        return jobDir.trim();
    }

    private String buildJobName(String taskName) {
        String base = StringUtils.hasText(taskName) ? taskName.trim().toLowerCase(Locale.ROOT) : "addax-job";
        base = base.replaceAll("[^a-z0-9-_]+", "-");
        if (!StringUtils.hasText(base)) {
            base = "addax-job";
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return base + "-" + suffix + ".json";
    }

    public String toContainerJobPath(String jobPath) {
        if (!StringUtils.hasText(jobPath)) {
            return jobPath;
        }
        String normalized = jobPath.trim();
        if (normalized.startsWith(ADDAX_CONTAINER_DIR + "/")) {
            return normalized;
        }
        Path path = Paths.get(normalized);
        Path filename = path.getFileName();
        if (filename == null) {
            return normalized;
        }
        return ADDAX_CONTAINER_DIR + "/" + filename;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private void normalizeAddaxJobConfig(Map<String, Object> jobConfig) {
        if (jobConfig == null || jobConfig.isEmpty()) {
            return;
        }
        Object jobObj = jobConfig.get("job");
        if (!(jobObj instanceof Map<?, ?> jobMap)) {
            return;
        }
        Object contentObj = jobMap.get("content");
        if (!(contentObj instanceof List<?> contentList)) {
            return;
        }
        for (Object item : contentList) {
            if (item instanceof Map<?, ?> contentMap) {
                Map<String, Object> readerParams = normalizeReaderWriter(contentMap, "reader");
                Map<String, Object> writerParams = normalizeReaderWriter(contentMap, "writer");
                replaceWriterTablePlaceholders(readerParams, writerParams);
            }
        }
    }

    private Map<String, Object> normalizeReaderWriter(Map<?, ?> contentMap, String key) {
        Object nodeObj = contentMap.get(key);
        if (!(nodeObj instanceof Map<?, ?> nodeMap)) {
            return null;
        }
        String pluginType = normalizeText(nodeMap.get("name"));
        Object paramObj = nodeMap.get("parameter");
        if (!(paramObj instanceof Map<?, ?> paramMap)) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        paramMap.forEach((k, v) -> {
            if (k != null) {
                params.put(k.toString(), v);
            }
        });
        ensureDriver(pluginType, params);
        if ("writer".equalsIgnoreCase(key) && StringUtils.hasText(pluginType) && !StringUtils.hasText(normalizeText(params.get("writerType")))) {
            params.put("writerType", pluginType);
        }
        if (nodeMap instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) nodeMap;
            mutable.put("parameter", params);
        }
        return params;
    }

    private Map<String, Object> ensureDriver(String pluginType, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return config;
        }
        normalizeJdbcUrl(pluginType, config);
        String existing = normalizeText(config.get("driver"));
        if (StringUtils.hasText(existing)) {
            return config;
        }
        String driverClass = normalizeText(config.get("driverClass"));
        if (StringUtils.hasText(driverClass)) {
            config.put("driver", driverClass);
            return config;
        }
        String jdbcUrl = resolveJdbcUrl(config);
        String resolved = resolveDriverClass(jdbcUrl, pluginType);
        if (StringUtils.hasText(resolved)) {
            config.put("driver", resolved);
        }
        return config;
    }

    private void normalizeJdbcUrl(String pluginType, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        boolean preferList = !isWriter(pluginType);
        normalizeJdbcUrlField(config, "jdbcUrl", preferList);
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            normalizeJdbcUrlField(map, "jdbcUrl", preferList);
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    normalizeJdbcUrlField(entryMap, "jdbcUrl", preferList);
                }
            }
        }
    }

    private void normalizeJdbcUrlField(Map<?, ?> map, String key, boolean preferList) {
        if (map == null || !map.containsKey(key)) {
            return;
        }
        Object raw = map.get(key);
        Object normalized = normalizeJdbcUrlValue(raw, preferList);
        if (normalized != raw && map instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.put(key, normalized);
        }
    }

    private Object normalizeJdbcUrlValue(Object raw, boolean preferList) {
        if (raw == null) {
            return raw;
        }
        if (raw instanceof List<?> list) {
            List<String> cleaned = list.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .toList();
            if (cleaned.isEmpty()) {
                return raw;
            }
            return preferList ? cleaned : cleaned.get(0);
        }
        if (raw instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                try {
                    List<String> parsed = objectMapper.readValue(
                        trimmed,
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}
                    );
                    List<String> cleaned = parsed.stream()
                        .map(this::normalizeText)
                        .filter(StringUtils::hasText)
                        .toList();
                    if (!cleaned.isEmpty()) {
                        return preferList ? cleaned : cleaned.get(0);
                    }
                } catch (Exception ex) {
                    LOG.debug("Failed to parse jdbcUrl array: {}", ex.getMessage());
                }
            }
            String url = normalizeText(trimmed);
            if (StringUtils.hasText(url) && preferList) {
                return List.of(url);
            }
            return url;
        }
        return raw;
    }

    private boolean isWriter(String pluginType) {
        if (!StringUtils.hasText(pluginType)) {
            return false;
        }
        return pluginType.toLowerCase(Locale.ROOT).contains("writer");
    }

    private String resolveJdbcUrl(Map<String, Object> config) {
        Object direct = config.get("jdbcUrl");
        String url = firstStringValue(direct);
        if (StringUtils.hasText(url)) {
            return url;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> connMap) {
            Object connUrl = connMap.get("jdbcUrl");
            return firstStringValue(connUrl);
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    String candidate = firstStringValue(entryMap.get("jdbcUrl"));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private String firstStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return normalizeText(str);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = normalizeText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return normalizeText(value);
    }

    private String resolveDriverClass(String jdbcUrl, String pluginType) {
        String url = normalizeText(jdbcUrl);
        if (StringUtils.hasText(url)) {
            String lower = url.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : JDBC_PREFIX_DRIVERS.entrySet()) {
                if (lower.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        String type = normalizeText(pluginType);
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String lowerType = type.toLowerCase(Locale.ROOT);
        if (lowerType.contains("dm")) {
            return "dm.jdbc.driver.DmDriver";
        }
        if (lowerType.contains("postgres")) {
            return "org.postgresql.Driver";
        }
        if (lowerType.contains("mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lowerType.contains("mariadb")) {
            return "org.mariadb.jdbc.Driver";
        }
        if (lowerType.contains("oracle")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lowerType.contains("sqlserver") || lowerType.contains("mssql")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (lowerType.contains("clickhouse")) {
            return "com.clickhouse.jdbc.ClickHouseDriver";
        }
        if (lowerType.contains("hive")) {
            return "org.apache.hive.jdbc.HiveDriver";
        }
        if (lowerType.contains("db2")) {
            return "com.ibm.db2.jcc.DB2Driver";
        }
        if (lowerType.contains("sqlite")) {
            return "org.sqlite.JDBC";
        }
        return null;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private void replaceWriterTablePlaceholders(Map<String, Object> readerConfig, Map<String, Object> writerConfig) {
        if (readerConfig == null || writerConfig == null) {
            return;
        }
        List<String> sourceTables = extractTables(readerConfig);
        if (sourceTables.isEmpty()) {
            return;
        }
        boolean hadPlaceholder = hasTablePlaceholderInConfig(writerConfig);
        replaceTableField(writerConfig, sourceTables);
        Object connection = writerConfig.get("connection");
        if (connection instanceof Map<?, ?> map) {
            replaceTableField(map, sourceTables);
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    replaceTableField(entryMap, sourceTables);
                }
            }
        }
        if (hadPlaceholder) {
            List<String> resolvedTargets = extractTables(writerConfig);
            LOG.info(
                "Resolved Addax writer table placeholder: sources={}, targets={}",
                summarizeTables(sourceTables),
                summarizeTables(resolvedTargets)
            );
        }
    }

    private boolean hasTablePlaceholderInConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        return extractTables(config).stream()
            .anyMatch(table -> table != null && table.contains(TABLE_PLACEHOLDER));
    }

    private String summarizeTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return "[]";
        }
        int max = 10;
        if (tables.size() <= max) {
            return tables.toString();
        }
        return tables.subList(0, max).toString() + "...(" + tables.size() + ")";
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> direct = extractTableValues(config.get("table"));
        if (!direct.isEmpty()) {
            return direct;
        }
        List<String> directAlt = extractTableValues(config.get("tables"));
        if (!directAlt.isEmpty()) {
            return directAlt;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            List<String> values = extractTableValues(map.get("table"));
            if (!values.isEmpty()) {
                return values;
            }
            List<String> altValues = extractTableValues(map.get("tables"));
            if (!altValues.isEmpty()) {
                return altValues;
            }
        } else if (connection instanceof List<?> list) {
            java.util.LinkedHashSet<String> collected = new java.util.LinkedHashSet<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    collected.addAll(extractTableValues(entryMap.get("table")));
                    collected.addAll(extractTableValues(entryMap.get("tables")));
                }
            }
            if (!collected.isEmpty()) {
                return List.copyOf(collected);
            }
        }
        return List.of();
    }

    private List<String> extractTableValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String str) {
            String normalized = normalizeText(str);
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.LinkedHashSet<String> collected = new java.util.LinkedHashSet<>();
            for (Object entry : iterable) {
                String normalized = normalizeText(entry);
                if (StringUtils.hasText(normalized)) {
                    collected.add(normalized);
                }
            }
            return List.copyOf(collected);
        }
        return List.of();
    }

    private void replaceTableField(Map<?, ?> map, List<String> sourceTables) {
        if (map == null || !map.containsKey("table")) {
            return;
        }
        Object raw = map.get("table");
        List<String> expanded = expandTableValue(raw, sourceTables);
        if (expanded == null || expanded.isEmpty()) {
            return;
        }
        if (map instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.put("table", expanded);
        }
    }

    private List<String> expandTableValue(Object raw, List<String> sourceTables) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String str) {
            return expandTableTemplate(str, sourceTables);
        }
        if (raw instanceof Iterable<?> iterable) {
            java.util.LinkedHashSet<String> expanded = new java.util.LinkedHashSet<>();
            for (Object entry : iterable) {
                if (entry == null) {
                    continue;
                }
                expanded.addAll(expandTableTemplate(entry.toString(), sourceTables));
            }
            return List.copyOf(expanded);
        }
        return List.of();
    }

    private List<String> expandTableTemplate(String template, List<String> sourceTables) {
        String normalized = normalizeText(template);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        if (!normalized.contains(TABLE_PLACEHOLDER)) {
            return List.of(normalized);
        }
        java.util.LinkedHashSet<String> resolved = new java.util.LinkedHashSet<>();
        for (String source : sourceTables) {
            if (!StringUtils.hasText(source)) {
                continue;
            }
            resolved.add(normalized.replace(TABLE_PLACEHOLDER, source));
        }
        return List.copyOf(resolved);
    }

    /**
     * 从IngestionTask实体创建Addax Job
     */
    public AddaxJobResult createJobFromTask(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        return createJobFromTask(task, null, null);
    }

    public AddaxJobResult createJobFromTask(
        com.yuzhi.dts.ingestion.domain.IngestionTask task,
        String readerTypeOverride,
        Map<String, Object> readerConfigOverride
    ) {
        if (task == null) {
            throw new IllegalArgumentException("IngestionTask cannot be null");
        }

        // 将JsonNode转换为Map
        Map<String, Object> readerConfig = readerConfigOverride != null
            ? mergeReaderConfig(readerConfigOverride, task.getSourceConfig())
            : jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null
            ? jsonNodeToMap(task.getDestinationConfig())
            : Map.of();
        applyTableMapping(task.getTableMapping(), readerConfig, writerConfig);
        Map<String, Object> jobConfig = task.getAddaxConfig() != null 
            ? jsonNodeToMap(task.getAddaxConfig()) 
            : null;

        return createJob(
            task.getName(),
            StringUtils.hasText(readerTypeOverride) ? readerTypeOverride : task.getSourceType(),
            readerConfig,
            task.getDestinationType() != null ? task.getDestinationType() : "postgresqlwriter",
            writerConfig,
            jobConfig
        );
    }

    private Map<String, Object> mergeReaderConfig(Map<String, Object> baseConfig, JsonNode overrideNode) {
        Map<String, Object> merged = baseConfig == null ? new LinkedHashMap<>() : new LinkedHashMap<>(baseConfig);
        if (overrideNode == null || overrideNode.isNull()) {
            return merged;
        }
        Map<String, Object> overrides = sanitizeReaderOverrides(jsonNodeToMap(overrideNode));
        if (overrides.isEmpty()) {
            return merged;
        }
        List<String> tables = extractTables(overrides);
        overrides.remove("table");
        overrides.remove("tables");
        overrides.remove("connection");
        merged.putAll(overrides);
        if (!tables.isEmpty()) {
            applyTables(merged, tables);
        }
        return merged;
    }

    private Map<String, Object> sanitizeReaderOverrides(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (isConnectionOverrideKey(key)) {
                continue;
            }
            if ("connection".equalsIgnoreCase(key)) {
                continue;
            }
            sanitized.put(key, entry.getValue());
        }
        List<String> tables = extractTables(overrides);
        if (!tables.isEmpty()) {
            sanitized.put("table", tables);
        }
        return sanitized;
    }

    private boolean isConnectionOverrideKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        for (String candidate : CONNECTION_OVERRIDE_KEYS) {
            if (candidate.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private void applyTables(Map<String, Object> config, List<String> tables) {
        if (config == null || tables == null || tables.isEmpty()) {
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTableField(map, tables);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTableField(entryMap, tables);
                }
            }
            return;
        }
        config.put("table", tables);
    }

    public boolean isDriverMissing(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        if (task == null) {
            return false;
        }
        if (task.getAddaxConfig() != null && !task.getAddaxConfig().isNull()) {
            return false;
        }
        Map<String, Object> readerConfig = jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null
            ? jsonNodeToMap(task.getDestinationConfig())
            : Map.of();
        return requiresDriver(task.getSourceType(), readerConfig) || requiresDriver(task.getDestinationType(), writerConfig);
    }

    public boolean isJdbcUrlMalformed(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        if (task == null) {
            return false;
        }
        if (task.getAddaxConfig() != null && !task.getAddaxConfig().isNull()) {
            Map<String, Object> config = jsonNodeToMap(task.getAddaxConfig());
            return hasMalformedJdbcUrlInJob(config);
        }
        Map<String, Object> readerConfig = jsonNodeToMap(task.getSourceConfig());
        Map<String, Object> writerConfig = task.getDestinationConfig() != null
            ? jsonNodeToMap(task.getDestinationConfig())
            : Map.of();
        return hasMalformedJdbcUrl(readerConfig) || hasMalformedJdbcUrl(writerConfig);
    }

    public boolean needsJobRebuild(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        return isDriverMissing(task) || isJdbcUrlMalformed(task);
    }

    public boolean isJobConfigMalformed(String jobPath) {
        if (!StringUtils.hasText(jobPath)) {
            return false;
        }
        return isJobConfigMalformed(Paths.get(jobPath));
    }

    public boolean isJobConfigMalformed(Path jobPath) {
        if (jobPath == null || !Files.exists(jobPath)) {
            return false;
        }
        try {
            Map<String, Object> jobConfig = objectMapper.readValue(
                jobPath.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            return hasMalformedJdbcUrlInJob(jobConfig) || hasTablePlaceholderInJob(jobConfig);
        } catch (Exception ex) {
            LOG.debug("Failed to parse Addax job {}: {}", jobPath, ex.getMessage());
            return true;
        }
    }

    private boolean hasMalformedJdbcUrlInJob(Map<String, Object> jobConfig) {
        if (jobConfig == null || jobConfig.isEmpty()) {
            return false;
        }
        Object jobObj = jobConfig.get("job");
        if (!(jobObj instanceof Map<?, ?> jobMap)) {
            return false;
        }
        Object contentObj = jobMap.get("content");
        if (!(contentObj instanceof List<?> contentList)) {
            return false;
        }
        for (Object item : contentList) {
            if (item instanceof Map<?, ?> contentMap) {
                Object readerObj = contentMap.get("reader");
                if (readerObj instanceof Map<?, ?> readerMap && hasMalformedJdbcUrlFromNode(readerMap, false)) {
                    return true;
                }
                Object writerObj = contentMap.get("writer");
                if (writerObj instanceof Map<?, ?> writerMap && hasMalformedJdbcUrlFromNode(writerMap, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTablePlaceholderInJob(Map<String, Object> jobConfig) {
        if (jobConfig == null || jobConfig.isEmpty()) {
            return false;
        }
        Object jobObj = jobConfig.get("job");
        if (!(jobObj instanceof Map<?, ?> jobMap)) {
            return false;
        }
        Object contentObj = jobMap.get("content");
        if (!(contentObj instanceof List<?> contentList)) {
            return false;
        }
        for (Object item : contentList) {
            if (item instanceof Map<?, ?> contentMap) {
                Object writerObj = contentMap.get("writer");
                if (writerObj instanceof Map<?, ?> writerMap && hasTablePlaceholder(writerMap)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTablePlaceholder(Map<?, ?> nodeMap) {
        Object paramObj = nodeMap.get("parameter");
        if (!(paramObj instanceof Map<?, ?> paramMap)) {
            return false;
        }
        Object tableObj = paramMap.get("table");
        if (tableObj instanceof String str) {
            return str.contains(TABLE_PLACEHOLDER);
        }
        if (tableObj instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null && entry.toString().contains(TABLE_PLACEHOLDER)) {
                    return true;
                }
            }
        }
        Object connection = paramMap.get("connection");
        if (connection instanceof Iterable<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    Object table = entryMap.get("table");
                    if (table instanceof String t && t.contains(TABLE_PLACEHOLDER)) {
                        return true;
                    }
                    if (table instanceof Iterable<?> tables) {
                        for (Object t : tables) {
                            if (t != null && t.toString().contains(TABLE_PLACEHOLDER)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasMalformedJdbcUrlFromNode(Map<?, ?> nodeMap, boolean writer) {
        Object paramObj = nodeMap.get("parameter");
        if (!(paramObj instanceof Map<?, ?> paramMap)) {
            return false;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        paramMap.forEach((k, v) -> {
            if (k != null) {
                params.put(k.toString(), v);
            }
        });
        if (writer && hasWriterJdbcUrlList(params)) {
            return true;
        }
        return hasMalformedJdbcUrl(params);
    }

    private boolean hasMalformedJdbcUrl(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        return isMalformedJdbcUrlValue(config.get("jdbcUrl"))
            || isMalformedJdbcUrlValue(extractConnectionJdbcUrl(config));
    }

    private boolean hasWriterJdbcUrlList(Map<String, Object> config) {
        Object direct = config.get("jdbcUrl");
        if (direct instanceof List<?>) {
            return true;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return map.get("jdbcUrl") instanceof List<?>;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap && entryMap.get("jdbcUrl") instanceof List<?>) {
                    return true;
                }
            }
        }
        return false;
    }

    private Object extractConnectionJdbcUrl(Map<String, Object> config) {
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return map.get("jdbcUrl");
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    Object url = entryMap.get("jdbcUrl");
                    if (url != null) {
                        return url;
                    }
                }
            }
        }
        return null;
    }

    private boolean isMalformedJdbcUrlValue(Object value) {
        if (!(value instanceof String str)) {
            return false;
        }
        String trimmed = str.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private boolean requiresDriver(String pluginType, Map<String, Object> config) {
        if (!StringUtils.hasText(pluginType) || config == null || config.isEmpty()) {
            return false;
        }
        String type = pluginType.toLowerCase(Locale.ROOT);
        boolean rdbms = type.contains("rdbms") || type.contains("jdbc") || type.contains("sql");
        if (!rdbms) {
            return false;
        }
        if (StringUtils.hasText(normalizeText(config.get("driver"))) || StringUtils.hasText(normalizeText(config.get("driverClass")))) {
            return false;
        }
        return StringUtils.hasText(resolveJdbcUrl(config));
    }

    /**
     * 保存Job JSON到指定路径（用于更新已有任务）
     */
    public String saveJobJson(String jobJson, Long taskId) {
        String jobDir = resolveJobDir();
        String jobName = "task_" + taskId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".json";
        Path dir = Paths.get(jobDir);
        try {
            Files.createDirectories(dir);
            Path jobPath = dir.resolve(jobName);
            Files.writeString(jobPath, jobJson);
            return jobPath.toString();
        } catch (Exception ex) {
            LOG.warn("Failed to save job JSON for task {}: {}", taskId, ex.getMessage());
            throw new IllegalStateException("保存 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 将JsonNode转换为Map
     */
    private Map<String, Object> jsonNodeToMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to convert JsonNode to Map: {}", e.getMessage());
            return Map.of();
        }
    }

    private void applyTableMapping(JsonNode tableMapping, Map<String, Object> readerConfig, Map<String, Object> writerConfig) {
        if (readerConfig == null || writerConfig == null) {
            return;
        }
        List<TableMapping> mappings = parseTableMapping(tableMapping);
        if (!mappings.isEmpty()) {
            List<String> sourceTables = mappings.stream()
                .map(TableMapping::source)
                .filter(StringUtils::hasText)
                .toList();
            List<String> targetTables = mappings.stream()
                .map(TableMapping::target)
                .filter(StringUtils::hasText)
                .toList();
            if (!sourceTables.isEmpty()) {
                setTables(readerConfig, sourceTables);
            }
            if (!targetTables.isEmpty()) {
                setTables(writerConfig, applySchemaPrefix(targetTables, resolveSchema(writerConfig)));
            }
            return;
        }
        List<String> sourceTables = extractTables(readerConfig);
        if (sourceTables.isEmpty()) {
            return;
        }
        List<String> targetTables = extractTables(writerConfig);
        if (targetTables.isEmpty()) {
            String prefix = resolveTablePrefix(writerConfig);
            targetTables = sourceTables.stream()
                .map(table -> StringUtils.hasText(prefix) ? prefix + table : table)
                .toList();
            setTables(writerConfig, targetTables);
        }
        if (!targetTables.isEmpty()) {
            setTables(writerConfig, applySchemaPrefix(targetTables, resolveSchema(writerConfig)));
        }
    }

    private List<TableMapping> parseTableMapping(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        try {
            List<TableMapping> mappings = objectMapper.convertValue(
                node,
                new com.fasterxml.jackson.core.type.TypeReference<List<TableMapping>>() {}
            );
            if (mappings == null) {
                return List.of();
            }
            return mappings.stream()
                .filter(mapping -> StringUtils.hasText(mapping.source()) || StringUtils.hasText(mapping.target()))
                .toList();
        } catch (Exception ex) {
            LOG.debug("Failed to parse table mapping: {}", ex.getMessage());
            return List.of();
        }
    }

    private void setTables(Map<String, Object> config, List<String> tables) {
        if (config == null || tables == null || tables.isEmpty()) {
            return;
        }
        if (config.containsKey("table")) {
            config.put("table", tables);
            return;
        }
        if (config.containsKey("tables")) {
            config.put("tables", tables);
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTableField(map, tables);
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTableField(entryMap, tables);
                }
            }
        } else {
            config.put("table", tables);
        }
    }

    private void setTableField(Map<?, ?> map, List<String> tables) {
        if (map == null) {
            return;
        }
        if (map instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.put("table", tables);
        }
    }

    private String resolveSchema(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String schema = normalizeText(config.get("schema"));
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        Object schemas = config.get("schemas");
        if (schemas instanceof List<?> list && !list.isEmpty()) {
            String candidate = normalizeText(list.get(0));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            String candidate = normalizeText(map.get("schema"));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    String candidate = normalizeText(entryMap.get("schema"));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private String resolveTablePrefix(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String prefix = normalizeText(config.get("tablePrefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        prefix = normalizeText(config.get("prefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        return normalizeText(config.get("targetPrefix"));
    }

    private List<String> applySchemaPrefix(List<String> tables, String schema) {
        if (!StringUtils.hasText(schema) || tables == null || tables.isEmpty()) {
            return tables;
        }
        String normalized = schema.trim();
        return tables.stream()
            .map(table -> {
                String name = normalizeText(table);
                if (!StringUtils.hasText(name) || name.contains(".")) {
                    return table;
                }
                return normalized + "." + name;
            })
            .toList();
    }

    private record TableMapping(String source, String target) {}
}
