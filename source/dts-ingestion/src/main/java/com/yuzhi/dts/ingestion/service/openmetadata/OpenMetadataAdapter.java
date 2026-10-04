package com.yuzhi.dts.ingestion.service.openmetadata;

import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.net.URI;
import java.net.URISyntaxException;
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
public class OpenMetadataAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMetadataAdapter.class);

    private final OpenMetadataClient client;
    private final OpenMetadataProperties properties;
    private final IngestionSettingsService settingsService;

    public OpenMetadataAdapter(OpenMetadataClient client, OpenMetadataProperties properties, IngestionSettingsService settingsService) {
        this.client = client;
        this.properties = properties;
        this.settingsService = settingsService;
    }

    public record LineageRequest(Boolean enabled, String domain, List<String> tags, String owner) {}

    public record StreamRef(String name, String namespace, String destinationName, String destinationNamespace) {
        public StreamRef(String name, String namespace) {
            this(name, namespace, null, null);
        }
    }

    public record LineageContext(
        String taskName,
        String sourceType,
        String namespaceTemplate,
        String prefix,
        Map<String, Object> sourceConfig,
        Map<String, Object> destinationConfig,
        List<StreamRef> streams
    ) {}

    public record IngestionContext(String taskName, Map<String, Object> destinationConfig, String scheduleCron, boolean runNow) {}

    public Map<String, Object> registerLineage(LineageRequest request, LineageContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_OPENMETADATA);
        boolean enabled = settings.getBoolean("enabled", properties.isEnabled());
        if (!enabled) {
            result.put("enabled", false);
            result.put("message", "OpenMetadata 未启用");
            return result;
        }
        if (request == null || !Boolean.TRUE.equals(request.enabled())) {
            result.put("enabled", false);
            result.put("message", "未启用元数据血缘");
            return result;
        }
        result.put("enabled", true);
        Map<String, Object> governance = summarizeGovernanceHints(request);
        if (!governance.isEmpty()) {
            result.put("governance", governance);
        }
        if (context == null || context.streams() == null || context.streams().isEmpty()) {
            result.put("status", "skipped");
            result.put("message", "未发现可用于血缘的表");
            return result;
        }

        String sourceService = firstNonEmpty(
            settings.getString("sourceServiceName", null),
            getConfigValue(context.sourceConfig(), List.of("serviceName", "openmetadataServiceName", "sourceServiceName")),
            properties.getSourceServiceName(),
            context.sourceType()
        );
        String destinationService = firstNonEmpty(
            settings.getString("destinationServiceName", null),
            getConfigValue(context.destinationConfig(), List.of("serviceName", "openmetadataServiceName", "destinationServiceName")),
            properties.getDestinationServiceName()
        );
        String sourceDatabase = firstNonEmpty(
            resolveDatabase(context.sourceConfig()),
            settings.getString("sourceDatabase", null),
            properties.getSourceDatabase()
        );
        String destinationDatabase = firstNonEmpty(
            resolveDatabase(context.destinationConfig()),
            settings.getString("destinationDatabase", null),
            properties.getDestinationDatabase(),
            sourceDatabase
        );

        if (!StringUtils.hasText(sourceService) || !StringUtils.hasText(destinationService)) {
            result.put("status", "skipped");
            result.put("message", "未配置 OpenMetadata 服务名称");
            return result;
        }

        int created = 0;
        int skipped = 0;
        List<Map<String, String>> edges = new ArrayList<>();
        for (StreamRef stream : context.streams()) {
            String sourceSchema = resolveSchema(stream, context.sourceConfig(), settings.getString("sourceSchema", properties.getSourceSchema()));
            String destinationSchema = resolveDestinationSchema(
                context.namespaceTemplate(),
                stream,
                sourceSchema,
                context.destinationConfig(),
                settings.getString("destinationSchema", properties.getDestinationSchema())
            );
            String sourceTable = stream.name();
            String destinationTable = firstNonEmpty(stream.destinationName(), buildDestinationTable(context.prefix(), stream.name()));
            String sourceFqn = buildFqn(sourceService, sourceDatabase, sourceSchema, sourceTable);
            String destinationFqn = buildFqn(destinationService, destinationDatabase, destinationSchema, destinationTable);
            if (!StringUtils.hasText(sourceFqn) || !StringUtils.hasText(destinationFqn)) {
                skipped++;
                continue;
            }
            String description = context.taskName() != null ? context.taskName() + " 入湖血缘" : null;
            String sourceId = resolveEntityId(sourceFqn);
            String destinationId = resolveEntityId(destinationFqn);
            if (!StringUtils.hasText(sourceId) || !StringUtils.hasText(destinationId)) {
                skipped++;
                continue;
            }
            if (client.upsertLineage(sourceId, destinationId, description).isPresent()) {
                created++;
                edges.add(Map.of("from", sourceFqn, "to", destinationFqn));
            } else {
                skipped++;
            }
        }

        result.put("status", created > 0 ? "success" : "skipped");
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("edges", edges);
        return result;
    }

    private Map<String, Object> summarizeGovernanceHints(LineageRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> hints = new LinkedHashMap<>();
        if (StringUtils.hasText(request.owner())) {
            hints.put("owner", request.owner().trim());
        }
        if (StringUtils.hasText(request.domain())) {
            hints.put("domain", request.domain().trim());
        }
        if (request.tags() != null && !request.tags().isEmpty()) {
            List<String> tags = request.tags().stream().map(this::normalize).filter(StringUtils::hasText).toList();
            if (!tags.isEmpty()) {
                hints.put("tags", tags);
            }
        }
        if (hints.isEmpty()) {
            return Map.of();
        }
        hints.put("status", "not_supported");
        hints.put("message", "owner/domain/tags 暂未写入 OpenMetadata，已在本次响应中显式回传");
        return hints;
    }

    public Map<String, Object> ensureMetadataIngestion(IngestionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_OPENMETADATA);
        boolean enabled = settings.getBoolean("enabled", properties.isEnabled());
        boolean ingestionEnabled = settings.getBoolean("ingestionEnabled", properties.isIngestionEnabled());
        if (!enabled || !ingestionEnabled) {
            result.put("enabled", false);
            result.put("message", "OpenMetadata 采集未启用");
            return result;
        }
        if (context == null || context.destinationConfig() == null || context.destinationConfig().isEmpty()) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "缺少目标端连接配置");
            return result;
        }

        String serviceName = firstNonEmpty(settings.getString("destinationServiceName", null), properties.getDestinationServiceName());
        if (!StringUtils.hasText(serviceName)) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "未配置 OpenMetadata 服务名称");
            return result;
        }

        String serviceType = resolveServiceType(
            settings.getString("destinationServiceType", properties.getDestinationServiceType()),
            resolveDestinationType(context.destinationConfig())
        );
        ConnectionDetails connectionDetails = resolveConnectionDetails(context.destinationConfig());
        Map<String, Object> connectionConfig = buildConnectionConfig(serviceType, connectionDetails);
        if (connectionConfig == null || connectionConfig.isEmpty()) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "目标端连接信息不完整");
            result.put("missing", connectionDetails.missingRequiredFields());
            return result;
        }

        Map<String, Object> service = client.getDatabaseServiceByName(serviceName).orElse(null);
        if (service == null || service.isEmpty()) {
            service = client.createDatabaseService(serviceName, serviceType, connectionConfig).orElse(null);
        }
        String serviceId = stringVal(service == null ? null : service.get("id"));
        if (!StringUtils.hasText(serviceId)) {
            result.put("enabled", true);
            result.put("status", "failed");
            result.put("message", "OpenMetadata 服务创建失败");
            return result;
        }

        String pipelineName = buildPipelineName(settings.getString("ingestionPrefix", properties.getIngestionPipelinePrefix()), serviceName);
        Map<String, Object> pipeline = client.getIngestionPipelineByName(pipelineName).orElse(null);
        if (pipeline == null || pipeline.isEmpty()) {
            String schedule = normalize(context.scheduleCron());
            if (!StringUtils.hasText(schedule)) {
                schedule = normalize(settings.getString("ingestionSchedule", properties.getIngestionDefaultSchedule()));
            }
            pipeline = client.createIngestionPipeline(pipelineName, serviceId, schedule, "metadata").orElse(null);
        }
        String pipelineId = stringVal(pipeline == null ? null : pipeline.get("id"));
        result.put("enabled", true);
        result.put("serviceName", serviceName);
        result.put("pipelineName", pipelineName);
        result.put("pipelineId", pipelineId);

        if (!StringUtils.hasText(pipelineId)) {
            result.put("status", "failed");
            result.put("message", "OpenMetadata 采集任务创建失败");
            return result;
        }

        if (context.runNow()) {
            Map<String, Object> trigger = client.triggerIngestionPipeline(pipelineId).orElse(null);
            if (trigger == null || trigger.isEmpty()) {
                result.put("status", "failed");
                result.put("message", "采集任务触发失败");
            } else {
                result.put("status", "triggered");
                result.put("trigger", trigger);
            }
        } else {
            result.put("status", "ready");
        }

        return result;
    }

    private String resolveEntityId(String fqn) {
        return client
            .getTableByFqn(fqn, properties.getTableFields())
            .map(payload -> stringVal(payload.get("id")))
            .orElse(null);
    }

    private String resolveSchema(StreamRef stream, Map<String, Object> sourceConfig, String fallback) {
        if (StringUtils.hasText(stream.namespace())) {
            return stream.namespace().trim();
        }
        String schema = resolveSchemaFromConfig(sourceConfig);
        return StringUtils.hasText(schema) ? schema : normalize(fallback);
    }

    private String resolveDestinationSchema(
        String template,
        StreamRef stream,
        String sourceSchema,
        Map<String, Object> destinationConfig,
        String fallback
    ) {
        String namespace = normalize(template);
        if (StringUtils.hasText(stream.destinationNamespace())) {
            return stream.destinationNamespace().trim();
        }
        if (StringUtils.hasText(namespace)) {
            String resolved = namespace.replace("${source}", StringUtils.hasText(stream.namespace()) ? stream.namespace() : sourceSchema);
            resolved = resolved.replace("${schema}", StringUtils.hasText(stream.namespace()) ? stream.namespace() : sourceSchema);
            return resolved;
        }
        String schema = resolveSchemaFromConfig(destinationConfig);
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        return StringUtils.hasText(sourceSchema) ? sourceSchema : normalize(fallback);
    }

    private String resolveDatabase(Map<String, Object> config) {
        return normalize(resolveConnectionDetails(config).database());
    }

    private String resolveSchemaFromConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String schema = getConfigValue(config, List.of("schema"));
        if (StringUtils.hasText(schema)) {
            return normalize(schema);
        }
        Object schemas = config.get("schemas");
        if (schemas instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first != null && StringUtils.hasText(first.toString())) {
                return first.toString().trim();
            }
        }
        return null;
    }

    private String buildDestinationTable(String prefix, String streamName) {
        if (!StringUtils.hasText(streamName)) {
            return null;
        }
        String resolvedPrefix = normalize(prefix);
        return StringUtils.hasText(resolvedPrefix) ? resolvedPrefix + streamName : streamName;
    }

    private String buildFqn(String service, String database, String schema, String table) {
        if (!StringUtils.hasText(service) || !StringUtils.hasText(database) || !StringUtils.hasText(table)) {
            return null;
        }
        if (StringUtils.hasText(schema)) {
            return String.join(".", service.trim(), database.trim(), schema.trim(), table.trim());
        }
        return String.join(".", service.trim(), database.trim(), table.trim());
    }

    private Map<String, Object> buildConnectionConfig(String serviceType, ConnectionDetails details) {
        if (!StringUtils.hasText(serviceType) || details == null || !details.isComplete()) {
            return Map.of();
        }
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("type", serviceType);
        connection.put("hostPort", details.hostPort());
        connection.put("database", details.database().trim());
        connection.put("username", details.username().trim());
        if (StringUtils.hasText(details.password())) {
            connection.put("authType", Map.of("password", details.password()));
        }
        connection.put("sslMode", "disable");
        return connection;
    }

    private String buildPipelineName(String prefix, String serviceName) {
        String normalized = normalize(prefix);
        if (!StringUtils.hasText(normalized)) {
            return serviceName;
        }
        return normalized + "_" + serviceName;
    }

    private String resolveServiceType(String fallback, String sourceType) {
        String normalized = normalize(sourceType);
        if (StringUtils.hasText(normalized)) {
            String lowered = normalized.toLowerCase();
            if (lowered.contains("mysql")) {
                return "Mysql";
            }
            if (lowered.contains("postgres")) {
                return "Postgres";
            }
            if (lowered.contains("oracle")) {
                return "Oracle";
            }
            if (lowered.contains("sqlserver") || lowered.contains("mssql")) {
                return "Mssql";
            }
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private String resolveDestinationType(Map<String, Object> config) {
        String explicit = getConfigValue(config, List.of("serviceType", "destinationServiceType", "writerType", "type", "name"));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String driver = getConfigValue(config, List.of("driver", "driverClass", "driverClassName"));
        if (StringUtils.hasText(driver)) {
            return driver;
        }
        return getConfigValue(config, List.of("jdbcUrl", "jdbc_url", "url"));
    }

    private ConnectionDetails resolveConnectionDetails(Map<String, Object> config) {
        String jdbcUrl = getConfigValue(config, List.of("jdbcUrl", "jdbc_url", "url"));
        JdbcParts jdbcParts = parseJdbcUrl(jdbcUrl);
        String hostPort = getConfigValue(config, List.of("hostPort", "hostport"));
        String host = firstNonEmpty(getConfigValue(config, List.of("host", "hostname", "server")), splitHost(hostPort), jdbcParts.host());
        String port = firstNonEmpty(getConfigValue(config, List.of("port")), splitPort(hostPort), jdbcParts.port());
        if (StringUtils.hasText(host) && host.contains(":") && !StringUtils.hasText(port)) {
            port = splitPort(host);
            host = splitHost(host);
        }
        String serviceType = resolveServiceType(null, resolveDestinationType(config));
        if (!StringUtils.hasText(port)) {
            port = defaultPort(serviceType);
        }
        String database = firstNonEmpty(
            getConfigValue(config, List.of("database", "db", "dbname", "databaseName", "schema")),
            jdbcParts.database()
        );
        String username = getConfigValue(config, List.of("username", "user", "userName"));
        String password = getConfigValue(config, List.of("password", "pass"));
        String schema = getConfigValue(config, List.of("schema"));
        return new ConnectionDetails(host, port, database, username, password, schema);
    }

    private JdbcParts parseJdbcUrl(String rawUrl) {
        String url = normalize(rawUrl);
        if (!StringUtils.hasText(url)) {
            return JdbcParts.empty();
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("jdbc:")) {
            return JdbcParts.empty();
        }
        int schemeEnd = url.indexOf(':', "jdbc:".length());
        if (schemeEnd < 0 || schemeEnd >= url.length() - 1) {
            return JdbcParts.empty();
        }
        String scheme = url.substring("jdbc:".length(), schemeEnd).toLowerCase(Locale.ROOT);
        String rest = url.substring(schemeEnd + 1);
        if ("sqlserver".equals(scheme)) {
            return parseSqlServerJdbc(rest);
        }
        if ("oracle".equals(scheme)) {
            return parseOracleJdbc(rest);
        }
        if (rest.startsWith("//")) {
            return parseUriJdbc(rest);
        }
        return JdbcParts.empty();
    }

    private JdbcParts parseUriJdbc(String rest) {
        try {
            URI uri = new URI(rest);
            String database = normalize(uri.getPath());
            if (StringUtils.hasText(database) && database.startsWith("/")) {
                database = database.substring(1);
            }
            return new JdbcParts(uri.getHost(), uri.getPort() > 0 ? String.valueOf(uri.getPort()) : null, database);
        } catch (URISyntaxException ex) {
            return JdbcParts.empty();
        }
    }

    private JdbcParts parseSqlServerJdbc(String rest) {
        String text = rest.startsWith("//") ? rest.substring(2) : rest;
        String[] parts = text.split(";", -1);
        String hostPart = parts.length > 0 ? parts[0] : null;
        String host = splitHost(hostPart);
        String port = splitPort(hostPart);
        String database = null;
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = part.substring(0, idx).trim();
            if ("database".equalsIgnoreCase(key) || "databaseName".equalsIgnoreCase(key)) {
                database = part.substring(idx + 1).trim();
                break;
            }
        }
        return new JdbcParts(host, port, database);
    }

    private JdbcParts parseOracleJdbc(String rest) {
        String marker = "@//";
        int idx = rest.indexOf(marker);
        if (idx < 0) {
            return JdbcParts.empty();
        }
        return parseUriJdbc("//" + rest.substring(idx + marker.length()));
    }

    private String defaultPort(String serviceType) {
        String normalized = normalize(serviceType);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> "5432";
            case "mysql", "mariadb" -> "3306";
            case "mssql", "sqlserver" -> "1433";
            case "oracle" -> "1521";
            case "clickhouse" -> "8123";
            case "hive" -> "10000";
            default -> null;
        };
    }

    private String splitHost(String hostPort) {
        String value = normalize(hostPort);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int idx = value.indexOf(':');
        return idx > 0 ? value.substring(0, idx) : value;
    }

    private String splitPort(String hostPort) {
        String value = normalize(hostPort);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int idx = value.indexOf(':');
        return idx > -1 && idx < value.length() - 1 ? value.substring(idx + 1) : null;
    }

    private String getConfigValue(Map<String, Object> config, List<String> keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        for (Map<String, Object> candidateMap : configCandidates(config)) {
            String value = getDirectConfigValue(candidateMap, keys);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String getDirectConfigValue(Map<String, Object> config, List<String> keys) {
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    Object candidate = entry.getValue();
                    if (candidate != null && StringUtils.hasText(candidate.toString())) {
                        return candidate.toString();
                    }
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> configCandidates(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(config);
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            candidates.add(toStringObjectMap(map));
        } else if (connection instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    candidates.add(toStringObjectMap(map));
                }
            }
        }
        Object parameter = config.get("parameter");
        if (parameter instanceof Map<?, ?> map) {
            candidates.add(toStringObjectMap(map));
        }
        return candidates;
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (map == null) {
            return copy;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return copy;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
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

    private record JdbcParts(String host, String port, String database) {
        static JdbcParts empty() {
            return new JdbcParts(null, null, null);
        }
    }

    private record ConnectionDetails(String host, String port, String database, String username, String password, String schema) {
        boolean isComplete() {
            return StringUtils.hasText(hostPort()) && StringUtils.hasText(database) && StringUtils.hasText(username);
        }

        String hostPort() {
            if (!StringUtils.hasText(host)) {
                return null;
            }
            return StringUtils.hasText(port) ? host.trim() + ":" + port.trim() : host.trim();
        }

        List<String> missingRequiredFields() {
            List<String> missing = new ArrayList<>();
            if (!StringUtils.hasText(hostPort())) {
                missing.add("hostPort");
            }
            if (!StringUtils.hasText(database)) {
                missing.add("database");
            }
            if (!StringUtils.hasText(username)) {
                missing.add("username");
            }
            return missing;
        }
    }
}
