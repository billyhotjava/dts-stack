package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
public class DbtAssetSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtAssetSyncService.class);

    private final ObjectMapper objectMapper;
    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository lineageRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final InfraOdsTableMappingRepository mappingRepository;
    private final AuditService auditService;

    public DbtAssetSyncService(
        ObjectMapper objectMapper,
        DbtProperties properties,
        DbtConfigService configService,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSyncService columnSyncService,
        InfraOdsTableMappingRepository mappingRepository,
        AuditService auditService
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.configService = configService;
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
        this.tableRepository = tableRepository;
        this.columnSyncService = columnSyncService;
        this.mappingRepository = mappingRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DbtAssetSyncResult syncFromManifest() {
        if (!properties.isEnabled()) {
            return DbtAssetSyncResult.disabled("dbt 未启用");
        }
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        String projectDir = view.config() != null && StringUtils.hasText(view.config().projectDir())
            ? view.config().projectDir()
            : properties.getProjectDir();
        if (!StringUtils.hasText(projectDir)) {
            return DbtAssetSyncResult.empty("dbt 项目目录未配置");
        }
        Path manifestPath = Path.of(projectDir, "target", "manifest.json");
        File manifestFile = manifestPath.toFile();
        if (!manifestFile.exists()) {
            return DbtAssetSyncResult.empty("manifest.json 不存在，请先执行 dbt run/docs");
        }

        try {
            Map<String, Object> raw = objectMapper.readValue(manifestFile, new TypeReference<>() {});
            Map<String, Object> nodes = asMap(raw.get("nodes"));
            Map<String, Object> sources = asMap(raw.get("sources"));

            Map<String, UUID> datasetByUniqueId = new HashMap<>();
            Map<String, UUID> datasetByTable = new HashMap<>();
            Map<String, CatalogTableSchema> tableByUniqueId = new HashMap<>();

            SyncStats stats = new SyncStats();
            stats.odsUpdated = syncOdsMappings(datasetByTable, view, stats);

            for (Map.Entry<String, Object> entry : sources.entrySet()) {
                Map<String, Object> source = asMap(entry.getValue());
                if (source.isEmpty()) {
                    continue;
                }
                NodeMeta meta = toNodeMeta(entry.getKey(), source);
                if (!StringUtils.hasText(meta.schema) || !StringUtils.hasText(meta.table)) {
                    continue;
                }
                CatalogDataset dataset = upsertDataset(meta, view, "ODS", stats);
                CatalogTableSchema table = ensureTable(dataset, meta.table);
                datasetByUniqueId.put(entry.getKey(), dataset.getId());
                datasetByTable.put(tableKey(meta.schema, meta.table), dataset.getId());
                tableByUniqueId.put(entry.getKey(), table);
                syncColumnsForNode(table, meta, projectDir);
            }

            Map<String, ModelMeta> modelNodes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : nodes.entrySet()) {
                Map<String, Object> node = asMap(entry.getValue());
                if (node.isEmpty()) {
                    continue;
                }
                String resourceType = text(node.get("resource_type"));
                if (!"model".equalsIgnoreCase(resourceType)) {
                    continue;
                }
                ModelMeta meta = toModelMeta(entry.getKey(), node);
                if (!StringUtils.hasText(meta.table) || !StringUtils.hasText(meta.schema)) {
                    continue;
                }
                CatalogDataset dataset = upsertDataset(toNodeMeta(meta), view, inferLayer(meta.table), stats);
                CatalogTableSchema table = ensureTable(dataset, meta.table);
                datasetByUniqueId.put(entry.getKey(), dataset.getId());
                modelNodes.put(entry.getKey(), meta);
                tableByUniqueId.put(entry.getKey(), table);
            }

            LineageSyncStats lineageStats = syncLineage(modelNodes, datasetByUniqueId);
            stats.lineageCreated = lineageStats.created();
            stats.lineageRemoved = lineageStats.removed();
            stats.columnsUpdated = syncColumnsForModels(modelNodes, tableByUniqueId, projectDir);
            auditService.auditAction(
                "DBT_MODEL_SYNC",
                AuditStage.SUCCESS,
                manifestPath.toString(),
                Map.of(
                    "summary",
                    "同步 dbt 模型资产",
                    "datasetsCreated",
                    stats.created,
                    "datasetsUpdated",
                    stats.updated,
                    "lineageCreated",
                    stats.lineageCreated,
                    "lineageRemoved",
                    stats.lineageRemoved,
                    "columnsUpdated",
                    stats.columnsUpdated
                )
            );
            return DbtAssetSyncResult.success(stats, manifestPath.toString());
        } catch (Exception ex) {
            LOG.warn("Failed to sync dbt assets: {}", ex.getMessage());
            auditService.auditAction(
                "DBT_MODEL_SYNC",
                AuditStage.FAIL,
                "manifest",
                Map.of("summary", "同步 dbt 模型资产失败", "error", ex.getMessage())
            );
            return DbtAssetSyncResult.empty("解析 manifest.json 失败: " + ex.getMessage());
        }
    }

    private int syncOdsMappings(Map<String, UUID> datasetByTable, DbtConfigService.DbtConfigView view, SyncStats stats) {
        List<InfraOdsTableMapping> mappings = mappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        int updated = 0;
        for (InfraOdsTableMapping mapping : mappings) {
            if (mapping == null || !StringUtils.hasText(mapping.getOdsTable())) {
                continue;
            }
            String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema() : "ods";
            String table = mapping.getOdsTable();
            NodeMeta meta = new NodeMeta("ods", schema, table, mapping.getConnectionId(), null, null, null);
            CatalogDataset dataset = upsertDataset(meta, view, "ODS", stats);
            if (dataset != null) {
                datasetByTable.put(tableKey(schema, table), dataset.getId());
                if (mapping.getDatasetId() == null || !mapping.getDatasetId().equals(dataset.getId())) {
                    mapping.setDatasetId(dataset.getId());
                    mappingRepository.save(mapping);
                    updated++;
                }
            }
        }
        return updated;
    }

    private LineageSyncStats syncLineage(Map<String, ModelMeta> modelNodes, Map<String, UUID> datasetByUniqueId) {
        int created = 0;
        int removed = 0;
        for (ModelMeta model : modelNodes.values()) {
            UUID downstream = datasetByUniqueId.get(model.uniqueId);
            if (downstream == null) {
                continue;
            }
            java.util.Set<UUID> desired = new java.util.LinkedHashSet<>();
            for (String upstreamUniqueId : model.dependsOn) {
                UUID upstream = datasetByUniqueId.get(upstreamUniqueId);
                if (upstream != null) {
                    desired.add(upstream);
                }
            }
            List<CatalogDatasetLineage> existing =
                lineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(downstream, "DBT");
            for (CatalogDatasetLineage link : existing) {
                UUID upstreamId = link.getUpstreamDatasetId();
                if (upstreamId == null) {
                    continue;
                }
                if (!desired.contains(upstreamId)) {
                    lineageRepository.delete(link);
                    removed++;
                }
            }
            for (UUID upstream : desired) {
                Optional<CatalogDatasetLineage> present =
                    lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetId(upstream, downstream);
                if (present.isPresent()) {
                    continue;
                }
                CatalogDatasetLineage link = new CatalogDatasetLineage();
                link.setUpstreamDatasetId(upstream);
                link.setDownstreamDatasetId(downstream);
                link.setRelationType("DBT");
                link.setUpstreamAssetType("DATASET");
                link.setDownstreamAssetType("MODEL");
                link.setDirection("UPSTREAM_TO_DOWNSTREAM");
                link.setProjectName(resolveDbtProjectName(model.uniqueId));
                lineageRepository.save(link);
                created++;
            }
        }
        return new LineageSyncStats(created, removed);
    }

    private CatalogDataset upsertDataset(NodeMeta meta, DbtConfigService.DbtConfigView view, String layer, SyncStats stats) {
        if (!StringUtils.hasText(meta.schema) || !StringUtils.hasText(meta.table)) {
            return null;
        }
        CatalogDataset dataset = findDataset(meta);
        boolean created = false;
        if (dataset == null) {
            dataset = new CatalogDataset();
            created = true;
        }
        dataset.setName(defaultIfBlank(dataset.getName(), meta.table));
        dataset.setHiveDatabase(defaultIfBlank(dataset.getHiveDatabase(), meta.schema));
        dataset.setHiveTable(defaultIfBlank(dataset.getHiveTable(), meta.table));
        if (!StringUtils.hasText(dataset.getWarehouseLayer()) && StringUtils.hasText(layer)) {
            dataset.setWarehouseLayer(layer);
        }
        if (dataset.getSourceId() == null && meta.sourceId != null) {
            dataset.setSourceId(meta.sourceId);
        }
        if (!StringUtils.hasText(dataset.getType())) {
            dataset.setType(resolveDatasetType(view));
        }
        if (!StringUtils.hasText(dataset.getDescription()) && StringUtils.hasText(meta.description)) {
            dataset.setDescription(meta.description);
        }
        CatalogDataset saved = datasetRepository.save(dataset);
        if (created) {
            stats.created++;
        } else {
            stats.updated++;
        }
        return saved;
    }

    private CatalogTableSchema ensureTable(CatalogDataset dataset, String tableName) {
        if (dataset == null || !StringUtils.hasText(tableName)) {
            return null;
        }
        Optional<CatalogTableSchema> existing = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, tableName);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        CatalogTableSchema table = new CatalogTableSchema();
        table.setDataset(dataset);
        table.setName(tableName.trim());
        return tableRepository.save(table);
    }

    private int syncColumnsForModels(Map<String, ModelMeta> modelNodes, Map<String, CatalogTableSchema> tableByUniqueId, String projectDir) {
        int updated = 0;
        if (modelNodes == null || modelNodes.isEmpty()) {
            return updated;
        }
        for (ModelMeta model : modelNodes.values()) {
            CatalogTableSchema table = tableByUniqueId.get(model.uniqueId);
            if (table == null) continue;
            updated += syncColumnsForNode(table, model, projectDir);
        }
        return updated;
    }

    private int syncColumnsForNode(CatalogTableSchema table, NodeMeta meta, String projectDir) {
        if (table == null || meta == null) {
            return 0;
        }
        int updated = 0;
        List<ColumnSpec> csvSpecs = resolveCsvSpecs(meta.originalFilePath, projectDir);
        List<ColumnSpec> manifestSpecs = columnSyncService.parseManifestColumns(meta.columns);
        if (csvSpecs != null && !csvSpecs.isEmpty()) {
            updated += columnSyncService.upsertColumns(table, csvSpecs, CatalogColumnSyncService.STATUS_DRAFT);
        }
        if (manifestSpecs != null && !manifestSpecs.isEmpty()) {
            updated += columnSyncService.upsertColumns(table, manifestSpecs, CatalogColumnSyncService.STATUS_ACTIVE);
        }
        return updated;
    }

    private int syncColumnsForNode(CatalogTableSchema table, ModelMeta meta, String projectDir) {
        if (table == null || meta == null) {
            return 0;
        }
        int updated = 0;
        List<ColumnSpec> csvSpecs = resolveCsvSpecs(meta.originalFilePath, projectDir);
        List<ColumnSpec> manifestSpecs = columnSyncService.parseManifestColumns(meta.columns);
        if (csvSpecs != null && !csvSpecs.isEmpty()) {
            updated += columnSyncService.upsertColumns(table, csvSpecs, CatalogColumnSyncService.STATUS_DRAFT);
        }
        if (manifestSpecs != null && !manifestSpecs.isEmpty()) {
            updated += columnSyncService.upsertColumns(table, manifestSpecs, CatalogColumnSyncService.STATUS_ACTIVE);
        }
        return updated;
    }

    private List<ColumnSpec> resolveCsvSpecs(String originalFilePath, String projectDir) {
        if (!StringUtils.hasText(originalFilePath) || !StringUtils.hasText(projectDir)) {
            return List.of();
        }
        Path sqlPath = Path.of(projectDir, originalFilePath).normalize();
        String fileName = sqlPath.getFileName() != null ? sqlPath.getFileName().toString() : null;
        if (!StringUtils.hasText(fileName)) {
            return List.of();
        }
        String csvName = fileName.endsWith(".sql") ? fileName.substring(0, fileName.length() - 4) + ".csv" : fileName + ".csv";
        Path csvPath = sqlPath.resolveSibling(csvName);
        return columnSyncService.parseCsv(csvPath);
    }

    private CatalogDataset findDataset(NodeMeta meta) {
        if (meta.sourceId != null) {
            Optional<CatalogDataset> existing =
                datasetRepository.findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                    meta.sourceId,
                    meta.schema,
                    meta.table
                );
            if (existing.isPresent()) {
                return existing.orElseThrow();
            }
        }
        return datasetRepository
            .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(meta.schema, meta.table)
            .orElse(null);
    }

    private String resolveDbtProjectName(String uniqueId) {
        if (!StringUtils.hasText(uniqueId)) {
            return null;
        }
        String[] parts = uniqueId.split("\\.");
        if (parts.length < 3) {
            return null;
        }
        String project = text(parts[1]);
        return StringUtils.hasText(project) ? project : null;
    }

    private String resolveDatasetType(DbtConfigService.DbtConfigView view) {
        if (view != null && view.target() != null && StringUtils.hasText(view.target().type())) {
            return view.target().type().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private ModelMeta toModelMeta(String uniqueId, Map<String, Object> node) {
        String name = text(node.get("name"));
        String alias = text(node.get("alias"));
        String database = text(node.get("database"));
        String schema = text(node.get("schema"));
        String table = StringUtils.hasText(alias) ? alias : name;
        List<String> dependsOn = extractDepends(node.get("depends_on"));
        String description = text(node.get("description"));
        Map<String, Object> columns = asMap(node.get("columns"));
        String filePath = text(node.get("original_file_path"));
        return new ModelMeta(uniqueId, database, schema, table, dependsOn, description, columns, filePath);
    }

    private NodeMeta toNodeMeta(String uniqueId, Map<String, Object> node) {
        String database = text(node.get("database"));
        String schema = text(node.get("schema"));
        String name = text(node.get("name"));
        String identifier = text(node.get("identifier"));
        String table = StringUtils.hasText(identifier) ? identifier : name;
        String description = text(node.get("description"));
        Map<String, Object> columns = asMap(node.get("columns"));
        String filePath = text(node.get("original_file_path"));
        return new NodeMeta(uniqueId, schema != null ? schema : database, table, null, description, columns, filePath);
    }

    private NodeMeta toNodeMeta(ModelMeta meta) {
        if (meta == null) {
            return null;
        }
        String schema = StringUtils.hasText(meta.schema) ? meta.schema : meta.database;
        return new NodeMeta(meta.uniqueId, schema, meta.table, null, meta.description, meta.columns, meta.originalFilePath);
    }

    private List<String> extractDepends(Object value) {
        Map<String, Object> map = asMap(value);
        Object nodes = map.get("nodes");
        if (!(nodes instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            String text = item == null ? null : item.toString();
            if (StringUtils.hasText(text)) {
                out.add(text);
            }
        }
        return out;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultIfBlank(String current, String fallback) {
        if (StringUtils.hasText(current)) {
            return current;
        }
        return fallback;
    }

    private String inferLayer(String table) {
        if (!StringUtils.hasText(table)) {
            return null;
        }
        String normalized = table.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ods_")) return "ODS";
        if (normalized.startsWith("dwd_")) return "DWD";
        if (normalized.startsWith("dws_")) return "DWS";
        if (normalized.startsWith("ads_")) return "ADS";
        return null;
    }

    private String tableKey(String schema, String table) {
        return (schema + "." + table).toLowerCase(Locale.ROOT);
    }

    public static final class SyncStats {
        int created = 0;
        int updated = 0;
        int lineageCreated = 0;
        int lineageRemoved = 0;
        int odsUpdated = 0;
        int columnsUpdated = 0;

        public int getCreated() {
            return created;
        }

        public int getUpdated() {
            return updated;
        }

        public int getLineageCreated() {
            return lineageCreated;
        }

        public int getLineageRemoved() {
            return lineageRemoved;
        }

        public int getOdsUpdated() {
            return odsUpdated;
        }

        public int getColumnsUpdated() {
            return columnsUpdated;
        }
    }

    public record DbtAssetSyncResult(boolean enabled, boolean synced, String message, String manifestPath, SyncStats stats) {
        static DbtAssetSyncResult disabled(String message) {
            return new DbtAssetSyncResult(false, false, message, null, new SyncStats());
        }

        static DbtAssetSyncResult empty(String message) {
            return new DbtAssetSyncResult(true, false, message, null, new SyncStats());
        }

        static DbtAssetSyncResult success(SyncStats stats, String path) {
            return new DbtAssetSyncResult(true, true, "dbt 模型资产已同步", path, stats);
        }
    }

    private record NodeMeta(
        String uniqueId,
        String schema,
        String table,
        UUID sourceId,
        String description,
        Map<String, Object> columns,
        String originalFilePath
    ) {}

    private record ModelMeta(
        String uniqueId,
        String database,
        String schema,
        String table,
        List<String> dependsOn,
        String description,
        Map<String, Object> columns,
        String originalFilePath
    ) {}

    private record LineageSyncStats(int created, int removed) {}
}
