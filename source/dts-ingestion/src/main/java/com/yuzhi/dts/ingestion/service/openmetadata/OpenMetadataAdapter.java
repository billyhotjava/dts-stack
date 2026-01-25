package com.yuzhi.dts.ingestion.service.openmetadata;

import com.yuzhi.dts.ingestion.config.OpenMetadataProperties;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public record StreamRef(String name, String namespace) {}

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
        if (context == null || context.streams() == null || context.streams().isEmpty()) {
            result.put("status", "skipped");
            result.put("message", "未发现可用于血缘的表");
            return result;
        }

        String sourceService = firstNonEmpty(settings.getString("sourceServiceName", null), properties.getSourceServiceName(), context.sourceType());
        String destinationService = firstNonEmpty(settings.getString("destinationServiceName", null), properties.getDestinationServiceName());
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
            String destinationTable = buildDestinationTable(context.prefix(), stream.name());
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
            null
        );
        Map<String, Object> connectionConfig = buildConnectionConfig(serviceType, context.destinationConfig());
        if (connectionConfig == null || connectionConfig.isEmpty()) {
            result.put("enabled", true);
            result.put("status", "skipped");
            result.put("message", "目标端连接信息不完整");
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
        String database = getConfigValue(config, List.of("database", "db", "dbname"));
        return normalize(database);
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
        if (!StringUtils.hasText(service) || !StringUtils.hasText(database) || !StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return null;
        }
        return String.join(".", service.trim(), database.trim(), schema.trim(), table.trim());
    }

    private Map<String, Object> buildConnectionConfig(String serviceType, Map<String, Object> config) {
        if (!StringUtils.hasText(serviceType) || config == null || config.isEmpty()) {
            return Map.of();
        }
        String host = getConfigValue(config, List.of("host", "hostname"));
        String port = getConfigValue(config, List.of("port"));
        String database = getConfigValue(config, List.of("database", "db", "dbname"));
        String username = getConfigValue(config, List.of("username", "user"));
        String password = getConfigValue(config, List.of("password", "pass"));
        if (!StringUtils.hasText(host) || !StringUtils.hasText(port) || !StringUtils.hasText(database) || !StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("type", serviceType);
        connection.put("hostPort", host.trim() + ":" + port.trim());
        connection.put("database", database.trim());
        connection.put("username", username.trim());
        if (StringUtils.hasText(password)) {
            connection.put("authType", Map.of("password", password));
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

    private String getConfigValue(Map<String, Object> config, List<String> keys) {
        if (config == null || config.isEmpty()) {
            return null;
        }
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
}
