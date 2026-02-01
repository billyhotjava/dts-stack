package com.yuzhi.dts.platform.service.infra;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultDestinationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDestinationSyncService.class);

    private final AdminInfraClient adminInfraClient;

    public DefaultDestinationSyncService(AdminInfraClient adminInfraClient) {
        this.adminInfraClient = adminInfraClient;
    }

    public DefaultDestinationSnapshot ensureDefaultDestination() {
        AdminInfraClient.AdminDataLakeConfig lake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (lake == null) {
            return null;
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        if (!StringUtils.hasText(writerType)) {
            LOG.debug("Default data lake missing Addax writer type; skip default destination");
            return null;
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName(), "dts-addax-destination");
        return new DefaultDestinationSnapshot(writerType, destinationName, destinationConfig);
    }

    public DefaultDestinationStatus checkDefaultDestinationStatus() {
        AdminInfraClient.AdminDataLakeConfig lake = adminInfraClient.fetchDefaultDataLake().orElse(null);
        if (lake == null) {
            return DefaultDestinationStatus.missing("未配置默认数据湖");
        }
        Map<String, Object> destinationConfig = resolveDestinationConfig(lake);
        String writerType = resolveWriterType(lake, destinationConfig);
        boolean hasWriterType = StringUtils.hasText(writerType);
        boolean hasConfig = destinationConfig != null && !destinationConfig.isEmpty();
        String message = null;
        if (!hasWriterType) {
            message = "默认数据湖未配置写入器类型";
        } else if (!hasConfig) {
            message = "默认数据湖未配置写入器参数";
        }
        String destinationName = firstNonEmpty(lake.getDestinationName(), lake.getName());
        return new DefaultDestinationStatus(true, hasWriterType, hasConfig, destinationName, writerType, message);
    }

    private Map<String, Object> resolveDestinationConfig(AdminInfraClient.AdminDataLakeConfig lake) {
        if (lake == null || lake.getDestinationConfig() == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> config = new LinkedHashMap<>(lake.getDestinationConfig());
        String jdbcUrl = normalize(config.get("jdbcUrl"));
        if (!StringUtils.hasText(jdbcUrl)) {
            Object legacy = firstNonEmptyValue(config.get("jdbc_url"), config.get("url"), config.get("jdbc"), config.get("jdbcURL"));
            jdbcUrl = normalize(legacy);
            if (StringUtils.hasText(jdbcUrl)) {
                config.put("jdbcUrl", jdbcUrl);
            }
        }
        if (!StringUtils.hasText(jdbcUrl) && StringUtils.hasText(lake.getJdbcUrl())) {
            jdbcUrl = lake.getJdbcUrl();
            config.put("jdbcUrl", jdbcUrl);
        }
        if (!StringUtils.hasText(jdbcUrl)) {
            String host = normalize(config.get("host"));
            String database = normalize(config.get("database"));
            String port = normalize(config.get("port"));
            String writerType = normalize(
                firstNonEmpty(
                    normalize(config.get("writerType")),
                    normalize(config.get("type")),
                    lake.getDestinationDefinitionId()
                )
            );
            String built = buildJdbcUrl(writerType, host, port, database);
            if (StringUtils.hasText(built)) {
                config.put("jdbcUrl", built);
            }
        }
        if (!StringUtils.hasText(normalize(config.get("username"))) && StringUtils.hasText(lake.getUsername())) {
            config.put("username", lake.getUsername());
        }
        if (!StringUtils.hasText(normalize(config.get("password"))) && StringUtils.hasText(lake.getPassword())) {
            config.put("password", lake.getPassword());
        }
        return config;
    }

    private String resolveWriterType(AdminInfraClient.AdminDataLakeConfig lake, Map<String, Object> config) {
        String writerType = normalize(lake == null ? null : lake.getDestinationDefinitionId());
        if (!StringUtils.hasText(writerType) && config != null) {
            writerType = normalize(config.get("writerType"));
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("writer"));
            }
            if (!StringUtils.hasText(writerType)) {
                writerType = normalize(config.get("type"));
            }
        }
        if (StringUtils.hasText(writerType) && config != null && !StringUtils.hasText(normalize(config.get("writerType")))) {
            config.put("writerType", writerType);
        }
        return writerType;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonEmpty(String... values) {
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

    private Object firstNonEmptyValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = normalize(value);
            if (StringUtils.hasText(text)) {
                return value;
            }
        }
        return null;
    }

    private String buildJdbcUrl(String writerType, String host, String port, String database) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(database)) {
            return null;
        }
        String type = normalize(writerType);
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String lower = type.toLowerCase();
        String resolvedPort = StringUtils.hasText(port) ? port.trim() : null;
        if (lower.contains("postgres")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            return "jdbc:mysql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        if (lower.contains("oracle")) {
            return "jdbc:oracle:thin:@" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ":" + database;
        }
        if (lower.contains("sqlserver") || lower.contains("mssql")) {
            return "jdbc:sqlserver://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + ";databaseName=" + database;
        }
        if (lower.contains("dm")) {
            return "jdbc:dm://" + host + (resolvedPort == null ? "" : ":" + resolvedPort);
        }
        if (lower.contains("rdbms")) {
            return "jdbc:postgresql://" + host + (resolvedPort == null ? "" : ":" + resolvedPort) + "/" + database;
        }
        return null;
    }

    public record DefaultDestinationSnapshot(
        String destinationDefinitionId,
        String destinationName,
        Map<String, Object> destinationConfig
    ) {
        public DefaultDestinationSnapshot {
            destinationConfig = destinationConfig == null ? Map.of() : new LinkedHashMap<>(destinationConfig);
        }

        public boolean isEmpty() {
            return !StringUtils.hasText(destinationDefinitionId)
                && !StringUtils.hasText(destinationName)
                && (destinationConfig == null || destinationConfig.isEmpty());
        }
    }

    public record DefaultDestinationStatus(
        boolean available,
        boolean writerTypeReady,
        boolean writerConfigReady,
        String destinationName,
        String writerType,
        String message
    ) {
        public static DefaultDestinationStatus missing(String message) {
            return new DefaultDestinationStatus(false, false, false, null, null, message);
        }
    }
}
