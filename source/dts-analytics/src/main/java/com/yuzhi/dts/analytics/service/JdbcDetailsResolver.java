package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JdbcDetailsResolver {

    public JdbcDetails resolve(String engine, JsonNode details) {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine is required");
        }
        if (details == null || !details.isObject()) {
            throw new IllegalArgumentException("details must be a map");
        }

        String jdbcUrl = firstText(details, "jdbc-url", "jdbc_url", "jdbcUrl", "url");
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

    public record JdbcDetails(String jdbcUrl, String username, String password) {}
}
