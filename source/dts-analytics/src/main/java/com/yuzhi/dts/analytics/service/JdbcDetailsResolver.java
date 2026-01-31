package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JdbcDetailsResolver {

    private final PlatformInfraClient platformInfraClient;

    public JdbcDetailsResolver(PlatformInfraClient platformInfraClient) {
        this.platformInfraClient = platformInfraClient;
    }

    public JdbcDetails resolve(String engine, JsonNode details) {
        if (details == null || !details.isObject()) {
            throw new IllegalArgumentException("details must be a map");
        }

        String platformId = resolvePlatformDataSourceId(details);
        if (platformId != null) {
            UUID id = parsePlatformUuid(platformId);
            PlatformInfraClient.DataSourceDetail detail = platformInfraClient.fetchDataSourceDetail(id);
            String jdbcUrl = detail.jdbcUrl();
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

        String jdbcUrl = firstText(details, "jdbc-url", "jdbc_url", "jdbcUrl", "url");
        if (jdbcUrl != null && jdbcUrl.startsWith("dbc:")) {
            jdbcUrl = "jdbc:" + jdbcUrl.substring(4);
        }
        String username = firstText(details, "user", "username");
        String password = firstText(details, "password");

        if (jdbcUrl == null && "postgres".equalsIgnoreCase(engine)) {
            String host = firstText(details, "host");
            Integer port = firstInt(details, "port").orElse(5432);
            String dbName = firstText(details, "dbname", "db", "database");
            if (host == null || dbName == null) {
                throw new IllegalArgumentException("Postgres database requires details.host and details.dbname");
            }
            jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(host, port, dbName);
        }

        if (jdbcUrl == null && "mysql".equalsIgnoreCase(engine)) {
            String host = firstText(details, "host");
            Integer port = firstInt(details, "port").orElse(3306);
            String dbName = firstText(details, "dbname", "db", "database");
            if (host == null || dbName == null) {
                throw new IllegalArgumentException("MySQL database requires details.host and details.dbname");
            }
            jdbcUrl = "jdbc:mysql://%s:%d/%s".formatted(host, port, dbName);
        }

        if (jdbcUrl == null && "oracle".equalsIgnoreCase(engine)) {
            String host = firstText(details, "host");
            Integer port = firstInt(details, "port").orElse(1521);
            String serviceName = firstText(details, "service-name", "service_name", "serviceName", "service");
            String sid = firstText(details, "sid", "database");
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

    public record JdbcDetails(String jdbcUrl, String username, String password) {
    }
}
