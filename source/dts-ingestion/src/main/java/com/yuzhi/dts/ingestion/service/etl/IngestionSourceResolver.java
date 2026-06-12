package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.etl.api.ApiConnectorTypes;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IngestionSourceResolver {

    private static final Map<String, String> JDBC_TYPE_READERS = Map.ofEntries(
        Map.entry("dm", "rdbmsreader"),
        Map.entry("dameng", "rdbmsreader"),
        Map.entry("dm8", "rdbmsreader"),
        Map.entry("dameng8", "rdbmsreader"),
        Map.entry("postgres", "postgresqlreader"),
        Map.entry("postgresql", "postgresqlreader"),
        Map.entry("pg", "postgresqlreader"),
        Map.entry("mysql", "mysqlreader"),
        Map.entry("mariadb", "mysqlreader"),
        Map.entry("oracle", "oraclereader"),
        Map.entry("sqlserver", "sqlserverreader"),
        Map.entry("mssql", "sqlserverreader"),
        Map.entry("clickhouse", "clickhousereader"),
        Map.entry("hive", "hivereader"),
        Map.entry("db2", "db2reader"),
        Map.entry("sqlite", "sqlitereader"),
        Map.entry("jdbc", "rdbmsreader"),
        Map.entry("excel", "excelreader"),
        Map.entry("csv", "txtfilereader"),
        Map.entry("json", "jsonreader"),
        Map.entry("api", "httpreader"),
        Map.entry("http", "httpreader"),
        Map.entry("https", "httpreader"),
        Map.entry("http_api", "httpreader"),
        Map.entry("api_http", "httpreader"),
        Map.entry("rest", "httpreader"),
        Map.entry("rest_api", "httpreader"),
        Map.entry("httpreader", "httpreader"),
        Map.entry("file", "txtfilereader")
    );

    private static final Map<String, String> JDBC_URL_READERS = Map.ofEntries(
        Map.entry("jdbc:dm:", "rdbmsreader"),
        Map.entry("jdbc:postgresql:", "postgresqlreader"),
        Map.entry("jdbc:mysql:", "mysqlreader"),
        Map.entry("jdbc:mariadb:", "mysqlreader"),
        Map.entry("jdbc:oracle:", "oraclereader"),
        Map.entry("jdbc:sqlserver:", "sqlserverreader"),
        Map.entry("jdbc:clickhouse:", "clickhousereader"),
        Map.entry("jdbc:hive2:", "hivereader"),
        Map.entry("jdbc:db2:", "db2reader"),
        Map.entry("jdbc:sqlite:", "sqlitereader")
    );

    private final PlatformInfraClient platformInfraClient;

    public IngestionSourceResolver(PlatformInfraClient platformInfraClient) {
        this.platformInfraClient = platformInfraClient;
    }

    public ResolvedSource resolve(UUID dataSourceId, List<String> tables) {
        PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(dataSourceId);
        Map<String, Object> props = safeMap(detail.props());
        if (isApiSource(detail, props)) {
            return resolveApiSource(detail, props, tables);
        }
        String jdbcUrl = resolveJdbcUrl(detail, props);
        Map<String, Object> readerConfig = resolveReaderConfig(props);
        applyJdbcConnection(readerConfig, jdbcUrl);
        applyCredentials(readerConfig, detail);
        applySecrets(readerConfig, detail.secrets());
        applyTables(readerConfig, tables);
        String readerType = resolveReaderType(detail, props, readerConfig, jdbcUrl);
        return new ResolvedSource(readerType, readerConfig, detail);
    }

    public ApiConnectionInfo resolveApiInfo(UUID dataSourceId) {
        PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(dataSourceId);
        Map<String, Object> props = safeMap(detail.props());
        if (!isApiSource(detail, props)) {
            throw new IllegalArgumentException("数据源不是 API 类型: " + dataSourceId);
        }
        return resolveApiInfo(detail, props);
    }

    public JdbcMetadataService.JdbcConnectionInfo resolveJdbcInfo(UUID dataSourceId) {
        PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(dataSourceId);
        Map<String, Object> props = safeMap(detail.props());
        String jdbcUrl = resolveJdbcUrl(detail, props);
        String username = StringUtils.hasText(detail.username()) ? detail.username().trim() : null;
        String password = extractSecret(detail.secrets(), "password");
        String driver = extractString(props, "driverClass", "driver");
        String driverVersion = extractString(props, "driverVersion");
        Map<String, String> jdbcProps = resolveJdbcProperties(props);
        return new JdbcMetadataService.JdbcConnectionInfo(jdbcUrl, username, password, driver, driverVersion, jdbcProps);
    }

    private Map<String, Object> resolveReaderConfig(Map<String, Object> props) {
        Map<String, Object> readerConfig = extractMap(props, "readerConfig");
        if (!readerConfig.isEmpty()) {
            return readerConfig;
        }
        Map<String, Object> readerNode = extractMap(props, "reader");
        if (!readerNode.isEmpty()) {
            Map<String, Object> parameter = extractMap(readerNode, "parameter", "config");
            if (!parameter.isEmpty()) {
                return parameter;
            }
            Map<String, Object> copy = new LinkedHashMap<>(readerNode);
            copy.remove("type");
            copy.remove("name");
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private ResolvedSource resolveApiSource(
        PlatformInfraClient.DataSourceDetail detail,
        Map<String, Object> props,
        List<String> tables
    ) {
        ApiConnectionInfo apiInfo = resolveApiInfo(detail, props);
        Map<String, Object> readerConfig = new LinkedHashMap<>();
        readerConfig.put("readerType", ApiConnectorTypes.DEFAULT_READER_TYPE);
        readerConfig.put("connectorType", ApiConnectorTypes.CONNECTOR_TYPE);
        readerConfig.put("sourceDataSourceId", detail.id() == null ? null : detail.id().toString());
        if (StringUtils.hasText(apiInfo.baseUrl())) {
            readerConfig.put("baseUrl", apiInfo.baseUrl());
        }
        readerConfig.put("authProvider", apiInfo.authProvider());
        if (!apiInfo.authConfig().isEmpty()) {
            readerConfig.put("auth", apiInfo.authConfig());
        }
        if (!apiInfo.defaultHeaders().isEmpty()) {
            readerConfig.put("defaultHeaders", apiInfo.defaultHeaders());
        }
        if (!apiInfo.secrets().isEmpty()) {
            readerConfig.put("secrets", apiInfo.secrets());
        }
        if (!apiInfo.props().isEmpty()) {
            readerConfig.put("props", apiInfo.props());
        }
        if (tables != null && !tables.isEmpty()) {
            readerConfig.put("resources", tables);
        }
        return new ResolvedSource(ApiConnectorTypes.DEFAULT_READER_TYPE, readerConfig, detail);
    }

    private ApiConnectionInfo resolveApiInfo(PlatformInfraClient.DataSourceDetail detail, Map<String, Object> props) {
        Map<String, Object> safeProps = safeMap(props);
        Map<String, Object> authConfig = resolveAuthConfig(safeProps);
        String authProvider = resolveAuthProvider(safeProps, authConfig);
        if (!authConfig.isEmpty()) {
            authConfig.putIfAbsent("provider", authProvider);
        }
        return new ApiConnectionInfo(
            detail.id(),
            detail.name(),
            resolveApiBaseUrl(safeProps),
            authProvider,
            authConfig,
            resolveDefaultHeaders(safeProps),
            safeProps,
            safeMap(detail.secrets())
        );
    }

    private boolean isApiSource(PlatformInfraClient.DataSourceDetail detail, Map<String, Object> props) {
        if (detail != null && ApiConnectorTypes.isApiSourceType(detail.type())) {
            return true;
        }
        String connectorType = extractString(props, "connectorType", "readerType", "sourceCategory");
        return ApiConnectorTypes.isApiSourceType(connectorType);
    }

    private String resolveApiBaseUrl(Map<String, Object> props) {
        String direct = extractString(props, "baseUrl", "baseURL");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        Map<String, Object> api = extractMap(props, "api");
        return extractString(api, "baseUrl", "baseURL");
    }

    private String resolveAuthProvider(Map<String, Object> props, Map<String, Object> authConfig) {
        String direct = extractString(props, "authProvider");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        String fromAuth = extractString(authConfig, "provider");
        if (StringUtils.hasText(fromAuth)) {
            return fromAuth;
        }
        Map<String, Object> api = extractMap(props, "api");
        String nestedDirect = extractString(api, "authProvider");
        if (StringUtils.hasText(nestedDirect)) {
            return nestedDirect;
        }
        return "none";
    }

    private Map<String, Object> resolveAuthConfig(Map<String, Object> props) {
        Map<String, Object> auth = extractMap(props, "auth");
        if (!auth.isEmpty()) {
            return auth;
        }
        Map<String, Object> api = extractMap(props, "api");
        return extractMap(api, "auth");
    }

    private Map<String, Object> resolveDefaultHeaders(Map<String, Object> props) {
        Map<String, Object> headers = extractMap(props, "defaultHeaders", "headers");
        if (!headers.isEmpty()) {
            return headers;
        }
        Map<String, Object> api = extractMap(props, "api");
        return extractMap(api, "defaultHeaders", "headers");
    }

    private String resolveReaderType(
        PlatformInfraClient.DataSourceDetail detail,
        Map<String, Object> props,
        Map<String, Object> readerConfig,
        String jdbcUrl
    ) {
        String fromProps = extractString(props, "readerType", "reader");
        if (StringUtils.hasText(fromProps)) {
            return normalizeReaderType(fromProps);
        }
        String fromConfig = extractString(readerConfig, "readerType", "type", "name");
        if (StringUtils.hasText(fromConfig)) {
            return normalizeReaderType(fromConfig);
        }
        String type = detail.type() == null ? null : detail.type().trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(type) && JDBC_TYPE_READERS.containsKey(type)) {
            return normalizeReaderType(JDBC_TYPE_READERS.get(type));
        }
        if (StringUtils.hasText(jdbcUrl)) {
            String lower = jdbcUrl.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : JDBC_URL_READERS.entrySet()) {
                if (lower.startsWith(entry.getKey())) {
                    return normalizeReaderType(entry.getValue());
                }
            }
            return "rdbmsreader";
        }
        return null;
    }

    private String normalizeReaderType(String readerType) {
        if (!StringUtils.hasText(readerType)) {
            return readerType;
        }
        String normalized = readerType.trim();
        if ("dmreader".equalsIgnoreCase(normalized)) {
            return "rdbmsreader";
        }
        return normalized;
    }

    private void applyJdbcConnection(Map<String, Object> readerConfig, String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return;
        }
        String normalizedUrl = jdbcUrl.trim();
        if (readerConfig == null) {
            return;
        }
        Object connection = readerConfig.get("connection");
        if (connection instanceof Map<?, ?> map) {
            ensureJdbcUrl(map, normalizedUrl);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    ensureJdbcUrl(entryMap, normalizedUrl);
                }
            }
            return;
        }
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("jdbcUrl", List.of(normalizedUrl));
        List<String> tables = extractTables(readerConfig);
        if (!tables.isEmpty()) {
            conn.put("table", tables);
        }
        readerConfig.put("connection", List.of(conn));
    }

    private void ensureJdbcUrl(Map<?, ?> map, String jdbcUrl) {
        if (map == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutable = (Map<Object, Object>) map;
        mutable.put("jdbcUrl", List.of(jdbcUrl));
    }

    private void applySecrets(Map<String, Object> readerConfig, Map<String, Object> secrets) {
        if (readerConfig == null || secrets == null || secrets.isEmpty()) {
            return;
        }
        secrets.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            readerConfig.putIfAbsent(key, value);
        });
    }

    private void applyCredentials(Map<String, Object> readerConfig, PlatformInfraClient.DataSourceDetail detail) {
        if (readerConfig == null || detail == null) {
            return;
        }
        if (StringUtils.hasText(detail.username())) {
            readerConfig.putIfAbsent("username", detail.username().trim());
        }
        String password = extractSecret(detail.secrets(), "password");
        if (StringUtils.hasText(password)) {
            readerConfig.putIfAbsent("password", password);
        }
    }

    private void applyTables(Map<String, Object> readerConfig, List<String> tables) {
        if (readerConfig == null || tables == null || tables.isEmpty()) {
            return;
        }
        Object connection = readerConfig.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTables(map, tables);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTables(entryMap, tables);
                }
            }
            return;
        }
        readerConfig.put("table", tables);
    }

    private void setTables(Map<?, ?> map, List<String> tables) {
        if (map == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutable = (Map<Object, Object>) map;
        mutable.put("table", tables);
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        Object direct = config.get("table");
        if (direct instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
        }
        if (direct instanceof String str && StringUtils.hasText(str)) {
            return List.of(str);
        }
        return List.of();
    }

    private String resolveJdbcUrl(PlatformInfraClient.DataSourceDetail detail, Map<String, Object> props) {
        if (detail != null && StringUtils.hasText(detail.jdbcUrl())) {
            return detail.jdbcUrl().trim();
        }
        if (props == null || props.isEmpty()) {
            return null;
        }
        Object raw = props.get("jdbcUrl");
        if (raw == null) {
            raw = props.get("jdbc_url");
        }
        if (raw == null) {
            raw = props.get("url");
        }
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, String> resolveJdbcProperties(Map<String, Object> props) {
        Object jdbcProps = props.get("jdbcProperties");
        if (!(jdbcProps instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                resolved.put(k.toString(), v.toString());
            }
        });
        return resolved;
    }

    private Map<String, Object> extractMap(Map<String, Object> props, String... keys) {
        if (props == null || props.isEmpty() || keys == null) {
            return new LinkedHashMap<>();
        }
        for (String key : keys) {
            Object value = props.get(key);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> resolved = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        resolved.put(k.toString(), v);
                    }
                });
                return resolved;
            }
        }
        return new LinkedHashMap<>();
    }

    private String extractString(Map<String, Object> props, String... keys) {
        if (props == null || props.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = props.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                Object type = map.get("type");
                if (type != null && StringUtils.hasText(type.toString())) {
                    return type.toString().trim();
                }
            } else if (StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private String extractSecret(Map<String, Object> secrets, String key) {
        if (secrets == null || key == null) {
            return null;
        }
        Object value = secrets.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Map<String, Object> safeMap(Map<String, Object> input) {
        return input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input);
    }

    public record ApiConnectionInfo(
        UUID id,
        String name,
        String baseUrl,
        String authProvider,
        Map<String, Object> authConfig,
        Map<String, Object> defaultHeaders,
        Map<String, Object> props,
        Map<String, Object> secrets
    ) {}

    public record ResolvedSource(String readerType, Map<String, Object> readerConfig, PlatformInfraClient.DataSourceDetail detail) {}
}
