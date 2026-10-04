package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JdbcDetailsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcDetailsResolver.class);

    private final PlatformInfraClient platformInfraClient;
    private final String pgHost;
    private final Environment environment;

    public JdbcDetailsResolver(
            PlatformInfraClient platformInfraClient,
            @Value("${PG_HOST:dts-pg}") String pgHost,
            Environment environment) {
        this.platformInfraClient = platformInfraClient;
        this.pgHost = pgHost;
        this.environment = environment;
    }

    public JdbcDetails resolve(String engine, JsonNode details) {
        if (details == null || !details.isObject()) {
            throw new IllegalArgumentException("details must be a map");
        }

        Long dataSourceId = resolveDataSourceId(details);
        if (dataSourceId != null) {
            PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(dataSourceId);
            String jdbcUrl = normalizePlatformJdbcUrl(detail.jdbcUrl());
            if (!StringUtils.hasText(jdbcUrl)) {
                throw new IllegalArgumentException("数据源未配置 JDBC URL");
            }
            return new JdbcDetails(jdbcUrl, detail.username(), resolvePlatformPassword(detail.secrets()));
        }

        String platformId = resolvePlatformDataSourceId(details);
        if (platformId != null) {
            UUID id = parsePlatformUuid(platformId);
            PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(id);
            String jdbcUrl = normalizePlatformJdbcUrl(detail.jdbcUrl());
            if (!StringUtils.hasText(jdbcUrl)) {
                throw new IllegalArgumentException("平台数据源未配置 JDBC URL");
            }
            String username = detail.username();
            String password = resolvePlatformPassword(detail.secrets());
            return new JdbcDetails(jdbcUrl, username, password);
        }

        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine is required");
        }

        String jdbcUrl = resolveConfiguredText(firstText(details, "jdbc-url", "jdbc_url", "jdbcUrl", "url"));
        if (jdbcUrl != null && jdbcUrl.startsWith("dbc:")) {
            jdbcUrl = "jdbc:" + jdbcUrl.substring(4);
        }
        String username = resolveConfiguredText(firstText(details, "user", "username"));
        String password = resolveConfiguredText(firstText(details, "password"));

        if (jdbcUrl == null && "postgres".equalsIgnoreCase(engine)) {
            String host = resolveConfiguredText(firstText(details, "host", "hostname", "hostName"));
            Integer port = firstInt(details, "port").orElse(5432);
            String dbName = resolveConfiguredText(firstText(details, "dbname", "db", "database", "dbName", "databaseName"));
            if (host == null || dbName == null) {
                throw new IllegalArgumentException("Postgres database requires details.host and details.dbname");
            }
            jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(host, port, dbName);
        }

        if (jdbcUrl == null && "mysql".equalsIgnoreCase(engine)) {
            String host = resolveConfiguredText(firstText(details, "host", "hostname", "hostName"));
            Integer port = firstInt(details, "port").orElse(3306);
            String dbName = resolveConfiguredText(firstText(details, "dbname", "db", "database", "dbName", "databaseName"));
            if (host == null || dbName == null) {
                throw new IllegalArgumentException("MySQL database requires details.host and details.dbname");
            }
            jdbcUrl = "jdbc:mysql://%s:%d/%s".formatted(host, port, dbName);
        }

        if (jdbcUrl == null && "oracle".equalsIgnoreCase(engine)) {
            String host = resolveConfiguredText(firstText(details, "host", "hostname", "hostName"));
            Integer port = firstInt(details, "port").orElse(1521);
            String serviceName = resolveConfiguredText(firstText(details, "service-name", "service_name", "serviceName", "service"));
            String sid = resolveConfiguredText(firstText(details, "sid", "database"));
            if (host == null) {
                throw new IllegalArgumentException("Oracle database requires details.host");
            }
            if (serviceName != null) {
                jdbcUrl = "jdbc:oracle:thin:@//%s:%d/%s".formatted(host, port, serviceName);
            } else if (sid != null) {
                jdbcUrl = "jdbc:oracle:thin:@%s:%d:%s".formatted(host, port, sid);
            } else {
                throw new IllegalArgumentException("Oracle database requires details.service-name or details.sid");
            }
        }

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("Missing details.jdbc-url for engine=" + engine);
        }

        return new JdbcDetails(jdbcUrl, username, password);
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String resolveConfiguredText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String resolved = environment.resolvePlaceholders(value);
        return StringUtils.hasText(resolved) ? resolved.trim() : resolved;
    }

    private static Optional<Integer> firstInt(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        for (String name : fieldNames) {
            JsonNode value = node.get(name);
            if (value == null) {
                continue;
            }
            if (value.canConvertToInt()) {
                return Optional.of(value.asInt());
            }
            if (value.isTextual()) {
                try {
                    return Optional.of(Integer.parseInt(value.asText()));
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        return Optional.empty();
    }

    private Long resolveDataSourceId(JsonNode details) {
        JsonNode direct = details.get("dataSourceId");
        if (direct == null) {
            direct = details.get("datasourceId");
        }
        if (direct == null) {
            return null;
        }
        if (direct.canConvertToLong()) {
            return direct.asLong();
        }
        if (direct.isTextual()) {
            try {
                return Long.parseLong(direct.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String resolvePlatformDataSourceId(JsonNode details) {
        String direct = firstText(details, "platformDataSourceId", "platform_data_source_id", "platformDataSourceID");
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        JsonNode platform = details.get("platform");
        if (platform != null && platform.isObject()) {
            String nested = firstText(platform, "dataSourceId", "datasourceId", "id");
            if (StringUtils.hasText(nested)) {
                return nested.trim();
            }
        }
        return null;
    }

    private UUID parsePlatformUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("无效的平台数据源 ID: " + raw);
        }
    }

    private String resolvePlatformPassword(Map<String, Object> secrets) {
        if (secrets == null || secrets.isEmpty()) {
            return null;
        }
        Object pwd = secrets.get("password");
        if (pwd == null) {
            pwd = secrets.get("pwd");
        }
        if (pwd == null) {
            pwd = secrets.get("pass");
        }
        if (pwd == null) {
            return null;
        }
        String text = pwd.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The admin default biadmin data-lake may expose an ephemeral docker bridge IP.
     * Convert it to PG_HOST so analytics remains stable across container IP changes.
     */
    private String normalizePlatformJdbcUrl(String rawJdbcUrl) {
        if (!StringUtils.hasText(rawJdbcUrl)) {
            return rawJdbcUrl;
        }
        String jdbcUrl = rawJdbcUrl.trim();
        String lower = jdbcUrl.toLowerCase();
        if (!lower.startsWith("jdbc:postgresql://") || !lower.contains("/biadmin")) {
            return jdbcUrl;
        }

        int hostStart = "jdbc:postgresql://".length();
        int slash = jdbcUrl.indexOf('/', hostStart);
        if (slash <= hostStart) {
            return jdbcUrl;
        }
        String hostPort = jdbcUrl.substring(hostStart, slash);
        if (!StringUtils.hasText(hostPort)) {
            return jdbcUrl;
        }

        String host = hostPort;
        String portPart = "";
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            portPart = hostPort.substring(colon);
        }

        if (!isDockerBridgeIpv4(host) || !StringUtils.hasText(pgHost)) {
            return jdbcUrl;
        }

        LOG.debug("Normalize platform JDBC host from {} to {} for biadmin", host, pgHost);
        return "jdbc:postgresql://" + pgHost.trim() + portPart + jdbcUrl.substring(slash);
    }

    private boolean isDockerBridgeIpv4(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int first;
        int second;
        try {
            first = Integer.parseInt(parts[0]);
            second = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        return first == 172 && second >= 16 && second <= 31;
    }

    public record JdbcDetails(String jdbcUrl, String username, String password) {
    }
}
