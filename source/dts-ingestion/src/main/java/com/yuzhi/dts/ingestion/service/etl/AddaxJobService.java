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
    private final JdbcMetadataService jdbcMetadataService;

    public AddaxJobService(AddaxProperties properties, IngestionSettingsService settingsService, ObjectMapper objectMapper, JdbcMetadataService jdbcMetadataService) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.jdbcMetadataService = jdbcMetadataService;
    }

    public record AddaxJobResult(String jobName, String jobPath, Map<String, Object> jobConfig) {}

    public record PerTableJob(String tableName, String containerJobPath, String hostJobPath) {}

    public AddaxJobResult createJob(
        String taskName,
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig
    ) {
        return createJob(taskName, readerType, readerConfig, writerType, writerConfig, jobConfig, null);
    }

    public AddaxJobResult createJob(
        String taskName,
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig,
        Map<String, Object> jobConfig,
        String syncMode
    ) {
        Map<String, Object> resolvedJob = resolveJobConfig(readerType, readerConfig, writerType, writerConfig, jobConfig);
        if ("full_refresh".equalsIgnoreCase(syncMode)) {
            applyFullRefreshPreSql(resolvedJob);
        }
        String jobDir = resolveJobDir();
        String jobName = buildJobName(taskName);
        Path dir = Paths.get(jobDir);
        try {
            if (Files.exists(dir) && !Files.isDirectory(dir)) {
                throw new IllegalStateException("Addax 作业目录不是有效目录: " + jobDir);
            }
            Files.createDirectories(dir);
            if (!Files.isWritable(dir)) {
                throw new IllegalStateException("Addax 作业目录不可写: " + jobDir);
            }
            Path jobPath = dir.resolve(jobName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jobPath.toFile(), resolvedJob);
            return new AddaxJobResult(jobName, jobPath.toString(), resolvedJob);
        } catch (Exception ex) {
            LOG.warn("Failed to write Addax job {}: {}", jobName, ex.getMessage());
            throw new IllegalStateException("生成 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    private static final List<String> FILE_METADATA_KEYS = List.of(
        "_filePath", "_containerPath", "_fileType", "_fileColumns", "_originalName", "_autoId"
    );

    private static final List<String> COLUMN_RULE_KEYS = List.of(
        "_columnPrefix", "_columnSuffix", "_extraColumns"
    );
    private static final String EXTRA_COL_SOURCE_SYSTEM = "source_system";
    private static final String EXTRA_COL_IMPORT_TIME = "import_time";

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
            applyJobDefaults(normalized, readerType, readerConfig, writerType, writerConfig);
            return normalized;
        }
        readerType = normalizePluginName(readerType, "reader");
        writerType = normalizePluginName(writerType, "writer");
        if (!StringUtils.hasText(readerType) || !StringUtils.hasText(writerType)) {
            throw new IllegalArgumentException("缺少 Addax Reader/Writer 类型");
        }
        Map<String, Object> resolvedReader = safeMap(readerConfig);
        // Capture file columns before metadata keys are stripped
        List<Map<String, Object>> fileColumns = isFileReaderType(readerType) ? extractFileColumns(readerConfig) : List.of();
        boolean fileAutoId = isFileReaderType(readerType) && resolveFileAutoId(readerConfig);
        if (isFileReaderType(readerType)) {
            ensureFileReaderConfig(readerType, resolvedReader);
            stripFileMetadataKeys(resolvedReader);
        } else {
            resolvedReader = ensureDriver(readerType, resolvedReader);
        }
        Map<String, Object> resolvedWriter = ensureDriver(writerType, safeMap(writerConfig));
        ensureWriterConnection(writerType, resolvedWriter);
        ensureDefaultExtraColumns(readerConfig, resolvedReader, resolvedWriter, readerType, writerType);
        if (isFileReaderType(readerType) && !fileColumns.isEmpty()) {
            injectFileSourceCreateTablePreSql(resolvedWriter, fileColumns, fileAutoId, writerType);
        }
        if (!isFileReaderType(readerType)) {
            replaceWriterTablePlaceholders(resolvedReader, resolvedWriter);
        }

        // Inject extra columns via postSql (ALTER TABLE + UPDATE) if configured
        injectExtraColumnsPostSql(resolvedWriter, writerType);

        // Strip column rule metadata keys from writer config
        for (String key : COLUMN_RULE_KEYS) {
            resolvedWriter.remove(key);
        }

        // Split into per-table content blocks to avoid Addax multi-table writer bugs
        List<Map<String, Object>> contentList = splitPerTable(readerType, resolvedReader, writerType, resolvedWriter);

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("setting", Map.of("speed", Map.of("channel", 1)));
        job.put("content", contentList);
        return Map.of("job", job);
    }

    /**
     * Split a multi-table reader/writer pair into per-table content blocks.
     * Addax PostgresqlWriter (and some other writers) do not reliably handle
     * multiple tables in a single content block, so we create one content block
     * per source→target table pair.
     */
    private List<Map<String, Object>> splitPerTable(
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig
    ) {
        List<String> readerTables = extractTables(readerConfig);
        List<String> writerTables = extractTables(writerConfig);

        // Only split when reader and writer have matching table counts > 1
        if (readerTables.size() <= 1 || readerTables.size() != writerTables.size()) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("reader", Map.of("name", readerType, "parameter", readerConfig));
            content.put("writer", Map.of("name", writerType, "parameter", writerConfig));
            return List.of(content);
        }

        List<Map<String, Object>> contentList = new java.util.ArrayList<>(readerTables.size());
        for (int i = 0; i < readerTables.size(); i++) {
            Map<String, Object> perReader = deepCopyConfig(readerConfig);
            setTables(perReader, List.of(readerTables.get(i)));

            Map<String, Object> perWriter = deepCopyConfig(writerConfig);
            setTables(perWriter, List.of(writerTables.get(i)));

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("reader", Map.of("name", readerType, "parameter", perReader));
            content.put("writer", Map.of("name", writerType, "parameter", perWriter));
            contentList.add(content);
        }
        LOG.info("Split multi-table job into {} per-table content blocks", contentList.size());
        return contentList;
    }

    private void applyJobDefaults(
        Map<String, Object> jobConfig,
        String readerType,
        Map<String, Object> readerConfig,
        String writerType,
        Map<String, Object> writerConfig
    ) {
        if (jobConfig == null || jobConfig.isEmpty()) {
            return;
        }
        Object jobObj = jobConfig.get("job");
        if (!(jobObj instanceof Map<?, ?> jobMap)) {
            return;
        }
        Object contentObj = jobMap.get("content");
        if (!(contentObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> contentMap)) {
            return;
        }
        applyNodeDefaults(contentMap, "reader", readerType, readerConfig);
        applyNodeDefaults(contentMap, "writer", writerType, writerConfig);
    }

    private void applyNodeDefaults(
        Map<?, ?> contentMap,
        String key,
        String pluginType,
        Map<String, Object> fallbackConfig
    ) {
        Object nodeObj = contentMap.get(key);
        if (!(nodeObj instanceof Map<?, ?> nodeMap)) {
            return;
        }
        Object paramObj = nodeMap.get("parameter");
        if (!(paramObj instanceof Map<?, ?> paramMap)) {
            return;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        paramMap.forEach((k, v) -> {
            if (k != null) {
                params.put(k.toString(), v);
            }
        });
        String normalizedPlugin = normalizePluginName(
            StringUtils.hasText(normalizeText(nodeMap.get("name"))) ? normalizeText(nodeMap.get("name")) : pluginType,
            key
        );
        if (StringUtils.hasText(normalizedPlugin) && nodeMap instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) nodeMap;
            mutable.put("name", normalizedPlugin);
        }
        if (fallbackConfig != null && !fallbackConfig.isEmpty()) {
            mergeMissing(params, fallbackConfig, "jdbcUrl");
            mergeMissing(params, fallbackConfig, "jdbc_url");
            mergeMissing(params, fallbackConfig, "url");
            mergeMissing(params, fallbackConfig, "jdbc");
            mergeMissing(params, fallbackConfig, "jdbcURL");
            mergeMissing(params, fallbackConfig, "username");
            mergeMissing(params, fallbackConfig, "password");
            mergeMissing(params, fallbackConfig, "host");
            mergeMissing(params, fallbackConfig, "port");
            mergeMissing(params, fallbackConfig, "database");
            mergeMissing(params, fallbackConfig, "schema");
            mergeMissing(params, fallbackConfig, "connection");
            mergeMissing(params, fallbackConfig, "table");
            mergeMissing(params, fallbackConfig, "tables");
        }
        ensureDriver(normalizedPlugin, params);
        if ("writer".equalsIgnoreCase(key)) {
            ensureWriterConnection(pluginType, params);
        }
        if (nodeMap instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) nodeMap;
            mutable.put("parameter", params);
        }
    }

    private void mergeMissing(Map<String, Object> target, Map<String, Object> source, String key) {
        if (target == null || source == null || !source.containsKey(key)) {
            return;
        }
        if (!target.containsKey(key) || !StringUtils.hasText(normalizeText(target.get(key)))) {
            target.put(key, source.get(key));
        }
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyConfig(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                copy.put(entry.getKey(), deepCopyConfig((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                java.util.ArrayList<Object> listCopy = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map<?, ?> itemMap) {
                        listCopy.add(deepCopyConfig((Map<String, Object>) itemMap));
                    } else {
                        listCopy.add(item);
                    }
                }
                copy.put(entry.getKey(), listCopy);
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
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
        List<Object> expanded = new java.util.ArrayList<>();
        for (Object item : contentList) {
            if (item instanceof Map<?, ?> contentMap) {
                Map<String, Object> readerParams = normalizeReaderWriter(contentMap, "reader");
                Map<String, Object> writerParams = normalizeReaderWriter(contentMap, "writer");
                replaceWriterTablePlaceholders(readerParams, writerParams);
                // Split multi-table content blocks into per-table blocks
                if (readerParams != null && writerParams != null) {
                    String rName = normalizeText(((Map<?, ?>) contentMap.get("reader")).get("name"));
                    String wName = normalizeText(((Map<?, ?>) contentMap.get("writer")).get("name"));
                    List<Map<String, Object>> split = splitPerTable(
                        rName != null ? rName : "", readerParams,
                        wName != null ? wName : "", writerParams
                    );
                    if (split.size() > 1) {
                        expanded.addAll(split);
                        continue;
                    }
                }
            }
            expanded.add(item);
        }
        if (expanded.size() != contentList.size()) {
            ((Map<Object, Object>) jobMap).put("content", expanded);
        }
    }

    private Map<String, Object> normalizeReaderWriter(Map<?, ?> contentMap, String key) {
        Object nodeObj = contentMap.get(key);
        if (!(nodeObj instanceof Map<?, ?> nodeMap)) {
            return null;
        }
        String pluginType = normalizeText(nodeMap.get("name"));
        String normalizedPlugin = normalizePluginName(pluginType, key);
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
        if (StringUtils.hasText(normalizedPlugin) && nodeMap instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) nodeMap;
            mutable.put("name", normalizedPlugin);
            pluginType = normalizedPlugin;
        }
        ensureDriver(pluginType, params);
        if ("writer".equalsIgnoreCase(key)) {
            ensureWriterConnection(pluginType, params);
        }
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

    private String normalizePluginName(String pluginType, String key) {
        if (!StringUtils.hasText(pluginType)) {
            return pluginType;
        }
        String normalized = pluginType.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("dm".equals(lower) || "dameng".equals(lower) || "dm8".equals(lower) || "dameng8".equals(lower)) {
            return "reader".equalsIgnoreCase(key) ? "rdbmsreader" : "rdbmswriter";
        }
        if ("dmreader".equals(lower)) {
            return "rdbmsreader";
        }
        if ("dmwriter".equals(lower)) {
            return "rdbmswriter";
        }
        if ("reader".equalsIgnoreCase(key) && "rdbms".equals(lower)) {
            return "rdbmsreader";
        }
        if ("writer".equalsIgnoreCase(key) && "rdbms".equals(lower)) {
            return "rdbmswriter";
        }
        return normalized;
    }

    private void ensureWriterConnection(String pluginType, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        List<String> jdbcUrls = normalizeJdbcUrlList(params.get("jdbcUrl"));
        if (jdbcUrls.isEmpty()) {
            fillJdbcUrlIfMissing(pluginType, params);
            jdbcUrls = normalizeJdbcUrlList(params.get("jdbcUrl"));
        }
        Object connection = params.get("connection");
        if (connection instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> connMap = new LinkedHashMap<>((Map<String, Object>) map);
                if (jdbcUrls.isEmpty()) {
                    fillJdbcUrlIfMissing(pluginType, connMap);
                    jdbcUrls = normalizeJdbcUrlList(connMap.get("jdbcUrl"));
                }
                if (!jdbcUrls.isEmpty()) {
                    // Writer jdbcUrl must be a plain string, not a list
                    connMap.put("jdbcUrl", jdbcUrls.get(0));
                }
                if (!connMap.containsKey("table")) {
                    List<String> tables = extractTables(params);
                    if (!tables.isEmpty()) {
                        connMap.put("table", tables);
                    }
                }
                java.util.ArrayList<Object> next = new java.util.ArrayList<>(list);
                next.set(0, connMap);
                params.put("connection", next);
            }
            ensurePostgresSslMode(params);
            return;
        }
        if (connection instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> connMap = new LinkedHashMap<>((Map<String, Object>) map);
            if (jdbcUrls.isEmpty()) {
                fillJdbcUrlIfMissing(pluginType, connMap);
                jdbcUrls = normalizeJdbcUrlList(connMap.get("jdbcUrl"));
            }
            if (!jdbcUrls.isEmpty()) {
                // Writer jdbcUrl must be a plain string, not a list
                connMap.put("jdbcUrl", jdbcUrls.get(0));
            }
            if (!connMap.containsKey("table")) {
                List<String> tables = extractTables(params);
                if (!tables.isEmpty()) {
                    connMap.put("table", tables);
                }
            }
            params.put("connection", List.of(connMap));
            params.remove("jdbcUrl");
            ensurePostgresSslMode(params);
            return;
        }
        if (!jdbcUrls.isEmpty()) {
            Map<String, Object> connMap = new LinkedHashMap<>();
            // Writer jdbcUrl must be a plain string, not a list
            connMap.put("jdbcUrl", jdbcUrls.get(0));
            List<String> tables = extractTables(params);
            if (!tables.isEmpty()) {
                connMap.put("table", tables);
            }
            params.put("connection", List.of(connMap));
            params.remove("jdbcUrl");
        }
        ensurePostgresSslMode(params);
    }

    /**
     * Append {@code sslmode=disable} to PostgreSQL JDBC URLs that don't already
     * specify an SSL mode.  This avoids SSL handshake failures when the target
     * PostgreSQL server does not have SSL enabled.
     */
    private void ensurePostgresSslMode(Map<String, Object> params) {
        if (params == null) {
            return;
        }
        // top-level jdbcUrl
        Object topUrl = params.get("jdbcUrl");
        if (topUrl instanceof String s) {
            params.put("jdbcUrl", appendSslDisable(s));
        }
        // connection list
        Object conn = params.get("connection");
        if (conn instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) entry;
                    Object url = m.get("jdbcUrl");
                    if (url instanceof String s) {
                        m.put("jdbcUrl", appendSslDisable(s));
                    }
                }
            }
        }
    }

    private String appendSslDisable(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return url;
        }
        if (url.contains("sslmode=") || url.contains("ssl=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "sslmode=disable";
    }

    private List<String> normalizeJdbcUrlList(Object value) {
        List<String> urls = new java.util.ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = normalizeText(item);
                if (StringUtils.hasText(text)) {
                    urls.add(text);
                }
            }
        } else {
            String text = normalizeText(value);
            if (StringUtils.hasText(text)) {
                urls.add(text);
            }
        }
        return urls;
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
        fillJdbcUrlIfMissing(pluginType, config);
        boolean preferList = !isWriter(pluginType);
        normalizeJdbcUrlField(config, "jdbcUrl", preferList);
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            fillJdbcUrlIfMissing(pluginType, map);
            normalizeJdbcUrlField(map, "jdbcUrl", preferList);
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    fillJdbcUrlIfMissing(pluginType, entryMap);
                    normalizeJdbcUrlField(entryMap, "jdbcUrl", preferList);
                }
            }
        }
    }

    private void fillJdbcUrlIfMissing(String pluginType, Map<?, ?> map) {
        if (map == null) {
            return;
        }
        Object direct = map.get("jdbcUrl");
        if (StringUtils.hasText(normalizeText(direct))) {
            return;
        }
        Object legacy = firstNonBlank(map.get("jdbc_url"), map.get("url"), map.get("jdbc"), map.get("jdbcURL"));
        String legacyUrl = normalizeText(legacy);
        if (StringUtils.hasText(legacyUrl) && map instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.put("jdbcUrl", legacyUrl);
            return;
        }
        String host = normalizeText(map.get("host"));
        String port = normalizeText(map.get("port"));
        String database = normalizeText(map.get("database"));
        if (!StringUtils.hasText(database)) {
            database = normalizeText(map.get("db"));
        }
        if (!StringUtils.hasText(database)) {
            database = normalizeText(map.get("schema"));
        }
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return;
        }
        String jdbcUrl = buildJdbcUrlFromParts(pluginType, host, port, database);
        if (StringUtils.hasText(jdbcUrl) && map instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            mutable.put("jdbcUrl", jdbcUrl);
        }
    }

    private String buildJdbcUrlFromParts(String pluginType, String host, String port, String database) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return null;
        }
        String type = normalizeText(pluginType).toLowerCase(Locale.ROOT);
        String resolvedPort = StringUtils.hasText(port) ? port : null;
        if (type.contains("postgres")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (type.contains("mysql") || type.contains("mariadb")) {
            return "jdbc:mysql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (type.contains("oracle")) {
            return "jdbc:oracle:thin:@" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ":" + database;
        }
        if (type.contains("sqlserver") || type.contains("mssql")) {
            return "jdbc:sqlserver://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ";databaseName=" + database;
        }
        if (type.contains("dm")) {
            return "jdbc:dm://" + host + (resolvedPort == null ? "" : ":" + resolvedPort);
        }
        if (type.contains("rdbms")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        return null;
    }

    private Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = normalizeText(value);
            if (StringUtils.hasText(text)) {
                return value;
            }
        }
        return null;
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
        List<String> existingTargets = extractTables(writerConfig);
        boolean hadPlaceholder = hasTablePlaceholderInConfig(writerConfig);
        if (!hadPlaceholder && !existingTargets.isEmpty()) {
            // Keep explicit writer table mapping as-is (e.g. prefixed targets like ods_erp_*).
            return;
        }
        List<String> targetTables = normalizeWriterTables(writerConfig, sourceTables);
        replaceTableField(writerConfig, targetTables);
        Object connection = writerConfig.get("connection");
        if (connection instanceof Map<?, ?> map) {
            replaceTableField(map, targetTables);
        } else if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    replaceTableField(entryMap, targetTables);
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

    private List<String> normalizeWriterTables(Map<String, Object> writerConfig, List<String> sourceTables) {
        if (sourceTables == null || sourceTables.isEmpty()) {
            return List.of();
        }
        String prefix = normalizeText(writerConfig == null ? null : writerConfig.get("tablePrefix"));
        if (!StringUtils.hasText(prefix) && writerConfig != null) {
            prefix = normalizeText(writerConfig.get("prefix"));
        }
        if (!StringUtils.hasText(prefix) && writerConfig != null) {
            prefix = normalizeText(writerConfig.get("targetPrefix"));
        }
        List<String> normalized = new java.util.ArrayList<>(sourceTables.size());
        for (String source : sourceTables) {
            String value = normalizeText(source);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String base = value;
            if (value.contains(".")) {
                base = value.substring(value.lastIndexOf('.') + 1);
            }
            if (StringUtils.hasText(prefix)) {
                base = prefix + base;
            }
            normalized.add(base);
        }
        return normalized.isEmpty() ? sourceTables : normalized;
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
        String writerType = normalizeWriterType(task.getDestinationType() != null ? task.getDestinationType() : "postgresqlwriter");
        applyTableMapping(task.getTableMapping(), readerConfig, writerConfig, writerType);
        Map<String, Object> jobConfig = task.getAddaxConfig() != null
            ? jsonNodeToMap(task.getAddaxConfig())
            : null;

        String resolvedReaderType = normalizeReaderType(StringUtils.hasText(readerTypeOverride) ? readerTypeOverride : task.getSourceType());
        // Map file source types to Addax plugin names
        if (StringUtils.hasText(resolvedReaderType)) {
            String lower = resolvedReaderType.toLowerCase(Locale.ROOT);
            if ("excel".equals(lower)) resolvedReaderType = "excelreader";
            else if ("csv".equals(lower)) resolvedReaderType = "txtfilereader";
        }
        return createJob(
            task.getName(),
            resolvedReaderType,
            readerConfig,
            writerType,
            writerConfig,
            jobConfig,
            task.getSyncMode()
        );
    }

    private boolean isFileReaderType(String readerType) {
        if (!StringUtils.hasText(readerType)) return false;
        String lower = readerType.toLowerCase(Locale.ROOT);
        return "excelreader".equals(lower) || "txtfilereader".equals(lower)
            || "excel".equals(lower) || "csv".equals(lower);
    }

    private void ensureFileReaderConfig(String readerType, Map<String, Object> config) {
        if (config == null) return;
        String containerPath = normalizeText(config.get("_containerPath"));
        if (!StringUtils.hasText(containerPath)) {
            containerPath = normalizeText(config.get("path"));
        }
        if (!StringUtils.hasText(containerPath)) {
            throw new IllegalArgumentException("文件源缺少文件路径");
        }
        String lower = readerType.toLowerCase(Locale.ROOT);
        if ("excelreader".equals(lower) || "excel".equals(lower)) {
            config.putIfAbsent("path", List.of(containerPath));
            config.putIfAbsent("header", true);
        } else {
            // txtfilereader for CSV
            config.putIfAbsent("path", List.of(containerPath));
            config.putIfAbsent("encoding", "UTF-8");
            config.putIfAbsent("fieldDelimiter", ",");
            config.putIfAbsent("skipHeader", true);
            config.putIfAbsent("column", List.of("*"));
        }
        // Ensure path is a list containing the container path
        Object pathObj = config.get("path");
        if (pathObj instanceof String str) {
            config.put("path", List.of(str));
        }
    }

    private void stripFileMetadataKeys(Map<String, Object> config) {
        if (config == null) return;
        for (String key : FILE_METADATA_KEYS) {
            config.remove(key);
        }
        // Also remove non-Addax keys
        config.remove("readerType");
        config.remove("sourceSystem");
    }

    /**
     * Extract _fileColumns from readerConfig before metadata keys are stripped.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFileColumns(Map<String, Object> readerConfig) {
        if (readerConfig == null) return List.of();
        Object obj = readerConfig.get("_fileColumns");
        if (!(obj instanceof List<?> list) || list.isEmpty()) return List.of();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> col = new LinkedHashMap<>();
                map.forEach((k, v) -> { if (k != null) col.put(k.toString(), v); });
                result.add(col);
            }
        }
        return result;
    }

    /**
     * For file source tasks, inject CREATE TABLE IF NOT EXISTS DDL into writer preSql.
     * This is necessary because dts-ingestion service cannot reach the target database,
     * but the Addax Docker container can. The DDL runs as part of the Addax job execution.
     */
    @SuppressWarnings("unchecked")
    private void injectFileSourceCreateTablePreSql(
        Map<String, Object> writerConfig,
        List<Map<String, Object>> fileColumns,
        boolean autoId,
        String writerType
    ) {
        if (writerConfig == null || fileColumns == null || fileColumns.isEmpty()) return;
        List<String> tables = extractTables(writerConfig);
        if (tables.isEmpty()) return;
        String rawTable = tables.get(0).toLowerCase(Locale.ROOT);
        String schema = normalizeText(writerConfig.get("schema"));

        // Parse schema.table if the table name contains a dot
        String tableName = rawTable;
        int dotIdx = rawTable.indexOf('.');
        if (dotIdx > 0 && dotIdx < rawTable.length() - 1) {
            String parsedSchema = rawTable.substring(0, dotIdx);
            tableName = rawTable.substring(dotIdx + 1);
            if (!StringUtils.hasText(schema)) {
                schema = parsedSchema;
            }
        }

        // Build qualified table reference
        String qualifiedTable = StringUtils.hasText(schema) && !"public".equalsIgnoreCase(schema)
            ? quoteIdentifier(schema) + "." + quoteIdentifier(tableName)
            : quoteIdentifier(tableName);

        // Resolve column prefix/suffix rules
        String colPrefix = normalizeText(writerConfig.get("_columnPrefix"));
        String colSuffix = normalizeText(writerConfig.get("_columnSuffix"));
        String safeColPrefix = StringUtils.hasText(colPrefix) ? colPrefix : "";
        String safeColSuffix = StringUtils.hasText(colSuffix) ? colSuffix : "";

        // Build CREATE TABLE IF NOT EXISTS DDL
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        ddl.append(qualifiedTable).append(" (");
        boolean first = true;
        if (autoId && isPostgresWriter(writerType)) {
            ddl.append("\"id\" bigserial primary key");
            first = false;
        }
        // Source columns (with prefix/suffix applied)
        List<String> writerColNames = new java.util.ArrayList<>();
        for (Map<String, Object> col : fileColumns) {
            String colName = resolveFileColumnName(col);
            String colType = normalizeText(col.get("type"));
            if (!StringUtils.hasText(colName)) continue;
            String targetColName = (safeColPrefix + colName + safeColSuffix).toLowerCase(Locale.ROOT);
            if (!first) ddl.append(", ");
            first = false;
            ddl.append(quoteIdentifier(targetColName))
               .append(" ").append(mapFileTypeToPostgres(colType, col));
            writerColNames.add(quoteIdentifier(targetColName));
        }
        // Extra columns (with DEFAULT values, not included in writer column list)
        Object extraObj = writerConfig.get("_extraColumns");
        if (extraObj instanceof List<?> extraList && !extraList.isEmpty()) {
            for (Object item : extraList) {
                if (!(item instanceof Map<?, ?> extraMap)) continue;
                String extraName = normalizeText(extraMap.get("name"));
                String extraType = normalizeText(extraMap.get("type"));
                String extraDefault = normalizeText(extraMap.get("defaultValue"));
                if (!StringUtils.hasText(extraName)) continue;
                if (!first) ddl.append(", ");
                first = false;
                ddl.append(quoteIdentifier(extraName.toLowerCase(Locale.ROOT)))
                   .append(" ").append(mapExtraColumnType(extraType, (Map<?, ?>) item));
                if (StringUtils.hasText(extraDefault)) {
                    ddl.append(" DEFAULT ").append(extraDefault);
                }
            }
        }
        ddl.append(")");

        // Prepend CREATE TABLE to preSql, then TRUNCATE
        List<String> preSql = new java.util.ArrayList<>();
        preSql.add(ddl.toString());
        preSql.add("TRUNCATE TABLE " + qualifiedTable);

        // Preserve existing preSql entries (skip duplicate TRUNCATE)
        Object existing = writerConfig.get("preSql");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                String s = normalizeText(item);
                if (StringUtils.hasText(s) && !s.toUpperCase(Locale.ROOT).contains("TRUNCATE")) {
                    preSql.add(s);
                }
            }
        }
        writerConfig.put("preSql", preSql);

        // Set explicit writer columns (source columns with prefix/suffix, excluding extras)
        Object colObj = writerConfig.get("column");
        if ((colObj == null || isWildcardColumn(colObj)) && !writerColNames.isEmpty()) {
            writerConfig.put("column", writerColNames);
            LOG.info("Resolved writer columns to: {}", writerColNames);
        }

        LOG.info("Injected CREATE TABLE preSql for file source table: {}", tableName);
    }

    /**
     * If _extraColumns is configured in writerConfig, inject ALTER TABLE + UPDATE as postSql.
     * Extra columns are added AFTER data load to avoid column count mismatch with the reader.
     */
    @SuppressWarnings("unchecked")
    private void injectExtraColumnsPostSql(Map<String, Object> writerConfig, String writerType) {
        if (writerConfig == null) return;
        Object extraObj = writerConfig.get("_extraColumns");
        if (!(extraObj instanceof List<?> extraList) || extraList.isEmpty()) return;
        if (!isPostgresWriter(writerType)) return;

        List<String> tables = extractTables(writerConfig);
        if (tables.isEmpty()) return;

        List<String> postSql = new java.util.ArrayList<>();
        // Preserve existing postSql
        Object existing = writerConfig.get("postSql");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                String s = normalizeText(item);
                if (StringUtils.hasText(s)) postSql.add(s);
            }
        }

        for (String rawTable : tables) {
            String tableName = rawTable.toLowerCase(Locale.ROOT);
            String schema = normalizeText(writerConfig.get("schema"));
            int dotIdx = tableName.indexOf('.');
            if (dotIdx > 0 && dotIdx < tableName.length() - 1) {
                if (!StringUtils.hasText(schema)) schema = tableName.substring(0, dotIdx);
                tableName = tableName.substring(dotIdx + 1);
            }
            String qualifiedTable = StringUtils.hasText(schema) && !"public".equalsIgnoreCase(schema)
                ? quoteIdentifier(schema) + "." + quoteIdentifier(tableName)
                : quoteIdentifier(tableName);

            StringBuilder updateSet = new StringBuilder();
            for (Object item : extraList) {
                if (!(item instanceof Map<?, ?> colMap)) continue;
                String colName = normalizeText(colMap.get("name"));
                String colType = normalizeText(colMap.get("type"));
                String defaultVal = normalizeText(colMap.get("defaultValue"));
                if (!StringUtils.hasText(colName)) continue;
                String sqlType = mapExtraColumnType(colType, colMap);
                String quotedCol = quoteIdentifier(colName.toLowerCase(Locale.ROOT));

                postSql.add("ALTER TABLE " + qualifiedTable
                    + " ADD COLUMN IF NOT EXISTS " + quotedCol + " " + sqlType);

                if (StringUtils.hasText(defaultVal)) {
                    if (!updateSet.isEmpty()) updateSet.append(", ");
                    updateSet.append(quotedCol).append(" = ").append(defaultVal);
                }
            }
            if (!updateSet.isEmpty()) {
                postSql.add("UPDATE " + qualifiedTable + " SET " + updateSet + " WHERE TRUE");
            }
        }

        if (!postSql.isEmpty()) {
            writerConfig.put("postSql", postSql);
            LOG.info("Injected extra columns postSql: {}", postSql);
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureDefaultExtraColumns(
        Map<String, Object> rawReaderConfig,
        Map<String, Object> readerConfig,
        Map<String, Object> writerConfig,
        String readerType,
        String writerType
    ) {
        if (writerConfig == null) {
            return;
        }
        java.util.List<Map<String, Object>> extras = new java.util.ArrayList<>();
        Map<String, Map<String, Object>> indexByName = new java.util.LinkedHashMap<>();
        Object existing = writerConfig.get("_extraColumns");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> extra = new LinkedHashMap<>();
                    map.forEach((k, v) -> {
                        if (k != null) {
                            extra.put(k.toString(), v);
                        }
                    });
                    if (!extra.isEmpty()) {
                        extras.add(extra);
                        String name = normalizeText(extra.get("name"));
                        if (StringUtils.hasText(name)) {
                            indexByName.put(name.toLowerCase(Locale.ROOT), extra);
                        }
                    }
                }
            }
        }

        java.util.Set<String> names = new java.util.HashSet<>();
        for (Map<String, Object> extra : extras) {
            String name = normalizeText(extra.get("name"));
            if (StringUtils.hasText(name)) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }

        boolean changed = false;
        boolean fileSource = isFileReaderType(readerType);
        String sourceSystem = resolveSourceSystemForExtra(
            fileSource && rawReaderConfig != null ? rawReaderConfig : readerConfig,
            fileSource
        );
        String sourceSystemValue = quoteSqlString(sourceSystem);
        Map<String, Object> sourceSystemCol = indexByName.get(EXTRA_COL_SOURCE_SYSTEM);
        if (sourceSystemCol == null) {
            sourceSystemCol = new LinkedHashMap<>();
            sourceSystemCol.put("name", EXTRA_COL_SOURCE_SYSTEM);
            extras.add(sourceSystemCol);
            changed = true;
        }
        if (!"来源系统".equals(normalizeText(sourceSystemCol.get("label")))) {
            sourceSystemCol.put("label", "来源系统");
            changed = true;
        }
        if (!"string".equalsIgnoreCase(normalizeText(sourceSystemCol.get("type")))) {
            sourceSystemCol.put("type", "string");
            changed = true;
        }
        if (!sourceSystemValue.equals(normalizeText(sourceSystemCol.get("defaultValue")))) {
            sourceSystemCol.put("defaultValue", sourceSystemValue);
            changed = true;
        }

        String importTimeExpr = resolveImportTimeDefault(writerType);
        Map<String, Object> importTimeCol = indexByName.get(EXTRA_COL_IMPORT_TIME);
        if (importTimeCol == null) {
            importTimeCol = new LinkedHashMap<>();
            importTimeCol.put("name", EXTRA_COL_IMPORT_TIME);
            extras.add(importTimeCol);
            changed = true;
        }
        if (!"导入时间".equals(normalizeText(importTimeCol.get("label")))) {
            importTimeCol.put("label", "导入时间");
            changed = true;
        }
        if (!"timestamp".equalsIgnoreCase(normalizeText(importTimeCol.get("type")))) {
            importTimeCol.put("type", "timestamp");
            changed = true;
        }
        if (!importTimeExpr.equals(normalizeText(importTimeCol.get("defaultValue")))) {
            importTimeCol.put("defaultValue", importTimeExpr);
            changed = true;
        }

        if (changed) {
            writerConfig.put("_extraColumns", extras);
        }
    }

    private String resolveSourceSystemForExtra(Map<String, Object> readerConfig, boolean fileSource) {
        if (fileSource) {
            return resolveFileSourceSystem(readerConfig);
        }
        if (readerConfig == null || readerConfig.isEmpty()) {
            return "unknown";
        }
        String source = normalizeText(readerConfig.get("sourceSystem"));
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("sourceApp"));
        }
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("appCode"));
        }
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("system"));
        }
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("name"));
        }
        return StringUtils.hasText(source) ? source : "unknown";
    }

    private String resolveFileSourceSystem(Map<String, Object> readerConfig) {
        if (readerConfig == null || readerConfig.isEmpty()) {
            return "unknown";
        }
        String source = extractFileName(normalizeText(readerConfig.get("_originalName")));
        if (!StringUtils.hasText(source)) {
            source = extractFileName(firstStringValue(readerConfig.get("_filePath")));
        }
        if (!StringUtils.hasText(source)) {
            source = extractFileName(firstStringValue(readerConfig.get("_containerPath")));
        }
        if (!StringUtils.hasText(source)) {
            source = extractFileName(firstStringValue(readerConfig.get("path")));
        }
        if (!StringUtils.hasText(source)) {
            source = normalizeText(readerConfig.get("sourceSystem"));
        }
        return StringUtils.hasText(source) ? source : "unknown";
    }

    private String extractFileName(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String filename = slash >= 0 && slash < normalized.length() - 1
            ? normalized.substring(slash + 1)
            : normalized;
        return normalizeText(filename);
    }

    private String resolveImportTimeDefault(String writerType) {
        String type = normalizeText(writerType);
        String normalized = StringUtils.hasText(type) ? type.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("postgres")) {
            // PostgreSQL column DEFAULT does not reliably accept "AT TIME ZONE" form here.
            // Use timezone(text, now()) to get timestamp without time zone in Asia/Shanghai.
            return "timezone('Asia/Shanghai', now())";
        }
        return "CURRENT_TIMESTAMP";
    }

    private String quoteSqlString(String value) {
        String resolved = StringUtils.hasText(value) ? value : "unknown";
        return "'" + resolved.replace("'", "''") + "'";
    }

    private String mapExtraColumnType(String type, Map<?, ?> colMap) {
        if (!StringUtils.hasText(type)) return "TEXT";
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "integer", "int" -> "INTEGER";
            case "long", "bigint" -> "BIGINT";
            case "double" -> "DOUBLE PRECISION";
            case "numeric", "decimal" -> "NUMERIC";
            case "boolean" -> "BOOLEAN";
            case "date" -> "DATE";
            case "timestamp" -> "TIMESTAMP";
            case "text" -> "TEXT";
            case "jsonb" -> "JSONB";
            default -> "VARCHAR(500)";
        };
    }

    private boolean resolveFileAutoId(Map<String, Object> readerConfig) {
        if (readerConfig == null) return false;
        Object value = readerConfig.get("_autoId");
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value != null) {
            String text = value.toString().trim();
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
        }
        return false;
    }

    private String resolveFileColumnName(Map<String, Object> col) {
        if (col == null) return "";
        String name = normalizeText(col.get("safeName"));
        if (!StringUtils.hasText(name)) {
            name = normalizeText(col.get("name"));
        }
        if (!StringUtils.hasText(name)) {
            name = normalizeText(col.get("label"));
        }
        return name;
    }

    private boolean isPostgresWriter(String writerType) {
        if (!StringUtils.hasText(writerType)) {
            return false;
        }
        String lower = writerType.toLowerCase(Locale.ROOT);
        return lower.contains("postgres");
    }

    private String quoteIdentifier(String name) {
        if (!StringUtils.hasText(name)) return name;
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private String mapFileTypeToPostgres(String fileType, Map<String, Object> col) {
        if (!StringUtils.hasText(fileType)) return "text";
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "long", "bigint" -> "bigint";
            case "integer", "int" -> "integer";
            case "double" -> "double precision";
            case "numeric", "decimal" -> {
                int precision = toInt(col.get("precision"), 18);
                int scale = toInt(col.get("scale"), 2);
                yield "numeric(" + precision + "," + scale + ")";
            }
            case "date" -> "date";
            case "timestamp" -> "timestamp";
            case "boolean" -> "boolean";
            case "text" -> "text";
            case "jsonb" -> "jsonb";
            default -> {
                int length = toInt(col.get("length"), 500);
                yield "varchar(" + length + ")";
            }
        };
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number num) return num.intValue();
        try { return Integer.parseInt(value.toString().trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private String normalizeReaderType(String readerType) {
        if (!StringUtils.hasText(readerType)) {
            return readerType;
        }
        String normalized = readerType.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("dm".equals(lower) || "dameng".equals(lower) || "dm8".equals(lower) || "dameng8".equals(lower)) {
            return "rdbmsreader";
        }
        if ("dmreader".equalsIgnoreCase(normalized)) {
            return "rdbmsreader";
        }
        return normalized;
    }

    private String normalizeWriterType(String writerType) {
        if (!StringUtils.hasText(writerType)) {
            return writerType;
        }
        String normalized = writerType.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("dm".equals(lower) || "dameng".equals(lower) || "dm8".equals(lower) || "dameng8".equals(lower)) {
            return "rdbmswriter";
        }
        if ("dmwriter".equalsIgnoreCase(normalized)) {
            return "rdbmswriter";
        }
        return normalized;
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
            try { Files.setPosixFilePermissions(jobPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--")); } catch (Exception ignored) {}
            return jobPath.toString();
        } catch (Exception ex) {
            LOG.warn("Failed to save job JSON for task {}: {}", taskId, ex.getMessage());
            throw new IllegalStateException("保存 Addax 作业失败: " + ex.getMessage(), ex);
        }
    }

    public boolean deleteJobIfExists(String jobPath) {
        if (!StringUtils.hasText(jobPath)) {
            return false;
        }
        try {
            Path path = Paths.get(jobPath.trim());
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                LOG.info("Deleted Addax job file: {}", path);
            }
            return deleted;
        } catch (Exception ex) {
            LOG.warn("Failed to delete Addax job file {}: {}", jobPath, ex.getMessage());
            return false;
        }
    }

    /**
     * Split a multi-content-block job JSON into per-table JSON files.
     * Addax processes only the first content block in a job, so for multi-table tasks
     * we need one JSON file per table and one Airflow operator per file.
     *
     * @param baseJobPath path to the base job JSON (may have multiple content blocks)
     * @return list of per-table jobs; single-element list if only one content block
     */
    @SuppressWarnings("unchecked")
    public List<PerTableJob> splitJobIntoPerTableFiles(String baseJobPath) {
        if (!StringUtils.hasText(baseJobPath)) {
            return List.of();
        }
        Path basePath = Paths.get(baseJobPath.trim());
        if (!Files.exists(basePath)) {
            return List.of();
        }
        try {
            Map<String, Object> jobConfig = objectMapper.readValue(
                basePath.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            Object jobObj = jobConfig.get("job");
            if (!(jobObj instanceof Map<?, ?> jobMap)) {
                return List.of(new PerTableJob("run", toContainerJobPath(baseJobPath), baseJobPath));
            }
            Object contentObj = jobMap.get("content");
            if (!(contentObj instanceof List<?> contentList)) {
                return List.of(new PerTableJob("run", toContainerJobPath(baseJobPath), baseJobPath));
            }
            if (contentList.size() <= 1) {
                String tableName = !contentList.isEmpty()
                    ? extractTableNameFromContent(contentList.get(0)) : "run";
                return List.of(new PerTableJob(
                    StringUtils.hasText(tableName) ? tableName : "run",
                    toContainerJobPath(baseJobPath),
                    baseJobPath
                ));
            }
            // Multiple content blocks — split into per-table files
            String baseName = basePath.getFileName().toString().replaceAll("\\.json$", "");
            Path dir = basePath.getParent();
            List<PerTableJob> results = new java.util.ArrayList<>();
            for (int i = 0; i < contentList.size(); i++) {
                Object content = contentList.get(i);
                String tableName = extractTableNameFromContent(content);
                if (!StringUtils.hasText(tableName)) {
                    tableName = "table_" + i;
                }
                String slug = tableName.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+", "").replaceAll("_+$", "");
                if (!StringUtils.hasText(slug)) {
                    slug = "table_" + i;
                }
                Map<String, Object> perTableConfig = new LinkedHashMap<>();
                Map<String, Object> perJob = new LinkedHashMap<>();
                perJob.put("setting", jobMap.get("setting"));
                perJob.put("content", List.of(content));
                perTableConfig.put("job", perJob);
                String fileName = baseName + "_" + slug + ".json";
                Path filePath = dir.resolve(fileName);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), perTableConfig);
                try { Files.setPosixFilePermissions(filePath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--")); } catch (Exception ignored) {}
                results.add(new PerTableJob(tableName, toContainerJobPath(filePath.toString()), filePath.toString()));
            }
            LOG.info("Split base job {} into {} per-table job files", basePath.getFileName(), results.size());
            return results;
        } catch (Exception ex) {
            LOG.warn("Failed to split job into per-table files: {}", ex.getMessage());
            return List.of(new PerTableJob("run", toContainerJobPath(baseJobPath), baseJobPath));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTableNameFromContent(Object content) {
        if (!(content instanceof Map<?, ?> contentMap)) {
            return null;
        }
        // Try writer table first (target table name is more meaningful)
        Object writerObj = contentMap.get("writer");
        if (writerObj instanceof Map<?, ?> writerMap) {
            Object paramObj = writerMap.get("parameter");
            if (paramObj instanceof Map<?, ?> paramMap) {
                Map<String, Object> params = new LinkedHashMap<>();
                paramMap.forEach((k, v) -> { if (k != null) params.put(k.toString(), v); });
                List<String> tables = extractTables(params);
                if (!tables.isEmpty()) {
                    return tables.get(0);
                }
            }
        }
        // Fallback to reader table
        Object readerObj = contentMap.get("reader");
        if (readerObj instanceof Map<?, ?> readerMap) {
            Object paramObj = readerMap.get("parameter");
            if (paramObj instanceof Map<?, ?> paramMap) {
                Map<String, Object> params = new LinkedHashMap<>();
                paramMap.forEach((k, v) -> { if (k != null) params.put(k.toString(), v); });
                List<String> tables = extractTables(params);
                if (!tables.isEmpty()) {
                    return tables.get(0);
                }
            }
        }
        return null;
    }

    /**
     * Workaround for Addax 6.0.8 bug: DataBaseType.quoteColumn() returns null for PostgreSQL,
     * causing dealColumnConf to corrupt column ["*"] into [null, null, ...] → invalid SQL.
     * This method resolves actual column names from the target database and replaces ["*"]
     * in PostgreSQL writer configs so Addax skips its broken column resolution.
     */
    @SuppressWarnings("unchecked")
    public void resolveWriterColumnsIfNeeded(String jobPath) {
        if (!StringUtils.hasText(jobPath)) {
            return;
        }
        Path path = Paths.get(jobPath.trim());
        if (!Files.exists(path)) {
            return;
        }
        try {
            Map<String, Object> jobConfig = objectMapper.readValue(
                path.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            Object jobObj = jobConfig.get("job");
            if (!(jobObj instanceof Map<?, ?> jobMap)) {
                return;
            }
            Object contentObj = jobMap.get("content");
            if (!(contentObj instanceof List<?> contentList)) {
                return;
            }
            boolean modified = false;
            for (Object item : contentList) {
                if (!(item instanceof Map<?, ?> contentMap)) {
                    continue;
                }
                Object writerObj = contentMap.get("writer");
                if (!(writerObj instanceof Map<?, ?> writerMap)) {
                    continue;
                }
                String writerName = normalizeText(writerMap.get("name"));
                if (!isPostgresWriter(writerName)) {
                    continue;
                }
                Object paramObj = writerMap.get("parameter");
                if (!(paramObj instanceof Map<?, ?> paramMap)) {
                    continue;
                }
                // Check if column is ["*"]
                Object columnObj = paramMap.get("column");
                if (!isWildcardColumn(columnObj)) {
                    continue;
                }
                // Extract writer connection info
                Map<String, Object> params = new LinkedHashMap<>();
                paramMap.forEach((k, v) -> {
                    if (k != null) params.put(k.toString(), v);
                });
                String jdbcUrl = resolveJdbcUrl(params);
                String username = normalizeText(params.get("username"));
                String password = normalizeText(params.get("password"));
                String driver = normalizeText(params.get("driver"));
                if (!StringUtils.hasText(jdbcUrl)) {
                    continue;
                }
                // Get the target table name
                List<String> tables = extractTables(params);
                if (tables.isEmpty()) {
                    continue;
                }
                String tableName = tables.get(0);
                // Query actual columns from target database
                JdbcMetadataService.JdbcConnectionInfo connInfo = new JdbcMetadataService.JdbcConnectionInfo(
                    jdbcUrl, username, password, driver, null, null
                );
                List<JdbcMetadataService.ColumnMeta> columns = jdbcMetadataService.getTableColumns(connInfo, tableName);
                if (columns.isEmpty()) {
                    LOG.warn("Could not resolve columns for target table {} — keeping [\"*\"]", tableName);
                    continue;
                }
                List<String> columnNames = columns.stream()
                    .map(JdbcMetadataService.ColumnMeta::name)
                    .map(name -> name != null ? name.toLowerCase(Locale.ROOT) : name)
                    .toList();
                // Replace ["*"] with actual column names
                ((Map<String, Object>) paramMap).put("column", columnNames);
                modified = true;
                LOG.info("Resolved {} writer columns for table {}: {}", columnNames.size(), tableName,
                    columnNames.size() <= 20 ? columnNames : columnNames.subList(0, 20) + "...(" + columnNames.size() + ")");
            }
            if (modified) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), jobConfig);
                LOG.info("Updated Addax job with resolved writer columns: {}", path);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to resolve writer columns for job {}: {}", jobPath, ex.getMessage());
        }
    }

    /**
     * For full_refresh sync mode, add preSql: ["TRUNCATE TABLE <table>"] to each writer.
     * This ensures the target table is cleared before inserting fresh data.
     */
    @SuppressWarnings("unchecked")
    private void applyFullRefreshPreSql(Map<String, Object> jobConfig) {
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
            if (!(item instanceof Map<?, ?> contentMap)) {
                continue;
            }
            Object writerObj = contentMap.get("writer");
            if (!(writerObj instanceof Map<?, ?> writerMap)) {
                continue;
            }
            Object paramObj = writerMap.get("parameter");
            if (!(paramObj instanceof Map<?, ?> paramMap)) {
                continue;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            paramMap.forEach((k, v) -> { if (k != null) params.put(k.toString(), v); });
            // Skip if preSql is already configured
            if (params.containsKey("preSql")) {
                continue;
            }
            List<String> tables = extractTables(params);
            if (tables.isEmpty()) {
                continue;
            }
            List<String> truncateStatements = tables.stream()
                .filter(StringUtils::hasText)
                .map(table -> "TRUNCATE TABLE " + table)
                .toList();
            if (!truncateStatements.isEmpty()) {
                ((Map<String, Object>) paramMap).put("preSql", truncateStatements);
                LOG.info("Added full_refresh preSql for tables: {}", tables);
            }
        }
    }

    private boolean isWildcardColumn(Object columnObj) {
        if (columnObj instanceof List<?> list) {
            return list.size() == 1 && "*".equals(normalizeText(list.get(0)));
        }
        if (columnObj instanceof String str) {
            return "*".equals(normalizeText(str));
        }
        return false;
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

    private void applyTableMapping(JsonNode tableMapping, Map<String, Object> readerConfig, Map<String, Object> writerConfig, String writerType) {
        if (readerConfig == null || writerConfig == null) {
            return;
        }
        String writerSchema = resolveSchema(writerConfig);
        List<String> configuredWriterTables = extractTables(writerConfig);
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
                String prefix = resolveTablePrefix(writerConfig);
                if (!StringUtils.hasText(prefix)) {
                    prefix = inferTablePrefixFromTargets(sourceTables, configuredWriterTables);
                }
                if (isSourceAlignedTables(sourceTables, targetTables)) {
                    if (!configuredWriterTables.isEmpty() && !isSourceAlignedTables(sourceTables, configuredWriterTables)) {
                        targetTables = configuredWriterTables;
                    } else {
                        final String resolvedPrefix = prefix;
                        targetTables = sourceTables.stream()
                            .map(source -> buildTargetTableName(source, resolvedPrefix))
                            .toList();
                    }
                }
                targetTables = resolveTargetTableNames(targetTables, writerSchema, writerType);
                setTables(writerConfig, targetTables);
            }
            return;
        }
        List<String> sourceTables = extractTables(readerConfig);
        if (sourceTables.isEmpty()) {
            return;
        }
        List<String> targetTables = extractTables(writerConfig);
        if (!targetTables.isEmpty() && isSourceAlignedTables(sourceTables, targetTables)) {
            String prefix = resolveTablePrefix(writerConfig);
            if (!StringUtils.hasText(prefix)) {
                prefix = inferTablePrefixFromTargets(sourceTables, configuredWriterTables);
            }
            if (StringUtils.hasText(prefix)) {
                final String resolvedPrefix = prefix;
                targetTables = sourceTables.stream()
                    .map(source -> buildTargetTableName(source, resolvedPrefix))
                    .toList();
            }
        }
        if (targetTables.isEmpty()) {
            String prefix = resolveTablePrefix(writerConfig);
            if (!StringUtils.hasText(prefix)) {
                prefix = inferTablePrefixFromTargets(sourceTables, configuredWriterTables);
            }
            final String resolvedPrefix = prefix;
            targetTables = sourceTables.stream()
                .map(table -> buildTargetTableName(table, resolvedPrefix))
                .toList();
        }
        targetTables = resolveTargetTableNames(targetTables, writerSchema, writerType);
        setTables(writerConfig, targetTables);
    }

    /**
     * Resolve final target table names:
     * - If table already has schema prefix (e.g. ERPDEMO.CUSTOMER), keep that schema
     * - If table has no schema, apply writerSchema as prefix
     * - For PostgreSQL writer, lowercase everything
     */
    private List<String> resolveTargetTableNames(List<String> tables, String writerSchema, String writerType) {
        if (tables == null || tables.isEmpty()) {
            return tables;
        }
        List<String> resolved = new java.util.ArrayList<>(tables.size());
        for (String table : tables) {
            String name = normalizeText(table);
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String base = name;
            String schemaPart = null;
            int idx = name.indexOf('.');
            if (idx > 0 && idx < name.length() - 1) {
                schemaPart = name.substring(0, idx);
                base = name.substring(idx + 1);
            }
            if (StringUtils.hasText(writerSchema)) {
                if (!StringUtils.hasText(schemaPart) || !schemaPart.equalsIgnoreCase(writerSchema)) {
                    name = writerSchema.trim() + "." + base;
                } else {
                    name = writerSchema.trim() + "." + base;
                }
            } else if (StringUtils.hasText(schemaPart)) {
                // avoid leaking source schema into target when no writer schema is specified
                name = base;
            }
            resolved.add(name);
        }
        return lowercaseTablesForPostgres(resolved, writerType);
    }

    private boolean isSourceAlignedTables(List<String> sourceTables, List<String> targetTables) {
        if (sourceTables == null || targetTables == null || sourceTables.isEmpty() || targetTables.isEmpty()) {
            return false;
        }
        if (sourceTables.size() != targetTables.size()) {
            return false;
        }
        for (int i = 0; i < sourceTables.size(); i++) {
            String source = normalizeText(sourceTables.get(i));
            String target = normalizeText(targetTables.get(i));
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
                return false;
            }
            if (source.equalsIgnoreCase(target)) {
                continue;
            }
            if (!stripSchema(source).equalsIgnoreCase(stripSchema(target))) {
                return false;
            }
        }
        return true;
    }

    private String buildTargetTableName(String sourceTable, String prefix) {
        String base = stripSchema(sourceTable);
        if (!StringUtils.hasText(base)) {
            return base;
        }
        return StringUtils.hasText(prefix) ? prefix + base : base;
    }

    private String inferTablePrefixFromTargets(List<String> sourceTables, List<String> targetTables) {
        if (sourceTables == null || targetTables == null || sourceTables.isEmpty() || targetTables.isEmpty()) {
            return null;
        }
        int size = Math.min(sourceTables.size(), targetTables.size());
        String inferred = null;
        for (int i = 0; i < size; i++) {
            String sourceBase = stripSchema(sourceTables.get(i));
            String targetBase = stripSchema(targetTables.get(i));
            if (!StringUtils.hasText(sourceBase) || !StringUtils.hasText(targetBase)) {
                continue;
            }
            String sourceLower = sourceBase.toLowerCase(Locale.ROOT);
            String targetLower = targetBase.toLowerCase(Locale.ROOT);
            if (!targetLower.endsWith(sourceLower)) {
                continue;
            }
            String candidate = targetBase.substring(0, targetBase.length() - sourceBase.length());
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (inferred == null) {
                inferred = candidate;
                continue;
            }
            if (!inferred.equals(candidate)) {
                return null;
            }
        }
        return inferred;
    }

    private String stripSchema(String table) {
        String normalized = normalizeText(table);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        int idx = normalized.lastIndexOf('.');
        if (idx > -1 && idx < normalized.length() - 1) {
            return normalized.substring(idx + 1);
        }
        return normalized;
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

    private List<String> lowercaseTablesForPostgres(List<String> tables, String writerType) {
        if (tables == null || tables.isEmpty() || !isPostgresWriter(writerType)) {
            return tables;
        }
        return tables.stream()
            .map(table -> table != null ? table.toLowerCase(Locale.ROOT) : table)
            .toList();
    }

    private record TableMapping(String source, String target) {}
}
