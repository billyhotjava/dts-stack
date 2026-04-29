package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraConnector;
import com.yuzhi.dts.platform.repository.infra.InfraConnectorRepository;
import com.yuzhi.dts.platform.service.infra.dto.InfraConnectorDto;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ConnectorRegistryService {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorRegistryService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final InfraConnectorRepository connectorRepository;
    private final ObjectMapper objectMapper;

    public ConnectorRegistryService(InfraConnectorRepository connectorRepository, ObjectMapper objectMapper) {
        this.connectorRepository = connectorRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedBuiltInConnectors() {
        int created = 0;
        int updated = 0;
        for (BuiltInConnector spec : builtInConnectors()) {
            InfraConnector connector = connectorRepository.findByConnectorKeyIgnoreCase(spec.connectorKey()).orElseGet(InfraConnector::new);
            boolean isNew = connector.getId() == null;
            applySpec(connector, spec);
            connectorRepository.save(connector);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        if (created > 0) {
            LOG.info("Seeded {} built-in connector(s), refreshed {}", created, updated);
        }
    }

    public List<InfraConnectorDto> list(String category, boolean includeDisabled) {
        List<InfraConnector> connectors;
        if (includeDisabled) {
            connectors = connectorRepository.findAll();
            connectors.sort(
                java.util.Comparator
                    .comparing((InfraConnector item) -> item.getDisplayOrder() == null ? 1000 : item.getDisplayOrder())
                    .thenComparing(item -> normalize(item.getConnectorKey()))
            );
        } else if (StringUtils.hasText(category)) {
            connectors =
                connectorRepository.findByCategoryIgnoreCaseAndStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc(
                    category.trim(),
                    STATUS_ACTIVE
                );
        } else {
            connectors = connectorRepository.findByStatusIgnoreCaseOrderByDisplayOrderAscConnectorKeyAsc(STATUS_ACTIVE);
        }
        if (StringUtils.hasText(category) && includeDisabled) {
            String expected = category.trim();
            connectors = connectors.stream().filter(connector -> expected.equalsIgnoreCase(connector.getCategory())).toList();
        }
        return connectors.stream().map(this::toDto).toList();
    }

    public InfraConnectorDto get(String connectorKey) {
        if (!StringUtils.hasText(connectorKey)) {
            throw new EntityNotFoundException("connectorKey is required");
        }
        return connectorRepository.findByConnectorKeyIgnoreCase(connectorKey.trim()).map(this::toDto).orElseThrow(() ->
            new EntityNotFoundException("Connector not found: " + connectorKey)
        );
    }

    private void applySpec(InfraConnector connector, BuiltInConnector spec) {
        connector.setConnectorKey(spec.connectorKey());
        connector.setName(spec.name());
        connector.setCategory(spec.category());
        connector.setSourceType(spec.sourceType());
        connector.setDefaultEngine(spec.defaultEngine());
        connector.setStatus(STATUS_ACTIVE);
        connector.setDisplayOrder(spec.displayOrder());
        connector.setDescription(spec.description());
        connector.setCapabilitiesPayload(writeJson(spec.capabilities()));
        connector.setConfigSchemaPayload(writeJson(spec.configSchema()));
        connector.setSensitiveFieldsPayload(writeJson(spec.sensitiveFields()));
        connector.setCompatibilityPayload(writeJson(spec.compatibility()));
    }

    private InfraConnectorDto toDto(InfraConnector connector) {
        return new InfraConnectorDto(
            connector.getId(),
            connector.getConnectorKey(),
            connector.getName(),
            connector.getCategory(),
            connector.getSourceType(),
            connector.getDefaultEngine(),
            connector.getStatus(),
            connector.getDisplayOrder(),
            connector.getDescription(),
            readMap(connector.getCapabilitiesPayload()),
            readMap(connector.getConfigSchemaPayload()),
            readList(connector.getSensitiveFieldsPayload()),
            readMap(connector.getCompatibilityPayload()),
            connector.getCreatedDate(),
            connector.getLastModifiedDate()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize connector registry payload", ex);
        }
    }

    private Map<String, Object> readMap(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> readList(String payload) {
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<ArrayList<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private List<BuiltInConnector> builtInConnectors() {
        return List.of(
            jdbc("postgresql", "PostgreSQL", "postgres", "org.postgresql.Driver", 10, true, true),
            jdbc("mysql", "MySQL", "mysql", "com.mysql.cj.jdbc.Driver", 20, true, true),
            jdbc("oracle", "Oracle", "oracle", "oracle.jdbc.OracleDriver", 30, true, true),
            jdbc("sqlserver", "SQL Server", "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 40, true, true),
            jdbc("clickhouse", "ClickHouse", "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver", 45, true, false),
            jdbc("dm", "DM8 达梦", "dm", "dm.jdbc.driver.DmDriver", 50, true, true),
            jdbc("kingbase", "人大金仓", "kingbase", "com.kingbase8.Driver", 60, true, true),
            jdbc("gbase", "GBase", "gbase", "com.gbase.jdbc.Driver", 70, true, true),
            jdbc("hive", "Hive", "hive", "org.apache.hive.jdbc.HiveDriver", 80, true, false),
            jdbc("inceptor", "Inceptor", "inceptor", "com.transwarp.jdbc.InceptorDriver", 90, true, false),
            jdbc("jdbc", "通用 JDBC", "jdbc", "", 100, true, false),
            file("excel", "Excel", "excel", 200, List.of("file", "sheet", "headerRow", "targetTable")),
            file("csv", "CSV", "csv", 210, List.of("file", "delimiter", "encoding", "targetTable")),
            file("json", "JSON", "json", 220, List.of("file", "jsonPath", "targetTable")),
            api("http-api", "HTTP API", "httpreader", 300),
            external("sftp", "SFTP", "FILE", "FILE", 400),
            external("minio", "MinIO", "FILE", "FILE", 410),
            external("kafka", "Kafka", "STREAM", "FUTURE", 500)
        );
    }

    private BuiltInConnector jdbc(
        String connectorKey,
        String name,
        String sourceType,
        String driverClass,
        int displayOrder,
        boolean supportsIncremental,
        boolean supportsPrimaryKey
    ) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("connectionTest", true);
        capabilities.put("schemaDiscover", true);
        capabilities.put("samplePreview", true);
        capabilities.put("fullRefresh", true);
        capabilities.put("append", true);
        capabilities.put("timestampIncremental", supportsIncremental);
        capabilities.put("primaryKeyIncremental", supportsPrimaryKey);
        capabilities.put("cdc", false);
        capabilities.put("odsGeneration", true);
        capabilities.put("dbtSourceGeneration", true);

        Map<String, Object> configSchema = new LinkedHashMap<>();
        configSchema.put("required", List.of("jdbcUrl", "username", "password"));
        configSchema.put("optional", List.of("driverClass", "driverVersion", "schemas", "connectTimeoutSeconds", "queryTimeoutSeconds"));
        configSchema.put("defaults", Map.of("driverClass", driverClass));

        return new BuiltInConnector(
            connectorKey,
            name,
            "DATABASE",
            sourceType,
            "ADDAX",
            displayOrder,
            name + " 数据库批量接入连接器",
            capabilities,
            configSchema,
            List.of("password"),
            Map.of("deployment", List.of("docker-compose", "offline"), "arch", List.of("x86_64", "arm64"), "driverClass", driverClass)
        );
    }

    private BuiltInConnector file(String connectorKey, String name, String sourceType, int displayOrder, List<String> fields) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("connectionTest", false);
        capabilities.put("schemaDiscover", true);
        capabilities.put("samplePreview", true);
        capabilities.put("fullRefresh", true);
        capabilities.put("append", true);
        capabilities.put("timestampIncremental", false);
        capabilities.put("primaryKeyIncremental", false);
        capabilities.put("cdc", false);
        capabilities.put("odsGeneration", true);
        capabilities.put("dbtSourceGeneration", true);

        return new BuiltInConnector(
            connectorKey,
            name,
            "FILE",
            sourceType,
            "ADDAX",
            displayOrder,
            name + " 文件入湖连接器",
            capabilities,
            Map.of("required", fields, "optional", List.of("ownerDept", "encoding", "nullValue")),
            List.of(),
            Map.of("deployment", List.of("docker-compose", "offline"), "arch", List.of("x86_64", "arm64"))
        );
    }

    private BuiltInConnector api(String connectorKey, String name, String sourceType, int displayOrder) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("connectionTest", true);
        capabilities.put("schemaDiscover", true);
        capabilities.put("samplePreview", true);
        capabilities.put("fullRefresh", true);
        capabilities.put("append", true);
        capabilities.put("timestampIncremental", true);
        capabilities.put("primaryKeyIncremental", false);
        capabilities.put("cdc", false);
        capabilities.put("odsGeneration", true);
        capabilities.put("dbtSourceGeneration", true);
        capabilities.put("customPython", false);

        return new BuiltInConnector(
            connectorKey,
            name,
            "API",
            sourceType,
            "API_RUNTIME",
            displayOrder,
            "HTTP/REST API 数据接入连接器",
            capabilities,
            Map.of(
                "required",
                List.of("baseUrl", "method", "path", "targetTable"),
                "optional",
                List.of("authType", "headers", "queryParams", "body", "pagination", "cursorPath", "recordsPath")
            ),
            List.of("token", "apiKey", "authorization", "password", "secret"),
            Map.of("deployment", List.of("docker-compose", "offline"), "arch", List.of("x86_64", "arm64"))
        );
    }

    private BuiltInConnector external(String connectorKey, String name, String category, String defaultEngine, int displayOrder) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("connectionTest", true);
        capabilities.put("schemaDiscover", false);
        capabilities.put("samplePreview", false);
        capabilities.put("fullRefresh", true);
        capabilities.put("append", true);
        capabilities.put("timestampIncremental", false);
        capabilities.put("primaryKeyIncremental", false);
        capabilities.put("cdc", false);
        capabilities.put("odsGeneration", false);
        capabilities.put("dbtSourceGeneration", false);

        return new BuiltInConnector(
            connectorKey,
            name,
            category,
            connectorKey,
            defaultEngine,
            displayOrder,
            name + " 连接器能力声明",
            capabilities,
            Map.of("required", List.of("endpoint"), "optional", List.of("username", "password", "bucket", "path", "topic")),
            List.of("password", "secretKey", "accessKey", "token"),
            Map.of("deployment", List.of("docker-compose", "offline"), "arch", List.of("x86_64", "arm64"), "status", "reserved")
        );
    }

    private record BuiltInConnector(
        String connectorKey,
        String name,
        String category,
        String sourceType,
        String defaultEngine,
        Integer displayOrder,
        String description,
        Map<String, Object> capabilities,
        Map<String, Object> configSchema,
        List<String> sensitiveFields,
        Map<String, Object> compatibility
    ) {}
}
