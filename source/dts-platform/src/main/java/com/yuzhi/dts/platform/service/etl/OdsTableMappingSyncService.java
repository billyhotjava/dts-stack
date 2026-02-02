package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OdsTableMappingSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(OdsTableMappingSyncService.class);
    private static final String DEFAULT_CODE = "unknown";
    private static final String DEFAULT_SCHEMA = "ods";

    private final InfraOdsTableMappingRepository mappingRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final DbtSourceService dbtSourceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public OdsTableMappingSyncService(
        InfraOdsTableMappingRepository mappingRepository,
        InfraDataSourceRepository dataSourceRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSyncService columnSyncService,
        DbtSourceService dbtSourceService,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.mappingRepository = mappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnSyncService = columnSyncService;
        this.dbtSourceService = dbtSourceService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyncResult syncFromIngestionPayload(Map<String, Object> payload) {
        Map<String, Object> task = unwrapTask(payload);
        if (task == null || task.isEmpty()) {
            return SyncResult.empty("未发现任务数据");
        }
        List<Map<String, String>> mappings = readTableMappings(task.get("tableMapping"));
        if (mappings.isEmpty()) {
            return SyncResult.empty("未发现表映射");
        }
        UUID connectionId = resolveConnectionId(task);
        if (connectionId == null) {
            return SyncResult.empty("未解析到数据源连接 ID");
        }
        Map<String, Object> destinationConfig = readMap(task.get("destinationConfig"));
        String taskName = normalize(task.get("name"));
        int updated = 0;
        List<ColumnSpec> columnSpecs = resolveColumnSpecs(connectionId);
        for (Map<String, String> mapping : mappings) {
            String source = normalize(mapping.get("source"));
            String target = normalize(mapping.get("target"));
            if (!StringUtils.hasText(source)) {
                continue;
            }
            TableRef sourceRef = splitTable(source);
            TableRef targetRef = splitTarget(target, destinationConfig);
            String namespace = normalizeNamespace(sourceRef.namespace());
            Optional<InfraOdsTableMapping> existing =
                mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
                    connectionId,
                    sourceRef.name(),
                    namespace
                );
            InfraOdsTableMapping entity = existing.orElseGet(InfraOdsTableMapping::new);
            entity.setConnectionId(connectionId);
            entity.setStreamName(sourceRef.name());
            entity.setStreamNamespace(namespace);
            OdsNameParts parts = parseOdsName(targetRef.table());
            entity.setSystemCode(parts.system());
            entity.setBizCode(parts.biz());
            entity.setEntityCode(parts.entity());
            entity.setOdsSchema(targetRef.schema());
            entity.setOdsTable(targetRef.table());
            entity.setEnabled(Boolean.TRUE);
            if (!StringUtils.hasText(entity.getDescription())) {
                entity.setDescription(buildDescription(taskName, sourceRef, targetRef));
            }
            mappingRepository.save(entity);
            updated++;
            if (!columnSpecs.isEmpty()) {
                syncColumns(entity, targetRef, columnSpecs, connectionId);
            }
        }
        if (updated == 0) {
            return SyncResult.empty("无可同步的表映射");
        }
        DbtSourceService.DbtSourceRefreshResult refresh = dbtSourceService.refreshOdsSources();
        auditService.auditAction(
            "INGESTION_MAPPING_SYNC",
            AuditStage.SUCCESS,
            taskName,
            Map.of("summary", "同步 ODS 映射", "task", taskName, "tables", updated, "dbt", refresh.message())
        );
        return SyncResult.success(updated, refresh.message());
    }

    @Transactional
    public SyncResult removeFromIngestionPayload(Map<String, Object> payload) {
        Map<String, Object> task = unwrapTask(payload);
        if (task == null || task.isEmpty()) {
            return SyncResult.empty("未发现任务数据");
        }
        List<Map<String, String>> mappings = readTableMappings(task.get("tableMapping"));
        if (mappings.isEmpty()) {
            return SyncResult.empty("未发现表映射");
        }
        UUID connectionId = resolveConnectionId(task);
        if (connectionId == null) {
            return SyncResult.empty("未解析到数据源连接 ID");
        }
        String taskName = normalize(task.get("name"));
        int deleted = 0;
        for (Map<String, String> mapping : mappings) {
            String source = normalize(mapping.get("source"));
            if (!StringUtils.hasText(source)) {
                continue;
            }
            TableRef sourceRef = splitTable(source);
            String namespace = normalizeNamespace(sourceRef.namespace());
            Optional<InfraOdsTableMapping> existing =
                mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
                    connectionId,
                    sourceRef.name(),
                    namespace
                );
            if (existing.isPresent()) {
                mappingRepository.delete(existing.orElseThrow());
                deleted++;
            }
        }
        if (deleted == 0) {
            return SyncResult.empty("未发现可删除的表映射");
        }
        DbtSourceService.DbtSourceRefreshResult refresh = dbtSourceService.refreshOdsSources();
        auditService.auditAction(
            "INGESTION_MAPPING_DELETE",
            AuditStage.SUCCESS,
            taskName,
            Map.of("summary", "删除 ODS 映射", "task", taskName, "tables", deleted, "dbt", refresh.message())
        );
        return SyncResult.success(deleted, refresh.message());
    }

    private Map<String, Object> unwrapTask(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object task = payload.get("task");
        if (task instanceof Map<?, ?> map) {
            return castMap(map);
        }
        if (payload.containsKey("id")) {
            return payload;
        }
        return payload;
    }

    private List<Map<String, String>> readTableMappings(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Map<String, String>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(toStringMap(map));
                }
            }
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            return List.of(toStringMap(map));
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(text, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception ex) {
                LOG.warn("Failed to parse tableMapping json: {}", ex.getMessage());
                return List.of();
            }
        }
        try {
            return objectMapper.convertValue(raw, new TypeReference<List<Map<String, String>>>() {});
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return castMap(map);
        }
        if (raw == null) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private UUID resolveConnectionId(Map<String, Object> task) {
        String sourceId = normalize(task.get("sourceDataSourceId"));
        if (StringUtils.hasText(sourceId)) {
            try {
                return UUID.fromString(sourceId);
            } catch (IllegalArgumentException ex) {
                LOG.warn("Invalid sourceDataSourceId: {}", sourceId);
            }
        }
        String taskId = normalize(task.get("id"));
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        return UUID.nameUUIDFromBytes(("ingestion-task:" + taskId).getBytes(StandardCharsets.UTF_8));
    }

    private TableRef splitTable(String value) {
        if (!StringUtils.hasText(value)) {
            return new TableRef("", "");
        }
        String trimmed = value.trim();
        int idx = trimmed.lastIndexOf('.');
        if (idx < 0) {
            return new TableRef("", trimmed);
        }
        String namespace = trimmed.substring(0, idx);
        String name = trimmed.substring(idx + 1);
        return new TableRef(namespace, name);
    }

    private TableRef splitTarget(String target, Map<String, Object> destinationConfig) {
        if (StringUtils.hasText(target)) {
            TableRef ref = splitTable(target);
            String schema = StringUtils.hasText(ref.namespace()) ? ref.namespace() : resolveSchema(destinationConfig);
            return new TableRef(schema, ref.name());
        }
        String schema = resolveSchema(destinationConfig);
        String table = DEFAULT_SCHEMA + "_unknown";
        return new TableRef(schema, table);
    }

    private String resolveSchema(Map<String, Object> destinationConfig) {
        if (destinationConfig == null || destinationConfig.isEmpty()) {
            return DEFAULT_SCHEMA;
        }
        String schema = normalize(destinationConfig.get("schema"));
        if (!StringUtils.hasText(schema)) {
            schema = normalize(destinationConfig.get("ods_schema"));
        }
        return StringUtils.hasText(schema) ? schema : DEFAULT_SCHEMA;
    }

    private OdsNameParts parseOdsName(String table) {
        if (!StringUtils.hasText(table)) {
            return new OdsNameParts(DEFAULT_CODE, DEFAULT_CODE, DEFAULT_CODE);
        }
        String normalized = table.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ods_")) {
            String[] parts = normalized.substring(4).split("_");
            if (parts.length >= 3) {
                String system = parts[0];
                String biz = parts[1];
                String entity = String.join("_", java.util.Arrays.copyOfRange(parts, 2, parts.length));
                return new OdsNameParts(nonEmpty(system), nonEmpty(biz), nonEmpty(entity));
            }
            if (parts.length == 2) {
                return new OdsNameParts(nonEmpty(parts[0]), nonEmpty(parts[1]), DEFAULT_CODE);
            }
        }
        return new OdsNameParts(DEFAULT_CODE, DEFAULT_CODE, nonEmpty(table));
    }

    private String normalizeNamespace(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return "";
        }
        return namespace.trim();
    }

    private String normalize(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private String nonEmpty(String value) {
        return StringUtils.hasText(value) ? value : DEFAULT_CODE;
    }

    private String buildDescription(String taskName, TableRef source, TableRef target) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(taskName)) {
            sb.append(taskName).append(": ");
        }
        if (StringUtils.hasText(source.namespace())) {
            sb.append(source.namespace()).append('.');
        }
        sb.append(source.name()).append(" -> ");
        if (StringUtils.hasText(target.schema())) {
            sb.append(target.schema()).append('.');
        }
        sb.append(target.table());
        return sb.toString();
    }

    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Map<String, String> toStringMap(Map<?, ?> map) {
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        return result;
    }

    private List<ColumnSpec> resolveColumnSpecs(UUID connectionId) {
        if (connectionId == null) {
            return List.of();
        }
        InfraDataSource dataSource = dataSourceRepository.findById(connectionId).orElse(null);
        if (dataSource == null) {
            return List.of();
        }
        if (StringUtils.hasText(dataSource.getJdbcUrl())) {
            return List.of();
        }
        Map<String, Object> props = readProps(dataSource.getProps());
        if (props.isEmpty()) {
            return List.of();
        }
        Map<String, Object> readerConfig = readMap(props.get("readerConfig"));
        Object columnRaw = readerConfig.get("column");
        if (columnRaw == null) {
            columnRaw = readerConfig.get("columns");
        }
        return extractColumnSpecs(columnRaw);
    }

    private List<ColumnSpec> extractColumnSpecs(Object raw) {
        if (raw == null) {
            return List.of();
        }
        List<ColumnSpec> specs = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                ColumnSpec spec = toColumnSpec(item);
                if (spec != null) {
                    specs.add(spec);
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            ColumnSpec spec = toColumnSpec(map);
            if (spec != null) {
                specs.add(spec);
            }
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            for (String name : text.split(",")) {
                if (!StringUtils.hasText(name)) continue;
                specs.add(new ColumnSpec(name.trim(), "string", null, null, null, null, null, null));
            }
        }
        return specs;
    }

    private ColumnSpec toColumnSpec(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof String text) {
            String name = text.trim();
            return StringUtils.hasText(name) ? new ColumnSpec(name, "string", null, null, null, null, null, null) : null;
        }
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> values = castMap(map);
            String name = normalize(values.get("name"));
            if (!StringUtils.hasText(name)) {
                name = normalize(values.get("column"));
            }
            if (!StringUtils.hasText(name)) {
                name = normalize(values.get("field"));
            }
            if (!StringUtils.hasText(name)) {
                return null;
            }
            String dataType = normalize(values.get("type"));
            if (!StringUtils.hasText(dataType)) {
                dataType = normalize(values.get("dataType"));
            }
            if (!StringUtils.hasText(dataType)) {
                dataType = "string";
            }
            return new ColumnSpec(name, dataType, null, null, null, null, null, null);
        }
        return null;
    }

    private void syncColumns(InfraOdsTableMapping mapping, TableRef targetRef, List<ColumnSpec> specs, UUID connectionId) {
        if (mapping == null || targetRef == null || specs == null || specs.isEmpty()) {
            return;
        }
        CatalogDataset dataset = ensureDataset(connectionId, targetRef);
        if (dataset == null) {
            return;
        }
        CatalogTableSchema table = ensureTable(dataset, targetRef.table());
        if (table == null) {
            return;
        }
        columnSyncService.upsertColumns(table, specs, CatalogColumnSyncService.STATUS_DRAFT);
    }

    private CatalogDataset ensureDataset(UUID connectionId, TableRef targetRef) {
        if (connectionId == null || targetRef == null) {
            return null;
        }
        String schema = StringUtils.hasText(targetRef.schema()) ? targetRef.schema() : DEFAULT_SCHEMA;
        String table = targetRef.table();
        CatalogDataset dataset = datasetRepository
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(connectionId, schema, table)
            .orElseGet(() ->
                datasetRepository
                    .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema, table)
                    .orElse(null)
            );
        boolean created = false;
        if (dataset == null) {
            dataset = new CatalogDataset();
            created = true;
        }
        if (!StringUtils.hasText(dataset.getName())) {
            dataset.setName(table);
        }
        if (!StringUtils.hasText(dataset.getHiveDatabase())) {
            dataset.setHiveDatabase(schema);
        }
        if (!StringUtils.hasText(dataset.getHiveTable())) {
            dataset.setHiveTable(table);
        }
        if (dataset.getSourceId() == null) {
            dataset.setSourceId(connectionId);
        }
        if (!StringUtils.hasText(dataset.getWarehouseLayer())) {
            dataset.setWarehouseLayer("ODS");
        }
        if (!StringUtils.hasText(dataset.getType())) {
            dataset.setType("FILE");
        }
        if (created) {
            return datasetRepository.save(dataset);
        }
        return datasetRepository.save(dataset);
    }

    private CatalogTableSchema ensureTable(CatalogDataset dataset, String tableName) {
        if (dataset == null || !StringUtils.hasText(tableName)) {
            return null;
        }
        return tableRepository
            .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
            .orElseGet(() -> {
                CatalogTableSchema table = new CatalogTableSchema();
                table.setDataset(dataset);
                table.setName(tableName.trim());
                return tableRepository.save(table);
            });
    }

    private Map<String, Object> readProps(String props) {
        if (!StringUtils.hasText(props)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(props, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public record SyncResult(boolean synced, int tables, String message) {
        static SyncResult empty(String message) {
            return new SyncResult(false, 0, message);
        }

        static SyncResult success(int tables, String message) {
            return new SyncResult(true, tables, message);
        }
    }

    private record TableRef(String namespace, String name) {
        String schema() {
            return namespace;
        }

        String table() {
            return name;
        }
    }

    private record OdsNameParts(String system, String biz, String entity) {}
}
