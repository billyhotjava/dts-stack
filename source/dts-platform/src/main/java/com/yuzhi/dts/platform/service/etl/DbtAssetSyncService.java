package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetGovernancePolicy;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationPropagationJobService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import com.yuzhi.dts.platform.service.catalog.CatalogPhysicalLocator;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecCompatibilityReader;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtAssetSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtAssetSyncService.class);

    private final ObjectMapper objectMapper;
    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository lineageRepository;
    private final CatalogColumnLineageRepository columnLineageRepository;
    private final CatalogLineageJobRepository lineageJobRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final InfraOdsTableMappingRepository mappingRepository;
    private final ModelLifecycleRepository lifecycleRepository;
    private final ModelSpecCompatibilityReader modelSpecReader;
    private final AuditService auditService;
    private final CatalogClassificationPropagationJobService propagationJobService;

    public DbtAssetSyncService(
        ObjectMapper objectMapper,
        DbtProperties properties,
        DbtConfigService configService,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository,
        CatalogColumnLineageRepository columnLineageRepository,
        CatalogLineageJobRepository lineageJobRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogColumnSyncService columnSyncService,
        InfraOdsTableMappingRepository mappingRepository,
        ModelLifecycleRepository lifecycleRepository,
        ModelSpecCompatibilityReader modelSpecReader,
        AuditService auditService,
        CatalogClassificationPropagationJobService propagationJobService
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.configService = configService;
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
        this.columnLineageRepository = columnLineageRepository;
        this.lineageJobRepository = lineageJobRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.columnSyncService = columnSyncService;
        this.mappingRepository = mappingRepository;
        this.lifecycleRepository = lifecycleRepository;
        this.modelSpecReader = modelSpecReader;
        this.auditService = auditService;
        this.propagationJobService = propagationJobService;
    }

    /**
     * Not wrapped in a single @Transactional on purpose. Each sub-step is its own short
     * transaction (repository.save opens one implicitly; upsertColumns is @Transactional).
     * Rationale: a manifest-wide transaction holds row locks on catalog_column_schema for
     * the entire sync, which previously deadlocked with concurrent user-triggered writers
     * (modeling / ods mapping / REST manual sync). Idempotent upserts mean partial failure
     * converges on the next tick.
     */
    public DbtAssetSyncResult syncFromManifest() {
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        String projectDir = view.config() != null && StringUtils.hasText(view.config().projectDir())
            ? view.config().projectDir()
            : properties.getProjectDir();
        return syncFromManifest(projectDir, view);
    }

    public DbtAssetSyncResult syncFromManifest(String projectDir) {
        return syncFromManifest(projectDir, configService.loadConfig());
    }

    private DbtAssetSyncResult syncFromManifest(String projectDir, DbtConfigService.DbtConfigView view) {
        if (!properties.isEnabled()) {
            return DbtAssetSyncResult.disabled("dbt 未启用");
        }
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
            ManifestEvidence manifestEvidence = manifestEvidence(raw);
            RunResults runResults = readRunResults(projectDir);

            Map<String, UUID> datasetByUniqueId = new HashMap<>();
            Map<String, UUID> datasetByTable = new HashMap<>();
            Map<String, CatalogTableSchema> tableByUniqueId = new HashMap<>();
            Set<String> retainedOrAcceptedPhysicalAssetKeys = new LinkedHashSet<>();
            String workspaceKey = dbtWorkspaceKey(projectDir);
            UUID targetSourceId = resolveTargetSourceId(view);

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
                RunEvidence pairedRunEvidence = pairRunEvidence(
                    entry.getKey(),
                    manifestEvidence,
                    runResults,
                    runResults.byUniqueId().get(entry.getKey())
                );
                ModelMeta meta = toModelMeta(entry.getKey(), node, pairedRunEvidence);
                modelNodes.put(entry.getKey(), meta);
                if (!isPhysicalMaterialization(meta)) {
                    continue;
                }
                if (!isMaterializedRelation(meta)) {
                    markMaterializedStale(meta, targetSourceId, stats);
                    if (meta.runEvidence != null && !StringUtils.hasText(meta.runEvidence.staleReason())) {
                        retainedOrAcceptedPhysicalAssetKeys.add(physicalMaterializationKey(meta));
                    }
                    continue;
                }
                if (!hasValidImplementationPin(meta)) {
                    markMaterializedStale(
                        meta,
                        targetSourceId,
                        "IMPLEMENTATION_PIN_INVALID",
                        stats
                    );
                    continue;
                }
                importCurrentDbtArtifacts(meta);
                retainedOrAcceptedPhysicalAssetKeys.add(physicalMaterializationKey(meta));
                if (isLifecycleBoundModel(meta)) {
                    continue;
                }
                String controlledLayer = resolveControlledLayer(meta);
                CatalogDataset dataset = upsertDataset(
                    toNodeMeta(meta, targetSourceId),
                    view,
                    controlledLayer,
                    buildMaterializedTags(meta, controlledLayer, manifestEvidence.projectName(), workspaceKey),
                    stats
                );
                CatalogTableSchema table = ensureTable(dataset, meta.table);
                datasetByUniqueId.put(entry.getKey(), dataset.getId());
                tableByUniqueId.put(entry.getKey(), table);
            }
            reconcileDbtMaterializations(
                manifestEvidence.projectName(),
                workspaceKey,
                retainedOrAcceptedPhysicalAssetKeys,
                stats
            );

            LineageSyncStats lineageStats = syncLineage(modelNodes, datasetByUniqueId);
            stats.lineageCreated = lineageStats.created();
            stats.lineageRemoved += lineageStats.removed();
            stats.classificationPropagationEnqueued = lineageStats.propagationEnqueued();
            stats.columnsUpdated = syncColumnsForModels(modelNodes, tableByUniqueId, projectDir);
            ColumnLineageSyncStats columnLineageStats = syncColumnLineage(modelNodes, datasetByUniqueId, tableByUniqueId);
            stats.columnLineageCreated = columnLineageStats.created();
            stats.columnLineageUpdated = columnLineageStats.updated();
            stats.columnLineageRemoved += columnLineageStats.removed();
            auditService.auditAction(
                "DBT_MODEL_SYNC",
                AuditStage.SUCCESS,
                manifestPath.toString(),
                Map.ofEntries(
                    Map.entry("summary", "同步 dbt 模型资产"),
                    Map.entry("datasetsCreated", stats.created),
                    Map.entry("datasetsUpdated", stats.updated),
                    Map.entry("lineageCreated", stats.lineageCreated),
                    Map.entry("lineageRemoved", stats.lineageRemoved),
                    Map.entry("classificationPropagationEnqueued", stats.classificationPropagationEnqueued),
                    Map.entry("columnsUpdated", stats.columnsUpdated),
                    Map.entry("manifestEvidenceUpdated", stats.manifestEvidenceUpdated),
                    Map.entry("columnLineageCreated", stats.columnLineageCreated),
                    Map.entry("columnLineageUpdated", stats.columnLineageUpdated),
                    Map.entry("columnLineageRemoved", stats.columnLineageRemoved)
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
        int propagationEnqueued = 0;
        for (ModelMeta model : modelNodes.values()) {
            CatalogLineageJob lineageJob = upsertDbtJob(model);
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
            List<CatalogDatasetLineage> existing = lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(downstream, "DBT");
            for (CatalogDatasetLineage link : existing) {
                UUID upstreamId = link.getUpstreamDatasetId();
                if (upstreamId == null) {
                    continue;
                }
                if (!desired.contains(upstreamId)) {
                    link.setValidTo(Instant.now());
                    lineageRepository.save(link);
                    removed++;
                }
            }
            for (UUID upstream : desired) {
                Optional<CatalogDatasetLineage> present =
                    lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(upstream, downstream, "DBT");
                if (present.isPresent()) {
                    CatalogDatasetLineage link = present.orElseThrow();
                    link.setLastObservedAt(Instant.now());
                    link.setNotes(buildDbtLineageEvidence(model, upstream, downstream));
                    if (lineageJob != null && !lineageJob.getId().equals(link.getLineageJobId())) {
                        link.setLineageJobId(lineageJob.getId());
                        link.setProjectName(resolveDbtProjectName(model.uniqueId));
                    }
                    lineageRepository.save(link);
                    continue;
                }
                CatalogDatasetLineage link = new CatalogDatasetLineage();
                link.setUpstreamDatasetId(upstream);
                link.setDownstreamDatasetId(downstream);
                link.setRelationType("DBT");
                link.setUpstreamAssetType("DATASET");
                link.setDownstreamAssetType("MODEL");
                link.setDirection("FORWARD");
                link.setProjectName(resolveDbtProjectName(model.uniqueId));
                link.setVerificationStatus("DECLARED");
                link.setValidFrom(Instant.now());
                link.setLastObservedAt(Instant.now());
                link.setNotes(buildDbtLineageEvidence(model, upstream, downstream));
                if (lineageJob != null) {
                    link.setLineageJobId(lineageJob.getId());
                }
                lineageRepository.save(link);
                created++;
            }
            if (
                !desired.isEmpty() &&
                propagationJobService.enqueue(
                    downstream,
                    "DBT",
                    dbtPropagationTriggerRef(model)
                )
            ) {
                propagationEnqueued++;
            }
        }
        return new LineageSyncStats(created, removed, propagationEnqueued);
    }

    private String dbtPropagationTriggerRef(ModelMeta model) {
        String invocationId =
            model != null && model.runEvidence != null && StringUtils.hasText(model.runEvidence.invocationId())
                ? model.runEvidence.invocationId()
                : "manifest";
        return "dbt:" + model.uniqueId + ":" + invocationId;
    }

    private ColumnLineageSyncStats syncColumnLineage(
        Map<String, ModelMeta> modelNodes,
        Map<String, UUID> datasetByUniqueId,
        Map<String, CatalogTableSchema> tableByUniqueId
    ) {
        if (modelNodes == null || modelNodes.isEmpty()) {
            return new ColumnLineageSyncStats(0, 0, 0);
        }
        int created = 0;
        int updated = 0;
        int removed = 0;
        Instant observedAt = Instant.now();
        for (ModelMeta model : modelNodes.values()) {
            UUID downstream = datasetByUniqueId.get(model.uniqueId);
            CatalogTableSchema downstreamTable = tableByUniqueId.get(model.uniqueId);
            if (downstream == null || downstreamTable == null) {
                continue;
            }
            Map<String, CatalogColumnSchema> downstreamColumns = columnsByName(columnRepository.findByTable(downstreamTable));
            if (downstreamColumns.isEmpty()) {
                continue;
            }
            Map<String, String> expressionsByAlias = selectExpressionsByAlias(model.compiledCode);
            Map<String, CatalogColumnLineage> existing = existingColumnLineageByKey(downstream);
            Set<String> desiredKeys = new LinkedHashSet<>();
            for (String upstreamUniqueId : model.dependsOn) {
                UUID upstream = datasetByUniqueId.get(upstreamUniqueId);
                CatalogTableSchema upstreamTable = tableByUniqueId.get(upstreamUniqueId);
                if (upstream == null || upstreamTable == null) {
                    continue;
                }
                CatalogDatasetLineage tableLineage = lineageRepository
                    .findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(upstream, downstream, "DBT")
                    .orElse(null);
                if (tableLineage == null || tableLineage.getId() == null) {
                    continue;
                }
                Map<String, CatalogColumnSchema> upstreamColumns = columnsByName(columnRepository.findByTable(upstreamTable));
                if (upstreamColumns.isEmpty()) {
                    continue;
                }
                for (Map.Entry<String, CatalogColumnSchema> downstreamEntry : downstreamColumns.entrySet()) {
                    CatalogColumnSchema downstreamColumn = downstreamEntry.getValue();
                    String expression = expressionsByAlias.get(downstreamEntry.getKey());
                    Set<String> matchedUpstreamColumns = new LinkedHashSet<>();
                    if (StringUtils.hasText(expression)) {
                        for (CatalogColumnSchema upstreamColumn : upstreamColumns.values()) {
                            if (!expressionReferencesColumn(expression, upstreamColumn.getName())) {
                                continue;
                            }
                            matchedUpstreamColumns.add(normalizeColumnName(upstreamColumn.getName()));
                            boolean isNew = writeColumnLineage(
                                existing,
                                desiredKeys,
                                tableLineage,
                                upstream,
                                downstream,
                                upstreamColumn,
                                downstreamColumn,
                                resolveDbtProjectName(model.uniqueId),
                                "SQL_EXPRESSION",
                                expression,
                                "PARSED",
                                observedAt
                            );
                            if (isNew) {
                                created++;
                            } else {
                                updated++;
                            }
                        }
                    }
                    CatalogColumnSchema sameNameUpstreamColumn = upstreamColumns.get(downstreamEntry.getKey());
                    if (sameNameUpstreamColumn == null || matchedUpstreamColumns.contains(normalizeColumnName(sameNameUpstreamColumn.getName()))) {
                        continue;
                    }
                    boolean isNew = writeColumnLineage(
                        existing,
                        desiredKeys,
                        tableLineage,
                        upstream,
                        downstream,
                        sameNameUpstreamColumn,
                        downstreamColumn,
                        resolveDbtProjectName(model.uniqueId),
                        "SAME_NAME",
                        "same-name projection: " + sameNameUpstreamColumn.getName(),
                        "INFERRED",
                        observedAt
                    );
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }
            for (Map.Entry<String, CatalogColumnLineage> entry : existing.entrySet()) {
                if (!desiredKeys.contains(entry.getKey())) {
                    CatalogColumnLineage stale = entry.getValue();
                    if (stale.getValidTo() == null) {
                        stale.setValidTo(observedAt);
                        columnLineageRepository.save(stale);
                        removed++;
                    }
                }
            }
        }
        return new ColumnLineageSyncStats(created, updated, removed);
    }

    private boolean writeColumnLineage(
        Map<String, CatalogColumnLineage> existing,
        Set<String> desiredKeys,
        CatalogDatasetLineage tableLineage,
        UUID upstream,
        UUID downstream,
        CatalogColumnSchema upstreamColumn,
        CatalogColumnSchema downstreamColumn,
        String projectName,
        String lineageType,
        String expression,
        String confidence,
        Instant observedAt
    ) {
        String key = columnLineageKey(upstream, downstream, upstreamColumn.getName(), downstreamColumn.getName());
        desiredKeys.add(key);
        CatalogColumnLineage columnLineage = existing.get(key);
        boolean isNew = columnLineage == null;
        if (isNew) {
            columnLineage = new CatalogColumnLineage();
        }
        columnLineage.setDatasetLineageId(tableLineage.getId());
        columnLineage.setUpstreamDatasetId(upstream);
        columnLineage.setDownstreamDatasetId(downstream);
        columnLineage.setUpstreamColumnId(upstreamColumn.getId());
        columnLineage.setDownstreamColumnId(downstreamColumn.getId());
        columnLineage.setUpstreamColumn(upstreamColumn.getName());
        columnLineage.setDownstreamColumn(downstreamColumn.getName());
        columnLineage.setRelationType("DBT");
        columnLineage.setLineageType(lineageType);
        columnLineage.setExpression(expression);
        columnLineage.setConfidence(confidence);
        columnLineage.setProjectName(projectName);
        columnLineage.setLineageJobId(tableLineage.getLineageJobId());
        columnLineage.setLastObservedAt(observedAt);
        if (columnLineage.getValidFrom() == null) {
            columnLineage.setValidFrom(observedAt);
        }
        columnLineage.setValidTo(null);
        columnLineageRepository.save(columnLineage);
        return isNew;
    }

    private Map<String, CatalogColumnLineage> existingColumnLineageByKey(UUID downstream) {
        Map<String, CatalogColumnLineage> result = new LinkedHashMap<>();
        if (downstream == null) {
            return result;
        }
        for (CatalogColumnLineage lineage : columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(downstream, "DBT")) {
            if (lineage == null) {
                continue;
            }
            String key = columnLineageKey(
                lineage.getUpstreamDatasetId(),
                lineage.getDownstreamDatasetId(),
                lineage.getUpstreamColumn(),
                lineage.getDownstreamColumn()
            );
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, lineage);
            }
        }
        return result;
    }

    private Map<String, CatalogColumnSchema> columnsByName(List<CatalogColumnSchema> columns) {
        Map<String, CatalogColumnSchema> result = new LinkedHashMap<>();
        if (columns == null || columns.isEmpty()) {
            return result;
        }
        for (CatalogColumnSchema column : columns) {
            String key = normalizeColumnName(column != null ? column.getName() : null);
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, column);
            }
        }
        return result;
    }

    private String columnLineageKey(UUID upstreamDatasetId, UUID downstreamDatasetId, String upstreamColumn, String downstreamColumn) {
        String upstream = normalizeColumnName(upstreamColumn);
        String downstream = normalizeColumnName(downstreamColumn);
        if (upstreamDatasetId == null || downstreamDatasetId == null || !StringUtils.hasText(upstream) || !StringUtils.hasText(downstream)) {
            return null;
        }
        return upstreamDatasetId + ">" + downstreamDatasetId + ":" + upstream + ">" + downstream;
    }

    private String normalizeColumnName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> selectExpressionsByAlias(String sql) {
        if (!StringUtils.hasText(sql)) {
            return Map.of();
        }
        String selectClause = extractTopLevelSelectClause(sql);
        if (!StringUtils.hasText(selectClause)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String expression : splitTopLevel(selectClause, ',')) {
            String trimmed = trimToNull(expression);
            if (trimmed == null) {
                continue;
            }
            String alias = resolveSelectAlias(trimmed);
            String key = normalizeColumnName(alias);
            if (StringUtils.hasText(key)) {
                result.putIfAbsent(key, trimmed);
            }
        }
        return result;
    }

    private String extractTopLevelSelectClause(String sql) {
        String normalized = stripSqlComments(sql);
        int selectStart = findTopLevelKeyword(normalized, "select", 0);
        if (selectStart < 0) {
            return null;
        }
        int fromStart = findTopLevelKeyword(normalized, "from", selectStart + "select".length());
        if (fromStart < 0 || fromStart <= selectStart) {
            return null;
        }
        return normalized.substring(selectStart + "select".length(), fromStart);
    }

    private int findTopLevelKeyword(String sql, String keyword, int startIndex) {
        if (!StringUtils.hasText(sql) || !StringUtils.hasText(keyword)) {
            return -1;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int depth = 0;
        char quote = 0;
        for (int index = Math.max(0, startIndex); index <= sql.length() - keyword.length(); index++) {
            char ch = sql.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth != 0) {
                continue;
            }
            if (sql.regionMatches(true, index, lowerKeyword, 0, lowerKeyword.length()) && isKeywordBoundary(sql, index, lowerKeyword.length())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isKeywordBoundary(String text, int start, int length) {
        char before = start > 0 ? text.charAt(start - 1) : ' ';
        char after = start + length < text.length() ? text.charAt(start + length) : ' ';
        return !isIdentifierChar(before) && !isIdentifierChar(after);
    }

    private List<String> splitTopLevel(String text, char delimiter) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
                continue;
            }
            if (ch == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (ch == delimiter && depth == 0) {
                parts.add(text.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private String resolveSelectAlias(String expression) {
        String trimmed = trimToNull(expression);
        if (trimmed == null) {
            return null;
        }
        java.util.regex.Matcher asMatcher = java.util.regex.Pattern
            .compile("(?is)\\s+as\\s+([\"`\\[]?[A-Za-z_][A-Za-z0-9_]*[\"`\\]]?)\\s*$")
            .matcher(trimmed);
        if (asMatcher.find()) {
            return unquoteIdentifier(asMatcher.group(1));
        }
        List<String> tokens = splitTopLevel(trimmed, ' ');
        for (int index = tokens.size() - 1; index >= 0; index--) {
            String token = trimToNull(tokens.get(index));
            if (token == null) {
                continue;
            }
            String alias = unquoteIdentifier(token);
            if (alias != null && alias.matches("[A-Za-z_][A-Za-z0-9_]*") && !isSqlKeyword(alias)) {
                return alias;
            }
            break;
        }
        String simpleColumn = trimmed.replace("\"", "").replace("`", "");
        int dot = simpleColumn.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < simpleColumn.length()) {
            simpleColumn = simpleColumn.substring(dot + 1);
        }
        simpleColumn = trimToNull(simpleColumn);
        return simpleColumn != null && simpleColumn.matches("[A-Za-z_][A-Za-z0-9_]*") ? simpleColumn : null;
    }

    private boolean expressionReferencesColumn(String expression, String columnName) {
        String column = trimToNull(columnName);
        if (!StringUtils.hasText(expression) || column == null) {
            return false;
        }
        String pattern = "(?i)(^|[^A-Za-z0-9_])([\"`\\[]?)" + java.util.regex.Pattern.quote(column) + "([\"`\\]]?)([^A-Za-z0-9_]|$)";
        return java.util.regex.Pattern.compile(pattern).matcher(expression).find();
    }

    private String stripSqlComments(String sql) {
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        return sql.replaceAll("(?m)--.*?$", " ").replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String unquoteIdentifier(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("`") && text.endsWith("`"))) {
            return text.substring(1, text.length() - 1);
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private boolean isSqlKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CASE", "WHEN", "THEN", "ELSE", "END", "NULL", "TRUE", "FALSE", "FROM", "WHERE", "GROUP", "ORDER" -> true;
            default -> false;
        };
    }

    private CatalogLineageJob upsertDbtJob(ModelMeta model) {
        if (model == null || !StringUtils.hasText(model.uniqueId)) {
            return null;
        }
        String jobKey = truncate("DBT:" + model.uniqueId.trim(), 256);
        CatalogLineageJob job = lineageJobRepository.findByJobKey(jobKey).orElseGet(CatalogLineageJob::new);
        job.setJobKey(jobKey);
        job.setName(truncate(defaultIfBlank(model.table, model.uniqueId), 256));
        job.setJobType("DBT_MODEL");
        job.setEngine("DBT");
        job.setRelationType("DBT");
        job.setProjectName(resolveDbtProjectName(model.uniqueId));
        job.setExternalId(truncate(model.uniqueId, 256));
        if (!StringUtils.hasText(job.getStatus())) {
            job.setStatus("declared");
        }
        job.setLastObservedAt(Instant.now());
        job.setDetailPayload(buildDbtJobDetail(model));
        return lineageJobRepository.save(job);
    }

    private CatalogDataset upsertDataset(NodeMeta meta, DbtConfigService.DbtConfigView view, String layer, SyncStats stats) {
        return upsertDataset(meta, view, layer, null, stats);
    }

    private CatalogDataset upsertDataset(
        NodeMeta meta,
        DbtConfigService.DbtConfigView view,
        String layer,
        String tags,
        SyncStats stats
    ) {
        if (!StringUtils.hasText(meta.schema) || !StringUtils.hasText(meta.table)) {
            return null;
        }
        CatalogDataset dataset = findDataset(meta);
        boolean created = false;
        if (dataset == null) {
            dataset = new CatalogDataset();
            if (meta.sourceId != null) {
                dataset.setId(
                    new CatalogPhysicalLocator(
                        meta.sourceId,
                        meta.schema,
                        meta.table
                    ).assetId()
                );
            }
            created = true;
        }
        dataset.setName(defaultIfBlank(dataset.getName(), meta.table));
        dataset.setHiveDatabase(defaultIfBlank(dataset.getHiveDatabase(), meta.schema));
        dataset.setHiveTable(defaultIfBlank(dataset.getHiveTable(), meta.table));
        if (StringUtils.hasText(layer)) {
            dataset.setWarehouseLayer(layer);
        }
        if (!StringUtils.hasText(dataset.getLifecycleStatus())) {
            dataset.setLifecycleStatus(CatalogAssetGovernancePolicy.lifecycleForDiscoveredAsset());
        }
        if (dataset.getSourceId() == null && meta.sourceId != null) {
            dataset.setSourceId(meta.sourceId);
        }
        if (!StringUtils.hasText(dataset.getType())) {
            dataset.setType(resolveDatasetType(view));
        }
        if (!StringUtils.hasText(dataset.getDescription())) {
            dataset.setDescription(defaultIfBlank(meta.description, buildDbtManifestEvidence(meta, layer)));
        }
        if (StringUtils.hasText(tags)) {
            dataset.setTags(tags);
            dataset.setEnabled(Boolean.TRUE);
            if ("STALE".equalsIgnoreCase(dataset.getLifecycleStatus())) {
                dataset.setLifecycleStatus(CatalogAssetGovernancePolicy.lifecycleForDiscoveredAsset());
            }
        }
        dataset.setSnapshotTime(Instant.now());
        CatalogDataset saved = datasetRepository.save(dataset);
        stats.manifestEvidenceUpdated++;
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
            List<CatalogDataset> matches = datasetRepository
                .findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                    meta.schema,
                    meta.table
                )
                .stream()
                .filter(dataset ->
                    dataset.getSourceId() == null ||
                    meta.sourceId.equals(dataset.getSourceId())
                )
                .toList();
            if (matches.size() > 1) {
                throw new IllegalStateException(
                    "CATALOG_DATASET_PHYSICAL_LOCATOR_AMBIGUOUS: " +
                    meta.sourceId +
                    ":" +
                    meta.schema +
                    "." +
                    meta.table
                );
            }
            return matches.isEmpty() ? null : matches.getFirst();
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

    private String buildDbtJobDetail(ModelMeta model) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("engine", "DBT");
        data.put("uniqueId", model.uniqueId);
        data.put("database", model.database);
        data.put("schema", model.schema);
        data.put("table", model.table);
        data.put("materialization", model.materialization);
        data.put("dependsOn", model.dependsOn);
        data.put("runStatus", model.runEvidence != null ? model.runEvidence.status : null);
        data.put("runInvocationId", model.runEvidence != null ? model.runEvidence.invocationId : null);
        data.put("originalFilePath", model.originalFilePath);
        data.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        return data.toString();
    }

    private String buildDbtManifestEvidence(NodeMeta meta, String layer) {
        if (meta == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "dbt-manifest");
        data.put("uniqueId", meta.uniqueId);
        data.put("schema", meta.schema);
        data.put("table", meta.table);
        data.put("layer", layer);
        data.put("assetKey", safeAssetKey(meta));
        data.put("originalFilePath", meta.originalFilePath);
        data.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        return data.toString();
    }

    private String buildDbtLineageEvidence(ModelMeta model, UUID upstream, UUID downstream) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "dbt-manifest");
        data.put("uniqueId", model != null ? model.uniqueId : null);
        data.put("project", model != null ? resolveDbtProjectName(model.uniqueId) : null);
        data.put("upstreamDatasetId", upstream);
        data.put("downstreamDatasetId", downstream);
        data.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        return data.toString();
    }

    private String buildMaterializedTags(
        ModelMeta model,
        String controlledLayer,
        String projectName,
        String workspaceKey
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("warehouseLayer", controlledLayer);
        data.put("tenantId", model.meta.get("tenantId"));
        data.put("modelSpecId", model.meta.get("modelSpecId"));
        data.put("revision", model.meta.get("revision"));
        data.put("implementationRevision", model.meta.get("implementationRevision"));
        data.put("implementationChecksum", model.meta.get("implementationChecksum"));
        data.put("modelChecksum", model.meta.get("modelChecksum"));
        data.put("dbtUniqueId", model.uniqueId);
        data.put("dbtProject", projectName);
        data.put("dbtWorkspace", workspaceKey);
        data.put("database", model.database);
        data.put("schema", model.schema);
        data.put("identifier", model.table);
        data.put("materialization", model.materialization);
        data.put("runStatus", model.runEvidence.status);
        data.put("runInvocationId", model.runEvidence.invocationId);
        data.put("runGeneratedAt", model.runEvidence.generatedAt);
        data.put("materializedTruth", true);
        data.put("physicalAssetVerified", true);
        data.put("artifactState", "CURRENT");
        data.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            throw new IllegalArgumentException("dbt 物化资产标签序列化失败", exception);
        }
    }

    private ManifestEvidence manifestEvidence(Map<String, Object> manifest) {
        Map<String, Object> metadata = asMap(manifest == null ? null : manifest.get("metadata"));
        return new ManifestEvidence(
            text(metadata.get("invocation_id")),
            text(metadata.get("generated_at")),
            defaultIfBlank(text(metadata.get("project_name")), text(metadata.get("project_id")))
        );
    }

    private RunEvidence pairRunEvidence(
        String uniqueId,
        ManifestEvidence manifest,
        RunResults runResults,
        RunEvidence result
    ) {
        if (manifest == null || !StringUtils.hasText(manifest.invocationId())) {
            return unverifiedRunEvidence(result);
        }
        String modelProject = resolveDbtProjectName(uniqueId);
        if (!StringUtils.hasText(manifest.projectName()) || !manifest.projectName().equals(modelProject)) {
            return unverifiedRunEvidence(result);
        }
        if (
            runResults == null ||
            !runResults.filePresent() ||
            result == null ||
            !StringUtils.hasText(result.invocationId())
        ) {
            return unverifiedRunEvidence(result);
        }
        if (
            StringUtils.hasText(runResults.projectName()) &&
            (!runResults.projectName().equals(manifest.projectName()) || !runResults.projectName().equals(modelProject))
        ) {
            return unverifiedRunEvidence(result);
        }
        if (!manifest.invocationId().equals(result.invocationId())) {
            return unverifiedRunEvidence(result);
        }
        if (!"SUCCESS".equals(result.status())) {
            return isExplicitRunFailure(result.status())
                ? invalidRunEvidence(result, "RUN_FAILED")
                : unverifiedRunEvidence(result);
        }
        Instant manifestGeneratedAt = parseArtifactInstant(manifest.generatedAt());
        Instant runGeneratedAt = parseArtifactInstant(result.generatedAt());
        if (manifestGeneratedAt == null || runGeneratedAt == null) {
            return unverifiedRunEvidence(result);
        }
        if (runGeneratedAt.isBefore(manifestGeneratedAt)) {
            return unverifiedRunEvidence(result);
        }
        return new RunEvidence(result.status(), result.invocationId(), result.generatedAt(), true, null);
    }

    private RunEvidence unverifiedRunEvidence(RunEvidence result) {
        return new RunEvidence(
            result == null ? null : result.status(),
            result == null ? null : result.invocationId(),
            result == null ? null : result.generatedAt(),
            false,
            result == null ? null : "RUN_EVIDENCE_UNVERIFIED"
        );
    }

    private RunEvidence invalidRunEvidence(RunEvidence result, String staleReason) {
        return new RunEvidence(
            result == null ? null : result.status(),
            result == null ? null : result.invocationId(),
            result == null ? null : result.generatedAt(),
            false,
            staleReason
        );
    }

    private boolean isExplicitRunFailure(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ERROR", "FAIL", "FAILED", "RUNTIME_ERROR" -> true;
            default -> false;
        };
    }

    private Instant parseArtifactInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isPhysicalMaterialization(ModelMeta model) {
        if (
            model == null ||
            !StringUtils.hasText(model.database) ||
            !StringUtils.hasText(model.schema) ||
            !StringUtils.hasText(model.table) ||
            !StringUtils.hasText(model.materialization)
        ) {
            return false;
        }
        return switch (model.materialization.trim().toLowerCase(Locale.ROOT)) {
            case "view", "table", "incremental" -> true;
            default -> false;
        };
    }

    private boolean hasValidImplementationPin(ModelMeta model) {
        if (model == null || model.meta == null || !StringUtils.hasText(text(model.meta.get("modelSpecId")))) {
            return true;
        }
        String tenantId = text(model.meta.get("tenantId"));
        String modelChecksum = text(model.meta.get("modelChecksum"));
        String implementationChecksum = text(model.meta.get("implementationChecksum"));
        UUID modelSpecId = parseUuid(text(model.meta.get("modelSpecId")));
        Integer modelRevision = positiveIntegerValue(model.meta.get("revision"));
        Integer implementationRevision = positiveIntegerValue(model.meta.get("implementationRevision"));
        if (
            !StringUtils.hasText(tenantId) ||
            modelSpecId == null ||
            modelRevision == null ||
            implementationRevision == null ||
            !StringUtils.hasText(modelChecksum) ||
            !StringUtils.hasText(implementationChecksum)
        ) {
            return false;
        }
        ImplementationView current = lifecycleRepository.findImplementation(tenantId, modelSpecId).orElse(null);
        String projectKey = defaultIfBlank(text(model.meta.get("projectKey")), resolveDbtProjectName(model.uniqueId));
        return (
            current != null &&
            modelSpecId.equals(current.modelSpecId()) &&
            current.revision() == modelRevision &&
            modelChecksum.equals(current.modelChecksum()) &&
            current.implementationRevision() == implementationRevision &&
            implementationChecksum.equals(current.implementationChecksum()) &&
            StringUtils.hasText(projectKey) &&
            projectKey.equals(current.projectKey()) &&
            (!StringUtils.hasText(current.dbtUniqueId()) || model.uniqueId.equals(current.dbtUniqueId())) &&
            (!StringUtils.hasText(current.materialization()) || model.materialization.equalsIgnoreCase(current.materialization()))
        );
    }

    private boolean isLifecycleBoundModel(ModelMeta model) {
        return model != null &&
        model.meta != null &&
        StringUtils.hasText(text(model.meta.get("modelSpecId")));
    }

    private void importCurrentDbtArtifacts(ModelMeta model) throws com.fasterxml.jackson.core.JsonProcessingException {
        String tenantId = text(model.meta.get("tenantId"));
        UUID modelSpecId = parseUuid(text(model.meta.get("modelSpecId")));
        if (!StringUtils.hasText(tenantId) || modelSpecId == null) {
            return;
        }
        ImplementationView implementation = lifecycleRepository.findImplementation(tenantId, modelSpecId).orElse(null);
        if (implementation == null || implementation.ownership() != ImplementationMode.DBT_MANAGED) {
            return;
        }
        ModelSpecView modelSpec = modelSpecReader.get(tenantId, modelSpecId);
        if (
            modelSpec.implementationMode() != ImplementationMode.DBT_MANAGED ||
            modelSpec.revision() != implementation.revision() ||
            !java.util.Objects.equals(modelSpec.checksum(), implementation.modelChecksum()) ||
            !java.util.Objects.equals(modelSpec.planId(), implementation.planId()) ||
            !model.uniqueId.equals(implementation.dbtUniqueId()) ||
            !StringUtils.hasText(model.rawCode)
        ) {
            throw new IllegalStateException("dbt manifest artifact does not match the current DBT_MANAGED implementation");
        }
        String nodeName = model.uniqueId.substring(model.uniqueId.lastIndexOf('.') + 1);
        String sqlPath = StringUtils.hasText(model.originalFilePath)
            ? model.originalFilePath
            : "models/" + nodeName + ".sql";
        String schemaPath = sqlPath.endsWith(".sql")
            ? sqlPath.substring(0, sqlPath.length() - 4) + ".schema.json"
            : sqlPath + ".schema.json";
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("dbtUniqueId", model.uniqueId);
        schema.put("database", model.database);
        schema.put("schema", model.schema);
        schema.put("identifier", model.table);
        schema.put("columns", model.columns);
        String schemaContent = objectMapper.writeValueAsString(schema);
        lifecycleRepository.saveDbtManagedArtifacts(
            tenantId,
            modelSpec,
            implementation,
            "dbt-manifest:" + model.runEvidence.invocationId(),
            List.of(
                new ArtifactWrite("SQL", sqlPath, sha256(model.rawCode), model.rawCode, "MODEL", model.materialization, null),
                new ArtifactWrite("SCHEMA", schemaPath, sha256(schemaContent), schemaContent, "MODEL", model.materialization, null)
            ),
            Instant.now()
        );
    }

    private Integer positiveIntegerValue(Object value) {
        try {
            int parsed = value == null ? 0 : Integer.parseInt(String.valueOf(value).trim());
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveControlledLayer(ModelMeta model) {
        if (model == null || model.meta == null) {
            return null;
        }
        String configured = defaultIfBlank(text(model.meta.get("warehouseLayer")), text(model.meta.get("layer")));
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ODS", "STG", "DWD", "DWS", "ADS" -> normalized;
            default -> null;
        };
    }

    private void markMaterializedStale(
        ModelMeta model,
        UUID targetSourceId,
        SyncStats stats
    ) {
        markMaterializedStale(
            model,
            targetSourceId,
            model == null || model.runEvidence == null
                ? null
                : model.runEvidence.staleReason(),
            stats
        );
    }

    private void markMaterializedStale(
        ModelMeta model,
        UUID targetSourceId,
        String staleReason,
        SyncStats stats
    ) {
        if (model == null || !StringUtils.hasText(staleReason)) {
            return;
        }
        CatalogDataset dataset = findDataset(
            toNodeMeta(model, targetSourceId)
        );
        if (dataset == null) {
            return;
        }
        Map<String, Object> currentTags = jsonTags(dataset.getTags());
        String currentUniqueId = text(currentTags.get("dbtUniqueId"));
        if (!model.uniqueId.equals(currentUniqueId)) {
            return;
        }
        currentTags.put("dbtUniqueId", model.uniqueId);
        currentTags.put("database", model.database);
        currentTags.put("schema", model.schema);
        currentTags.put("identifier", model.table);
        currentTags.put("materialization", model.materialization);
        currentTags.put("runStatus", model.runEvidence.status());
        currentTags.put("runInvocationId", model.runEvidence.invocationId());
        currentTags.put("runGeneratedAt", model.runEvidence.generatedAt());
        currentTags.put("materializedTruth", false);
        currentTags.put("physicalAssetVerified", false);
        currentTags.put("artifactState", "STALE");
        currentTags.put("staleReason", staleReason);
        currentTags.values().removeIf(value -> value == null || !StringUtils.hasText(String.valueOf(value)));
        markDatasetStale(dataset, currentTags, stats);
    }

    private void reconcileDbtMaterializations(
        String projectName,
        String workspaceKey,
        Set<String> currentPhysicalAssetKeys,
        SyncStats stats
    ) {
        if (
            !StringUtils.hasText(projectName) ||
            !StringUtils.hasText(workspaceKey) ||
            currentPhysicalAssetKeys == null
        ) {
            return;
        }
        for (CatalogDataset dataset : datasetRepository.findAll()) {
            if (dataset == null || dataset.getId() == null) {
                continue;
            }
            Map<String, Object> currentTags = jsonTags(dataset.getTags());
            String uniqueId = text(currentTags.get("dbtUniqueId"));
            String physicalAssetKey = physicalMaterializationKey(
                uniqueId,
                text(currentTags.get("database")),
                defaultIfBlank(text(currentTags.get("schema")), dataset.getHiveDatabase()),
                defaultIfBlank(text(currentTags.get("identifier")), dataset.getHiveTable())
            );
            if (
                !projectName.equals(text(currentTags.get("dbtProject"))) ||
                !workspaceKey.equals(text(currentTags.get("dbtWorkspace"))) ||
                !projectName.equals(resolveDbtProjectName(uniqueId)) ||
                currentPhysicalAssetKeys.contains(physicalAssetKey) ||
                !isCurrentDbtMaterialization(currentTags)
            ) {
                continue;
            }
            currentTags.put("materializedTruth", false);
            currentTags.put("physicalAssetVerified", false);
            currentTags.put("artifactState", "STALE");
            currentTags.put("staleReason", "MANIFEST_MODEL_REMOVED_OR_NON_PHYSICAL");
            markDatasetStale(dataset, currentTags, stats);
        }
    }

    private String physicalMaterializationKey(ModelMeta model) {
        if (model == null) return null;
        return physicalMaterializationKey(model.uniqueId, model.database, model.schema, model.table);
    }

    private String physicalMaterializationKey(String uniqueId, String database, String schema, String identifier) {
        return String.join(
            "|",
            normalizeKeyPart(uniqueId),
            normalizeKeyPart(database),
            normalizeKeyPart(schema),
            normalizeKeyPart(identifier)
        );
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isCurrentDbtMaterialization(Map<String, Object> tags) {
        return (
            Boolean.parseBoolean(String.valueOf(tags.get("materializedTruth"))) ||
            Boolean.parseBoolean(String.valueOf(tags.get("physicalAssetVerified")))
        );
    }

    private void markDatasetStale(CatalogDataset dataset, Map<String, Object> tags, SyncStats stats) {
        try {
            dataset.setTags(objectMapper.writeValueAsString(tags));
        } catch (Exception exception) {
            throw new IllegalArgumentException("dbt 失效资产标签序列化失败", exception);
        }
        dataset.setEnabled(Boolean.FALSE);
        dataset.setLifecycleStatus("STALE");
        dataset.setSnapshotTime(Instant.now());
        datasetRepository.save(dataset);
        stats.updated++;
        stats.manifestEvidenceUpdated++;
        revokeMaterializedLineage(dataset.getId(), stats);
    }

    private String dbtWorkspaceKey(String projectDir) {
        if (!StringUtils.hasText(projectDir)) {
            return null;
        }
        String normalized = Path.of(projectDir).toAbsolutePath().normalize().toString();
        return UUID.nameUUIDFromBytes(("dbt-workspace:" + normalized).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private Map<String, Object> jsonTags(String tags) {
        if (!StringUtils.hasText(tags) || !tags.trim().startsWith("{")) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(tags, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void revokeMaterializedLineage(UUID datasetId, SyncStats stats) {
        if (datasetId == null) {
            return;
        }
        Instant revokedAt = Instant.now();
        List<CatalogDatasetLineage> datasetLineage = lineageRepository.findCurrentByDownstreamDatasetIdAndRelationTypeIgnoreCase(
            datasetId,
            "DBT"
        );
        if (datasetLineage != null) {
            for (CatalogDatasetLineage lineage : datasetLineage) {
                if (lineage != null && lineage.getValidTo() == null) {
                    lineage.setValidTo(revokedAt);
                    lineageRepository.save(lineage);
                    stats.lineageRemoved++;
                }
            }
        }
        List<CatalogColumnLineage> columnLineage = columnLineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(
            datasetId,
            "DBT"
        );
        if (columnLineage != null) {
            for (CatalogColumnLineage lineage : columnLineage) {
                if (lineage != null && lineage.getValidTo() == null) {
                    lineage.setValidTo(revokedAt);
                    columnLineageRepository.save(lineage);
                    stats.columnLineageRemoved++;
                }
            }
        }
    }

    private String safeAssetKey(NodeMeta meta) {
        if (meta == null) {
            return null;
        }
        try {
            return CatalogAssetKey.dataset(meta.sourceId, meta.schema, meta.schema, meta.table, meta.table);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolveDatasetType(DbtConfigService.DbtConfigView view) {
        if (view != null && view.target() != null && StringUtils.hasText(view.target().type())) {
            return view.target().type().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private ModelMeta toModelMeta(String uniqueId, Map<String, Object> node, RunEvidence runEvidence) {
        String name = text(node.get("name"));
        String database = text(node.get("database"));
        String schema = text(node.get("schema"));
        String identifier = defaultIfBlank(
            text(node.get("identifier")),
            defaultIfBlank(text(node.get("alias")), name)
        );
        String table = identifier;
        List<String> dependsOn = extractDepends(node.get("depends_on"));
        String description = text(node.get("description"));
        Map<String, Object> columns = asMap(node.get("columns"));
        String filePath = text(node.get("original_file_path"));
        String rawCode = defaultIfBlank(text(node.get("raw_code")), text(node.get("raw_sql")));
        String compiledCode = defaultIfBlank(text(node.get("compiled_code")), defaultIfBlank(text(node.get("compiled_sql")), rawCode));
        Map<String, Object> config = asMap(node.get("config"));
        Map<String, Object> meta = new LinkedHashMap<>(asMap(config.get("meta")));
        if (meta.isEmpty()) {
            meta.putAll(asMap(node.get("meta")));
        }
        return new ModelMeta(
            uniqueId,
            database,
            schema,
            table,
            text(config.get("materialized")),
            dependsOn,
            description,
            columns,
            filePath,
            rawCode,
            compiledCode,
            meta,
            runEvidence
        );
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

    private NodeMeta toNodeMeta(
        ModelMeta meta,
        UUID targetSourceId
    ) {
        if (meta == null) {
            return null;
        }
        String schema = StringUtils.hasText(meta.schema) ? meta.schema : meta.database;
        return new NodeMeta(
            meta.uniqueId,
            schema,
            meta.table,
            targetSourceId,
            meta.description,
            meta.columns,
            meta.originalFilePath
        );
    }

    private UUID resolveTargetSourceId(
        DbtConfigService.DbtConfigView view
    ) {
        if (
            view != null &&
            view.config() != null &&
            view.config().targetDataSourceId() != null
        ) {
            return view.config().targetDataSourceId();
        }
        return view != null && view.target() != null
            ? view.target().id()
            : null;
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private boolean isMaterializedRelation(ModelMeta model) {
        if (
            model == null ||
            model.runEvidence == null ||
            !model.runEvidence.current() ||
            !"SUCCESS".equals(model.runEvidence.status)
        ) {
            return false;
        }
        return isPhysicalMaterialization(model);
    }

    private RunResults readRunResults(String projectDir) throws java.io.IOException {
        Path path = Path.of(projectDir, "target", "run_results.json");
        File file = path.toFile();
        if (!file.exists()) {
            return new RunResults(Map.of(), null, null, null, false);
        }
        Map<String, Object> raw = objectMapper.readValue(file, new TypeReference<>() {});
        Map<String, Object> metadata = asMap(raw.get("metadata"));
        String invocationId = text(metadata.get("invocation_id"));
        String generatedAt = text(metadata.get("generated_at"));
        String projectName = defaultIfBlank(text(metadata.get("project_name")), text(metadata.get("project_id")));
        Map<String, RunEvidence> byUniqueId = new LinkedHashMap<>();
        Object values = raw.get("results");
        if (values instanceof List<?> results) {
            for (Object value : results) {
                Map<String, Object> result = asMap(value);
                String uniqueId = text(result.get("unique_id"));
                if (!StringUtils.hasText(uniqueId)) {
                    continue;
                }
                byUniqueId.put(
                    uniqueId,
                    new RunEvidence(normalizeRunStatus(text(result.get("status"))), invocationId, generatedAt, false, null)
                );
            }
        }
        return new RunResults(Map.copyOf(byUniqueId), invocationId, generatedAt, projectName, true);
    }

    private String normalizeRunStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
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
        int manifestEvidenceUpdated = 0;
        int columnLineageCreated = 0;
        int columnLineageUpdated = 0;
        int columnLineageRemoved = 0;
        int classificationPropagationEnqueued = 0;

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

        public int getManifestEvidenceUpdated() {
            return manifestEvidenceUpdated;
        }

        public int getColumnLineageCreated() {
            return columnLineageCreated;
        }

        public int getColumnLineageUpdated() {
            return columnLineageUpdated;
        }

        public int getColumnLineageRemoved() {
            return columnLineageRemoved;
        }

        public int getClassificationPropagationEnqueued() {
            return classificationPropagationEnqueued;
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
        String materialization,
        List<String> dependsOn,
        String description,
        Map<String, Object> columns,
        String originalFilePath,
        String rawCode,
        String compiledCode,
        Map<String, Object> meta,
        RunEvidence runEvidence
    ) {}

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ManifestEvidence(String invocationId, String generatedAt, String projectName) {}

    private record RunResults(
        Map<String, RunEvidence> byUniqueId,
        String invocationId,
        String generatedAt,
        String projectName,
        boolean filePresent
    ) {}

    private record RunEvidence(String status, String invocationId, String generatedAt, boolean current, String staleReason) {}

    private record LineageSyncStats(int created, int removed, int propagationEnqueued) {}

    private record ColumnLineageSyncStats(int created, int updated, int removed) {}
}
