package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AddaxJdbcConfigNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(AddaxJdbcConfigNormalizer.class);
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

    private final ObjectMapper objectMapper;

    public AddaxJdbcConfigNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> ensureDriver(String pluginType, Map<String, Object> config) {
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

    public void ensureWriterConnection(String pluginType, Map<String, Object> params) {
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
                Map<String, Object> connMap = new LinkedHashMap<>(toStringKeyMap(map));
                if (jdbcUrls.isEmpty()) {
                    fillJdbcUrlIfMissing(pluginType, connMap);
                    jdbcUrls = normalizeJdbcUrlList(connMap.get("jdbcUrl"));
                }
                if (!jdbcUrls.isEmpty()) {
                    connMap.put("jdbcUrl", jdbcUrls.get(0));
                }
                if (!connMap.containsKey("table")) {
                    List<String> tables = extractTables(params);
                    if (!tables.isEmpty()) {
                        connMap.put("table", tables);
                    }
                }
                List<Object> next = new ArrayList<>(list);
                next.set(0, connMap);
                params.put("connection", next);
            }
            ensurePostgresSslMode(params);
            return;
        }
        if (connection instanceof Map<?, ?> map) {
            Map<String, Object> connMap = new LinkedHashMap<>(toStringKeyMap(map));
            if (jdbcUrls.isEmpty()) {
                fillJdbcUrlIfMissing(pluginType, connMap);
                jdbcUrls = normalizeJdbcUrlList(connMap.get("jdbcUrl"));
            }
            if (!jdbcUrls.isEmpty()) {
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

    public void normalizeJdbcUrl(String pluginType, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        ensureMutableConnection(config);
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

    private void ensurePostgresSslMode(Map<String, Object> params) {
        if (params == null) {
            return;
        }
        Object topUrl = params.get("jdbcUrl");
        if (topUrl instanceof String s) {
            params.put("jdbcUrl", appendSslDisable(s));
        }
        Object conn = params.get("connection");
        if (conn instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mutable = (Map<String, Object>) map;
                    Object url = mutable.get("jdbcUrl");
                    if (url instanceof String s) {
                        mutable.put("jdbcUrl", appendSslDisable(s));
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
        List<String> urls = new ArrayList<>();
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

    private void ensureMutableConnection(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            config.put("connection", new LinkedHashMap<>(toStringKeyMap(map)));
            return;
        }
        if (connection instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            boolean changed = false;
            for (Object item : list) {
                if (item instanceof Map<?, ?> mapItem) {
                    normalized.add(new LinkedHashMap<>(toStringKeyMap(mapItem)));
                    changed = true;
                } else {
                    normalized.add(item);
                }
            }
            if (changed) {
                config.put("connection", normalized);
            }
        }
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return copy;
        }
        source.forEach((k, v) -> {
            if (k != null) {
                copy.put(k.toString(), v);
            }
        });
        return copy;
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
            return "jdbc:dm://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
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
            List<String> cleaned = list.stream().map(this::normalizeText).filter(StringUtils::hasText).toList();
            if (cleaned.isEmpty()) {
                return raw;
            }
            return preferList ? cleaned : cleaned.get(0);
        }
        if (raw instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                try {
                    List<String> parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                    List<String> cleaned = parsed.stream().map(this::normalizeText).filter(StringUtils::hasText).toList();
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
            return firstStringValue(connMap.get("jdbcUrl"));
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
}
