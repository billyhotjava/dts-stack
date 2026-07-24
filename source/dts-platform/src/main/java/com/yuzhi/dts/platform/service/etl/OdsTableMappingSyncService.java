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
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
    private final IngestionLineageWriter ingestionLineageWriter;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public OdsTableMappingSyncService(
        InfraOdsTableMappingRepository mappingRepository,
        InfraDataSourceRepository dataSourceRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSyncService columnSyncService,
        DbtSourceService dbtSourceService,
        IngestionLineageWriter ingestionLineageWriter,
        AuditService auditService,
        ObjectMapper objectMapper,
        EntityManager entityManager
    ) {
        this.mappingRepository = mappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnSyncService = columnSyncService;
        this.dbtSourceService = dbtSourceService;
        this.ingestionLineageWriter = ingestionLineageWriter;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public SyncResult syncFromIngestionPayload(Map<String, Object> payload) {
        return syncFromIngestionPayload(payload, IngestionLineageWriter.LineageObservation.declared());
    }

    @Transactional
    public SyncResult syncFromIngestionPayload(Map<String, Object> payload, IngestionLineageWriter.LineageObservation observation) {
        Map<String, Object> task = unwrapTask(payload);
        if (task == null || task.isEmpty()) {
            return SyncResult.empty("未发现任务数据");
        }
        if (isApiTask(task)) {
            return syncApiLandingTargets(task, payload, observation);
        }
        UUID connectionId = resolveConnectionId(task);
        if (connectionId == null) {
            return SyncResult.empty("未解析到数据源连接 ID");
        }
        List<Map<String, String>> mappings = readTableMappings(task.get("tableMapping"));
        Map<String, Object> destinationConfig = readMap(task.get("destinationConfig"));
        String taskName = normalize(task.get("name"));
        String taskId = normalize(task.get("id"));
        Set<String> incomingSourceKeys = collectSourceKeys(mappings);
        int removed = pruneStaleTaskMappings(connectionId, incomingSourceKeys, taskName, taskId);
        Instant snapshotTime = Instant.now();
        int updated = 0;
        int lineageCreated = 0;
        int lineageUpdated = 0;
        int lineageSkipped = 0;
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
            String nextDescription = buildDescription(taskName, sourceRef, targetRef, taskId);
            if (!StringUtils.hasText(entity.getDescription()) || isManagedByTask(entity, taskName, taskId)) {
                entity.setDescription(nextDescription);
            }
            InfraOdsTableMapping savedMapping = mappingRepository.save(entity);
            updated++;
            IngestionLineageWriter.LineageWriteResult lineage = ingestionLineageWriter.writeAddaxLineage(savedMapping, observation);
            lineageCreated += lineage.created();
            lineageUpdated += lineage.updated();
            lineageSkipped += lineage.skipped();
            if (!columnSpecs.isEmpty()) {
                syncColumns(entity, targetRef, columnSpecs, connectionId, snapshotTime);
            }
        }
        if (updated == 0 && removed == 0) {
            return SyncResult.empty("无可同步的表映射");
        }
        DbtSourceService.DbtSourceRefreshResult refresh = dbtSourceService.refreshOdsSources();
        auditService.auditAction(
            "INGESTION_MAPPING_SYNC",
            AuditStage.SUCCESS,
            taskName,
            Map.of(
                "summary",
                "同步 ODS 映射",
                "task",
                taskName,
                "tables",
                updated,
                "removed",
                removed,
                "lineageCreated",
                lineageCreated,
                "lineageUpdated",
                lineageUpdated,
                "lineageSkipped",
                lineageSkipped,
                "dbt",
                refresh.message()
            )
        );
        String message = refresh.message() + "；ADDAX 血缘 +" + lineageCreated + " / 更新 " + lineageUpdated + " / 跳过 " + lineageSkipped;
        return SyncResult.success(updated + removed, message);
    }

    private SyncResult syncApiLandingTargets(
        Map<String, Object> task,
        Map<String, Object> payload,
        IngestionLineageWriter.LineageObservation observation
    ) {
        Map<String, Object> execution = readMap(payload == null ? null : payload.get("execution"));
        ApiExecutionEvidence executionEvidence = resolveApiExecutionEvidence(task, execution, observation);
        if (executionEvidence == null) {
            return SyncResult.empty("API 落地证据不完整或执行未成功");
        }
        List<Map<String, Object>> targetTables = readObjectMaps(execution.get("targetTables"));
        if (targetTables.isEmpty()) {
            return SyncResult.empty("未发现 API 落地目标表");
        }
        UUID connectionId = executionEvidence.connectionId();
        String taskName = normalize(task.get("name"));
        String taskId = executionEvidence.taskId();
        String sourceNamespace = "api:" + taskId;
        List<ApiLandingTarget> targets = resolveApiLandingTargets(targetTables, executionEvidence.executionId());
        if (targets.size() != targetTables.size()) {
            return SyncResult.empty("API 落地目标证据不完整");
        }
        if (
            entityManager.find(
                InfraDataSource.class,
                connectionId,
                LockModeType.PESSIMISTIC_WRITE
            ) == null
        ) {
            return SyncResult.empty("API 源连接不存在，无法锁定落地证据");
        }
        String orderingConflict = validateApiLandingOrder(executionEvidence, targets);
        if (StringUtils.hasText(orderingConflict)) {
            return SyncResult.empty(orderingConflict);
        }
        Set<String> incomingSourceKeys = new LinkedHashSet<>();
        for (ApiLandingTarget target : targets) {
            incomingSourceKeys.add(buildSourceKey(sourceNamespace, target.resourceName()));
        }
        int removed = pruneStaleTaskMappings(connectionId, incomingSourceKeys, taskName, taskId);
        int updated = 0;
        int lineageCreated = 0;
        int lineageUpdated = 0;
        int lineageSkipped = 0;
        Instant snapshotTime = Instant.now();
        IngestionLineageWriter.LineageObservation effectiveObservation = observation;
        for (ApiLandingTarget target : targets) {
            Optional<InfraOdsTableMapping> existing = mappingRepository.findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(
                connectionId,
                target.resourceName(),
                sourceNamespace
            );
            InfraOdsTableMapping mapping = existing.orElseGet(InfraOdsTableMapping::new);
            if (mapping.getId() == null) {
                mapping.setId(apiMappingId(connectionId, taskId, target.resourceName()));
            }
            mapping.setConnectionId(connectionId);
            mapping.setStreamName(target.resourceName());
            mapping.setStreamNamespace(sourceNamespace);
            OdsNameParts parts = parseOdsName(target.tableRef().table());
            mapping.setSystemCode(parts.system());
            mapping.setBizCode(parts.biz());
            mapping.setEntityCode(parts.entity());
            mapping.setOdsSchema(target.tableRef().schema());
            mapping.setOdsTable(target.tableRef().table());
            mapping.setEnabled(Boolean.TRUE);
            String description = buildDescription(taskName, new TableRef(sourceNamespace, target.resourceName()), target.tableRef(), taskId);
            if (!StringUtils.hasText(mapping.getDescription()) || isManagedByTask(mapping, taskName, taskId)) {
                mapping.setDescription(description);
            }
            CatalogDataset dataset = ensureApiDataset(executionEvidence, target, snapshotTime);
            if (dataset == null) {
                return SyncResult.empty("API 目录资产身份冲突");
            }
            CatalogTableSchema table = ensureApiTable(dataset, target);
            if (table == null) {
                return SyncResult.empty("API 目录表身份冲突");
            }
            String evidenceTags = apiEvidenceTags(task, target, executionEvidence, effectiveObservation);
            dataset.setTags(evidenceTags);
            dataset.setEnabled(Boolean.TRUE);
            dataset = datasetRepository.save(dataset);
            CatalogDataset reloaded = datasetRepository.findById(dataset.getId()).orElse(dataset);
            if (!matchesApiIdentity(reloaded, executionEvidence, target) || !matchesApiExecution(reloaded.getTags(), executionEvidence, target)) {
                return SyncResult.empty("API 目录资产并发冲突");
            }
            table.setDataset(reloaded);
            table.setTags(evidenceTags);
            table = tableRepository.save(table);
            CatalogTableSchema reloadedTable = tableRepository.findById(table.getId()).orElse(table);
            if (reloadedTable.getDataset() == null || !reloaded.getId().equals(reloadedTable.getDataset().getId())) {
                return SyncResult.empty("API 目录表并发冲突");
            }
            if (!target.columnSpecs().isEmpty()) {
                columnSyncService.upsertColumns(reloadedTable, target.columnSpecs(), CatalogColumnSyncService.STATUS_ACTIVE);
            }
            mapping.setDatasetId(reloaded.getId());
            InfraOdsTableMapping savedMapping = mappingRepository.save(mapping);
            updated++;
            IngestionLineageWriter.LineageWriteResult lineage = ingestionLineageWriter.writeIngestionLineage(
                savedMapping,
                effectiveObservation,
                IngestionLineageWriter.RELATION_API
            );
            lineageCreated += lineage.created();
            lineageUpdated += lineage.updated();
            lineageSkipped += lineage.skipped();
        }
        DbtSourceService.DbtSourceRefreshResult refresh = dbtSourceService.refreshOdsSources();
        auditService.auditAction(
            "INGESTION_MAPPING_SYNC",
            AuditStage.SUCCESS,
            taskName,
            Map.of(
                "summary", "同步 API 落地目录",
                "task", taskName,
                "tables", updated,
                "removed", removed,
                "lineageCreated", lineageCreated,
                "lineageUpdated", lineageUpdated,
                "lineageSkipped", lineageSkipped,
                "dbt", refresh.message()
            )
        );
        return SyncResult.success(
            updated + removed,
            refresh.message() + "；API 血缘 +" + lineageCreated + " / 更新 " + lineageUpdated + " / 跳过 " + lineageSkipped
        );
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
        int lineageRemoved = 0;
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
                InfraOdsTableMapping existingMapping = existing.orElseThrow();
                lineageRemoved += ingestionLineageWriter.removeAddaxLineage(existingMapping);
                mappingRepository.delete(existingMapping);
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
            Map.of("summary", "删除 ODS 映射", "task", taskName, "tables", deleted, "lineageRemoved", lineageRemoved, "dbt", refresh.message())
        );
        return SyncResult.success(deleted, refresh.message() + "；ADDAX 血缘删除 " + lineageRemoved);
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

    private List<Map<String, Object>> readObjectMaps(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                Map<String, Object> value = readMap(item);
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
            return result;
        }
        Map<String, Object> value = readMap(raw);
        return value.isEmpty() ? List.of() : List.of(value);
    }

    private List<ApiLandingTarget> resolveApiLandingTargets(List<Map<String, Object>> targetTables, String executionId) {
        List<ApiLandingTarget> targets = new ArrayList<>();
        for (Map<String, Object> target : targetTables) {
            if (target == null) {
                continue;
            }
            String qualifiedName = normalize(target.get("qualifiedName"));
            if (!StringUtils.hasText(qualifiedName)) {
                continue;
            }
            TableRef targetRef = splitTarget(qualifiedName, Map.of());
            if (!StringUtils.hasText(targetRef.table())) {
                continue;
            }
            String resourceName = normalize(target.get("resourceId"));
            String targetExecutionId = normalize(target.get("executionId"));
            String landingStatus = normalize(target.get("landingStatus"));
            String configChecksum = normalize(target.get("configChecksum"));
            String fieldSnapshotChecksum = normalize(target.get("fieldSnapshotChecksum"));
            Long rowsWritten = parseNonNegativeLong(target.get("rowsWritten"));
            if (
                !StringUtils.hasText(resourceName) ||
                !StringUtils.hasText(targetExecutionId) ||
                !targetExecutionId.equals(executionId) ||
                !"SUCCESS".equalsIgnoreCase(landingStatus) ||
                !StringUtils.hasText(configChecksum) ||
                !StringUtils.hasText(fieldSnapshotChecksum) ||
                rowsWritten == null
            ) {
                continue;
            }
            targets.add(new ApiLandingTarget(targetRef, resourceName, target, apiColumnSpecs(target)));
        }
        return targets;
    }

    private List<ColumnSpec> apiColumnSpecs(Map<String, Object> target) {
        LinkedHashMap<String, ColumnSpec> result = new LinkedHashMap<>();
        Object snapshot = target.get("fieldSnapshot");
        if (snapshot instanceof Map<?, ?> snapshotMap) {
            Map<String, Object> snapshotValues = castMap(snapshotMap);
            snapshot = firstNonNull(snapshotValues.get("columns"), snapshotValues.get("fields"));
        }
        if (snapshot == null) {
            snapshot = firstNonNull(target.get("columns"), target.get("fields"));
        }
        addColumnSpecs(result, extractColumnSpecs(snapshot));
        String rawRecordColumn = normalize(target.get("rawRecordColumn"));
        if (StringUtils.hasText(rawRecordColumn)) {
            addColumnSpecs(result, List.of(new ColumnSpec(rawRecordColumn, "jsonb", Boolean.FALSE, null, null, null, null, null)));
        }
        Object technicalColumns = target.get("technicalColumns");
        if (technicalColumns instanceof List<?> list) {
            for (Object column : list) {
                String name = normalize(column);
                if (StringUtils.hasText(name)) {
                    addColumnSpecs(result, List.of(new ColumnSpec(name, "string", null, null, null, null, null, null)));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private void addColumnSpecs(Map<String, ColumnSpec> destination, List<ColumnSpec> specs) {
        if (destination == null || specs == null) {
            return;
        }
        for (ColumnSpec spec : specs) {
            if (spec == null || !StringUtils.hasText(spec.name())) {
                continue;
            }
            destination.putIfAbsent(spec.name().trim().toLowerCase(Locale.ROOT), spec);
        }
    }

    private String apiEvidenceTags(
        Map<String, Object> task,
        ApiLandingTarget target,
        ApiExecutionEvidence execution,
        IngestionLineageWriter.LineageObservation observation
    ) {
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("origin", "API");
        putEvidence(evidence, "connectionId", execution.connectionId().toString());
        putEvidence(evidence, "taskId", execution.taskId());
        putEvidence(evidence, "taskName", normalize(task.get("name")));
        putEvidence(evidence, "resourceId", target.resourceName());
        putEvidence(evidence, "qualifiedName", normalize(target.payload().get("qualifiedName")));
        putEvidence(evidence, "taskRevision", execution.taskRevision().toString());
        putEvidence(evidence, "executionSequence", Long.toString(execution.executionSequence()));
        putEvidence(evidence, "configChecksum", normalize(target.payload().get("configChecksum")));
        putEvidence(evidence, "fieldSnapshotChecksum", normalize(target.payload().get("fieldSnapshotChecksum")));
        putEvidence(evidence, "executionId", execution.executionId());
        putEvidence(evidence, "batchId", observation.batchId());
        putEvidence(evidence, "executionStatus", "success");
        putEvidence(evidence, "landingStatus", "SUCCESS");
        putEvidence(evidence, "landingTruth", "VERIFIED");
        putEvidence(evidence, "rowsWritten", normalize(target.payload().get("rowsWritten")));
        putEvidence(evidence, "observedAt", observation.observedAt() == null ? execution.completedAt().toString() : observation.observedAt().toString());
        putEvidence(evidence, "checkpoint", firstNonEmpty(
            normalize(target.payload().get("cursorValue")),
            normalize(target.payload().get("checkpoint"))
        ));
        return truncate(evidence.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(";")), 1024);
    }

    private void putEvidence(Map<String, String> evidence, String key, String value) {
        if (StringUtils.hasText(value)) {
            evidence.put(key, value.trim());
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private boolean isApiTask(Map<String, Object> task) {
        if ("API".equalsIgnoreCase(normalize(task.get("sourceKind")))) {
            return true;
        }
        String sourceType = normalize(task.get("sourceType"));
        if (!StringUtils.hasText(sourceType)) {
            return false;
        }
        String normalized = sourceType.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return "API".equals(normalized) || "HTTP".equals(normalized) || "HTTPREADER".equals(normalized) || "RESTAPI".equals(normalized);
    }

    private ApiExecutionEvidence resolveApiExecutionEvidence(
        Map<String, Object> task,
        Map<String, Object> execution,
        IngestionLineageWriter.LineageObservation observation
    ) {
        UUID connectionId = parseUuid(task.get("sourceDataSourceId"));
        String taskId = normalize(task.get("id"));
        Instant taskRevision = parseInstant(task.get("taskRevision"));
        Long executionSequence = parseNonNegativeLong(firstNonNull(execution.get("executionSequence"), execution.get("id")));
        String executionId = normalize(execution.get("executionId"));
        String executionStatus = normalize(execution.get("status"));
        Instant completedAt = parseInstant(execution.get("endTime"));
        if (
            connectionId == null ||
            !StringUtils.hasText(taskId) ||
            taskRevision == null ||
            executionSequence == null ||
            !StringUtils.hasText(executionId) ||
            !"SUCCESS".equalsIgnoreCase(executionStatus) ||
            completedAt == null ||
            observation == null ||
            !IngestionLineageWriter.STATUS_VERIFIED.equalsIgnoreCase(observation.verificationStatus()) ||
            !"SUCCESS".equalsIgnoreCase(observation.executionStatus()) ||
            !executionId.equals(observation.executionId())
        ) {
            return null;
        }
        return new ApiExecutionEvidence(connectionId, taskId, taskRevision, executionSequence, executionId, completedAt);
    }

    private String validateApiLandingOrder(ApiExecutionEvidence execution, List<ApiLandingTarget> targets) {
        for (ApiLandingTarget target : targets) {
            CatalogDataset existing = datasetRepository.findById(apiDatasetId(execution, target)).orElse(null);
            if (existing == null) {
                continue;
            }
            if (!matchesApiIdentity(existing, execution, target)) {
                return "API 目录资产身份冲突";
            }
            Map<String, String> evidence = parseEvidenceTags(existing.getTags());
            if (evidence.isEmpty()) {
                continue;
            }
            if (!"API".equalsIgnoreCase(evidence.get("origin"))) {
                return "API 目录资产身份冲突";
            }
            Instant currentTaskRevision = parseInstant(evidence.get("taskRevision"));
            Long currentSequence = parseNonNegativeLong(evidence.get("executionSequence"));
            if (
                !execution.connectionId().toString().equals(evidence.get("connectionId")) ||
                !execution.taskId().equals(evidence.get("taskId")) ||
                !target.resourceName().equals(evidence.get("resourceId")) ||
                currentTaskRevision == null ||
                currentSequence == null
            ) {
                return "API 目录资产缺少可排序证据";
            }
            if (currentTaskRevision.isAfter(execution.taskRevision()) || currentSequence > execution.executionSequence()) {
                return "检测到旧 API 执行，拒绝覆盖当前目录证据";
            }
            if (
                currentSequence == execution.executionSequence() &&
                (!execution.executionId().equals(evidence.get("executionId")) ||
                    !normalize(target.payload().get("configChecksum")).equals(evidence.get("configChecksum")) ||
                    !normalize(target.payload().get("fieldSnapshotChecksum")).equals(evidence.get("fieldSnapshotChecksum")))
            ) {
                return "检测到 API 并发执行证据冲突";
            }
        }
        return null;
    }

    private CatalogDataset ensureApiDataset(ApiExecutionEvidence execution, ApiLandingTarget target, Instant snapshotTime) {
        UUID datasetId = apiDatasetId(execution, target);
        CatalogDataset dataset = datasetRepository.findById(datasetId).orElseGet(CatalogDataset::new);
        if (dataset.getId() == null) {
            dataset.setId(datasetId);
        }
        if (!matchesApiIdentity(dataset, execution, target)) {
            return null;
        }
        dataset.setName(target.tableRef().table());
        dataset.setHiveDatabase(target.tableRef().schema());
        dataset.setHiveTable(target.tableRef().table());
        dataset.setSourceId(execution.connectionId());
        dataset.setType("DATASET");
        dataset.setWarehouseLayer("ODS");
        dataset.setSnapshotTime(snapshotTime);
        return dataset;
    }

    private CatalogTableSchema ensureApiTable(CatalogDataset dataset, ApiLandingTarget target) {
        UUID tableId = apiTableId(dataset.getId());
        CatalogTableSchema table = tableRepository.findById(tableId).orElseGet(CatalogTableSchema::new);
        if (table.getId() == null) {
            table.setId(tableId);
        }
        if (table.getDataset() != null && !dataset.getId().equals(table.getDataset().getId())) {
            return null;
        }
        table.setDataset(dataset);
        table.setName(target.tableRef().table());
        return table;
    }

    private boolean matchesApiIdentity(CatalogDataset dataset, ApiExecutionEvidence execution, ApiLandingTarget target) {
        if (dataset == null) {
            return false;
        }
        return (
            (dataset.getId() == null || apiDatasetId(execution, target).equals(dataset.getId())) &&
            (dataset.getSourceId() == null || execution.connectionId().equals(dataset.getSourceId())) &&
            (!StringUtils.hasText(dataset.getHiveDatabase()) || target.tableRef().schema().equalsIgnoreCase(dataset.getHiveDatabase())) &&
            (!StringUtils.hasText(dataset.getHiveTable()) || target.tableRef().table().equalsIgnoreCase(dataset.getHiveTable()))
        );
    }

    private boolean matchesApiExecution(String tags, ApiExecutionEvidence execution, ApiLandingTarget target) {
        Map<String, String> evidence = parseEvidenceTags(tags);
        return (
            execution.connectionId().toString().equals(evidence.get("connectionId")) &&
            execution.taskId().equals(evidence.get("taskId")) &&
            target.resourceName().equals(evidence.get("resourceId")) &&
            Long.toString(execution.executionSequence()).equals(evidence.get("executionSequence")) &&
            execution.executionId().equals(evidence.get("executionId")) &&
            "success".equalsIgnoreCase(evidence.get("executionStatus")) &&
            "SUCCESS".equalsIgnoreCase(evidence.get("landingStatus")) &&
            "VERIFIED".equalsIgnoreCase(evidence.get("landingTruth")) &&
            normalize(target.payload().get("configChecksum")).equals(evidence.get("configChecksum")) &&
            normalize(target.payload().get("fieldSnapshotChecksum")).equals(evidence.get("fieldSnapshotChecksum"))
        );
    }

    private Map<String, String> parseEvidenceTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Map.of();
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        for (String part : tags.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0 && separator < part.length() - 1) {
                evidence.put(part.substring(0, separator).trim(), part.substring(separator + 1).trim());
            }
        }
        return evidence;
    }

    private String truncate(String input, int max) {
        if (input == null) return "";
        if (max <= 0) return "";
        return input.length() <= max ? input : input.substring(0, Math.min(input.length(), max));
    }

    private UUID apiDatasetId(ApiExecutionEvidence execution, ApiLandingTarget target) {
        String value =
            "api-landing-dataset:" + execution.connectionId() + ":api:" + execution.taskId() + ":" + target.resourceName();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private UUID apiTableId(UUID datasetId) {
        return UUID.nameUUIDFromBytes(("api-landing-table:" + datasetId).getBytes(StandardCharsets.UTF_8));
    }

    private UUID apiMappingId(UUID connectionId, String taskId, String resourceId) {
        String value = "api-landing-mapping:" + connectionId + ":api:" + taskId + ":" + resourceId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private UUID parseUuid(Object value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Instant parseInstant(Object value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long parseNonNegativeLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed >= 0 ? parsed : null;
        }
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(normalized);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
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
        if (!StringUtils.hasText(schema) && looksLikePostgresDestination(destinationConfig)) {
            return "public";
        }
        return StringUtils.hasText(schema) ? schema : DEFAULT_SCHEMA;
    }

    private boolean looksLikePostgresDestination(Map<String, Object> destinationConfig) {
        String writerType = firstNonEmpty(
            normalize(destinationConfig.get("writerType")),
            normalize(destinationConfig.get("writer")),
            normalize(destinationConfig.get("type")),
            normalize(destinationConfig.get("destinationType")),
            normalize(destinationConfig.get("destinationDefinitionId"))
        );
        if (StringUtils.hasText(writerType)) {
            String normalized = writerType.toLowerCase(Locale.ROOT);
            if (normalized.contains("postgres")) {
                return true;
            }
        }
        String jdbcUrl = firstNonEmpty(
            normalize(destinationConfig.get("jdbcUrl")),
            normalize(destinationConfig.get("jdbc_url")),
            normalize(destinationConfig.get("url"))
        );
        return StringUtils.hasText(jdbcUrl) && jdbcUrl.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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

    private String buildDescription(String taskName, TableRef source, TableRef target, String taskId) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(taskId)) {
            sb.append("[task:").append(taskId).append("] ");
        }
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

    private Set<String> collectSourceKeys(List<Map<String, String>> mappings) {
        Set<String> keys = new LinkedHashSet<>();
        if (mappings == null || mappings.isEmpty()) {
            return keys;
        }
        for (Map<String, String> mapping : mappings) {
            if (mapping == null) continue;
            String source = normalize(mapping.get("source"));
            if (!StringUtils.hasText(source)) continue;
            TableRef ref = splitTable(source);
            String key = buildSourceKey(normalizeNamespace(ref.namespace()), ref.name());
            if (StringUtils.hasText(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private int pruneStaleTaskMappings(UUID connectionId, Set<String> incomingSourceKeys, String taskName, String taskId) {
        if (connectionId == null) {
            return 0;
        }
        int removed = 0;
        List<InfraOdsTableMapping> existing = mappingRepository.findByConnectionIdOrderByCreatedDateDesc(connectionId);
        for (InfraOdsTableMapping mapping : existing) {
            if (mapping == null || !isManagedByTask(mapping, taskName, taskId)) {
                continue;
            }
            String key = buildSourceKey(normalizeNamespace(mapping.getStreamNamespace()), mapping.getStreamName());
            if (!incomingSourceKeys.contains(key)) {
                mappingRepository.delete(mapping);
                removed++;
            }
        }
        return removed;
    }

    private boolean isManagedByTask(InfraOdsTableMapping mapping, String taskName, String taskId) {
        if (mapping == null || !StringUtils.hasText(mapping.getDescription())) {
            return false;
        }
        String description = mapping.getDescription().trim();
        if (StringUtils.hasText(taskId) && description.startsWith("[task:" + taskId + "]")) {
            return true;
        }
        return StringUtils.hasText(taskName) && description.startsWith(taskName + ":");
    }

    private String buildSourceKey(String namespace, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        String normalizedNamespace = StringUtils.hasText(namespace) ? namespace.trim().toLowerCase(Locale.ROOT) : "";
        return normalizedNamespace + "|" + normalizedName;
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

    private void syncColumns(InfraOdsTableMapping mapping, TableRef targetRef, List<ColumnSpec> specs, UUID connectionId, Instant snapshotTime) {
        if (mapping == null || targetRef == null || specs == null || specs.isEmpty()) {
            return;
        }
        CatalogDataset dataset = ensureDataset(connectionId, targetRef, snapshotTime);
        if (dataset == null) {
            return;
        }
        CatalogTableSchema table = ensureTable(dataset, targetRef.table());
        if (table == null) {
            return;
        }
        columnSyncService.upsertColumns(table, specs, CatalogColumnSyncService.STATUS_DRAFT);
    }

    private CatalogDataset ensureDataset(UUID connectionId, TableRef targetRef, Instant snapshotTime) {
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
        dataset.setSnapshotTime(snapshotTime);
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

    private record ApiLandingTarget(TableRef tableRef, String resourceName, Map<String, Object> payload, List<ColumnSpec> columnSpecs) {}

    private record ApiExecutionEvidence(
        UUID connectionId,
        String taskId,
        Instant taskRevision,
        long executionSequence,
        String executionId,
        Instant completedAt
    ) {}

    private record OdsNameParts(String system, String biz, String entity) {}
}
