package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDesignUpdateRequest;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Task-owned facade for the integrated visual flow.
 *
 * <p>The existing revisioned task remains the only write model. The topology is
 * generated from that contract and is deliberately read-only.</p>
 */
@Service
@Transactional
public class IngestionTaskDesignService {

    private static final String DATASET_PREFIX = "dataset:";
    private static final int MAX_TABLE_MAPPINGS = 1_000;
    private static final int MAX_NORMALIZED_DESIGN_BYTES = 1_048_576;

    private final IngestionTaskQueryService taskQueryService;
    private final IngestionTaskService taskService;
    private final IngestionTaskRepository taskRepository;
    private final AirflowDagService airflowDagService;
    private final ObjectMapper objectMapper;

    public IngestionTaskDesignService(
        IngestionTaskQueryService taskQueryService,
        IngestionTaskService taskService,
        IngestionTaskRepository taskRepository,
        AirflowDagService airflowDagService,
        ObjectMapper objectMapper
    ) {
        this.taskQueryService = taskQueryService;
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.airflowDagService = airflowDagService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public IngestionTaskDesignDTO getDesign(Long taskId) {
        IngestionTaskDTO task = taskQueryService.findOne(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        return project(task);
    }

    public IngestionTaskDesignDTO saveDesign(
        Long taskId,
        String expectedPlanChecksum,
        IngestionTaskDesignUpdateRequest request
    ) {
        // Serialize compare-and-save on the task row. Otherwise two sessions
        // can both validate the same checksum before either draft is persisted.
        taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        IngestionTaskDTO current = taskQueryService.findOne(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        requireMatchingChecksum(current.getEffectiveConfigChecksum(), expectedPlanChecksum);

        IngestionTaskDesignDTO.ValidationResult validation = validateRequest(request);
        if (!validation.valid()) {
            throw new DesignValidationException(validation);
        }

        IngestionTaskDTO patch = new IngestionTaskDTO();
        patch.setName(request.taskName().trim());
        patch.setDescription(trimToNull(request.description()));
        patch.setSourceDataSourceId(request.sourceDataSourceId());
        patch.setSourceType(request.sourceType().trim());
        patch.setSourceConfig(copy(request.sourceConfig()));
        patch.setDestinationType(request.destinationType().trim());
        patch.setDestinationConfig(copy(request.destinationConfig()));
        patch.setTargetDatasetId(request.targetDatasetId());
        patch.setSyncMode(request.syncMode().trim());
        patch.setSyncSchedule(trimToNull(request.syncSchedule()));
        patch.setTableMapping(copy(request.tableMapping()));
        patch.setSyncConfig(copy(request.syncConfig()));
        patch.setQualityPolicyRef(
            request.postIngestionQualityEnabled() ? trimToNull(request.qualityPolicyRef()) : null
        );

        taskService.updateDesign(taskId, patch, request.postIngestionQualityEnabled());
        return getDesign(taskId);
    }

    @Transactional(readOnly = true)
    public IngestionTaskDesignDTO.ValidationResult validateDesign(
        Long taskId,
        String expectedPlanChecksum,
        IngestionTaskDesignUpdateRequest request
    ) {
        IngestionTaskDTO current = taskQueryService.findOne(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (StringUtils.hasText(expectedPlanChecksum)) {
            requireMatchingChecksum(current.getEffectiveConfigChecksum(), expectedPlanChecksum);
        }
        return validateRequest(request);
    }

    @Transactional(readOnly = true)
    public IngestionTaskDesignDTO.TopologyProjection getTopology(Long taskId) {
        return getDesign(taskId).topology();
    }

    @Transactional(readOnly = true)
    public IngestionTaskDesignDTO.TopologyProjection getTopology(Long taskId, String view) {
        String normalized = StringUtils.hasText(view) ? view.trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if ("DRAFT".equals(normalized)) {
            return getTopology(taskId);
        }
        if (!"ACTIVE".equals(normalized)) {
            throw new IllegalArgumentException("TOPOLOGY_VIEW_INVALID");
        }
        IngestionTaskDTO task = taskQueryService.findOneActive(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        return project(task).topology();
    }

    @Transactional(readOnly = true)
    public void requirePlanChecksum(Long taskId, String expectedPlanChecksum) {
        IngestionTaskDTO current = taskQueryService.findOne(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        requireMatchingChecksum(current.getEffectiveConfigChecksum(), expectedPlanChecksum);
    }

    public IngestionTaskDesignDTO.ScheduleCommand setSchedulePaused(Long taskId, boolean paused) {
        IngestionTask task = taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        String currentState = normalize(task.getStatus());
        if (!"active".equals(currentState) && !"paused".equals(currentState)) {
            throw new IllegalStateException("TASK_SCHEDULE_STATE_INVALID");
        }
        if (!StringUtils.hasText(task.getAirflowDagId())) {
            throw new IllegalStateException("TASK_SCHEDULE_DAG_MISSING");
        }
        if ((paused && "paused".equals(currentState)) || (!paused && "active".equals(currentState))) {
            return new IngestionTaskDesignDTO.ScheduleCommand(
                taskId,
                paused ? "PAUSED" : "ENABLED",
                task.getAirflowDagId()
            );
        }
        airflowDagService.setDagPausedStrict(task.getAirflowDagId(), paused);
        task.setStatus(paused ? "paused" : "active");
        taskRepository.save(task);
        return new IngestionTaskDesignDTO.ScheduleCommand(
            taskId,
            paused ? "PAUSED" : "ENABLED",
            task.getAirflowDagId()
        );
    }

    private IngestionTaskDesignDTO project(IngestionTaskDTO task) {
        String policyRef = trimToNull(task.getQualityPolicyRef());
        UUID qualityDatasetId = parseDatasetId(policyRef);
        UUID datasetId = task.getTargetDatasetId() == null ? qualityDatasetId : task.getTargetDatasetId();
        boolean qualityEnabled = qualityDatasetId != null;
        IngestionTaskDesignDTO.ValidationResult validation = validateTask(task, qualityEnabled, policyRef);
        IngestionTaskDesignDTO.TopologyProjection topology = topology(task, datasetId != null, qualityEnabled);
        boolean legacyPresent = task.getGraphDsl() != null
            && !task.getGraphDsl().isNull()
            && !task.getGraphDsl().isEmpty();

        return new IngestionTaskDesignDTO(
            task.getId(),
            task.getName(),
            task.getDescription(),
            task.getRevisionNumber(),
            task.getRevisionState(),
            task.getStatus(),
            new IngestionTaskDesignDTO.SourceDesign(
                task.getSourceDataSourceId(),
                task.getSourceType(),
                copy(task.getSourceConfig())
            ),
            new IngestionTaskDesignDTO.DestinationDesign(
                task.getDestinationType(),
                copy(task.getDestinationConfig()),
                new IngestionTaskDesignDTO.DestinationAssetRef(
                    datasetId,
                    policyRef,
                    datasetId == null ? "UNRESOLVED" : "POLICY_REF"
                )
            ),
            task.getSyncMode(),
            task.getSyncSchedule(),
            copy(task.getTableMapping()),
            copy(task.getSyncConfig()),
            new IngestionTaskDesignDTO.PostIngestionQuality(qualityEnabled, policyRef),
            task.getEffectiveConfigChecksum(),
            validation,
            topology,
            new IngestionTaskDesignDTO.LegacyDsl(
                legacyPresent,
                false,
                legacyPresent ? "历史画布仅供兼容查看，不参与发布或执行" : null
            )
        );
    }

    private IngestionTaskDesignDTO.ValidationResult validateTask(
        IngestionTaskDTO task,
        boolean qualityEnabled,
        String policyRef
    ) {
        UUID targetDatasetId = task.getTargetDatasetId() == null
            ? parseDatasetId(policyRef)
            : task.getTargetDatasetId();
        return validateValues(
            task.getName(),
            task.getSourceDataSourceId(),
            task.getSourceType(),
            task.getSourceConfig(),
            task.getDestinationType(),
            task.getDestinationConfig(),
            targetDatasetId,
            task.getSyncMode(),
            task.getSyncSchedule(),
            task.getTableMapping(),
            task.getSyncConfig(),
            qualityEnabled,
            policyRef,
            task.getEffectiveConfigChecksum()
        );
    }

    private IngestionTaskDesignDTO.ValidationResult validateRequest(IngestionTaskDesignUpdateRequest request) {
        if (request == null) {
            return invalid("REQUEST_REQUIRED", "request", "任务设计不能为空");
        }
        IngestionTaskDesignDTO.ValidationResult fieldValidation = validateValues(
            request.taskName(),
            request.sourceDataSourceId(),
            request.sourceType(),
            request.sourceConfig(),
            request.destinationType(),
            request.destinationConfig(),
            request.targetDatasetId(),
            request.syncMode(),
            request.syncSchedule(),
            request.tableMapping(),
            request.syncConfig(),
            request.postIngestionQualityEnabled(),
            request.qualityPolicyRef(),
            "request"
        );
        List<IngestionTaskDesignDTO.ValidationIssue> issues = new ArrayList<>(fieldValidation.issues());
        try {
            if (objectMapper.writeValueAsBytes(request).length > MAX_NORMALIZED_DESIGN_BYTES) {
                issues.add(issue("DESIGN_PAYLOAD_TOO_LARGE", "request", "任务设计大小不能超过 1 MiB"));
            }
        } catch (JsonProcessingException ex) {
            issues.add(issue("DESIGN_PAYLOAD_INVALID", "request", "任务设计无法规范化"));
        }
        return new IngestionTaskDesignDTO.ValidationResult(issues.isEmpty(), List.copyOf(issues));
    }

    private IngestionTaskDesignDTO.ValidationResult validateValues(
        String name,
        UUID sourceDataSourceId,
        String sourceType,
        JsonNode sourceConfig,
        String destinationType,
        JsonNode destinationConfig,
        UUID targetDatasetId,
        String syncMode,
        String syncSchedule,
        JsonNode tableMapping,
        JsonNode syncConfig,
        boolean qualityEnabled,
        String policyRef,
        String checksum
    ) {
        List<IngestionTaskDesignDTO.ValidationIssue> issues = new ArrayList<>();
        requireText(issues, name, "TASK_NAME_REQUIRED", "taskName", "任务名称不能为空");
        requireText(issues, sourceType, "SOURCE_TYPE_REQUIRED", "source.type", "来源类型不能为空");
        if (!isFileSource(sourceType) && sourceDataSourceId == null) {
            issues.add(issue("SOURCE_REQUIRED", "source.dataSourceId", "请选择来源数据源"));
        }
        if (sourceConfig == null || !sourceConfig.isObject()) {
            issues.add(issue("SOURCE_CONFIG_REQUIRED", "source.config", "来源配置必须是 JSON 对象"));
        }
        requireText(issues, destinationType, "DESTINATION_TYPE_REQUIRED", "destination.type", "目标类型不能为空");
        if (destinationConfig == null || !destinationConfig.isObject()) {
            issues.add(issue("DESTINATION_CONFIG_REQUIRED", "destination.config", "目标配置不能为空"));
        }
        if (targetDatasetId == null) {
            issues.add(issue("TARGET_ASSET_UNRESOLVED", "targetDatasetId", "请选择唯一的目标数据资产"));
        }
        requireText(issues, syncMode, "SYNC_MODE_REQUIRED", "syncMode", "同步模式不能为空");
        String normalizedSyncMode = normalize(syncMode);
        if (StringUtils.hasText(syncMode) && !List.of("full_refresh", "incremental", "cdc").contains(normalizedSyncMode)) {
            issues.add(issue("SYNC_MODE_INVALID", "syncMode", "同步模式不受支持"));
        }
        if (syncConfig != null && !syncConfig.isNull() && !syncConfig.isObject()) {
            issues.add(issue("SYNC_CONFIG_INVALID", "syncConfig", "同步参数必须是 JSON 对象"));
        }
        if (
            "incremental".equals(normalizedSyncMode)
                && (syncConfig == null || !syncConfig.isObject() || missingText(syncConfig.get("incrementalColumn")))
        ) {
            issues.add(issue("INCREMENTAL_COLUMN_REQUIRED", "syncConfig.incrementalColumn", "增量同步必须配置增量列"));
        }
        if (tableMapping == null || !tableMapping.isArray() || tableMapping.isEmpty()) {
            issues.add(issue("TABLE_MAPPING_REQUIRED", "tableMapping", "至少配置一条表映射"));
        } else if (tableMapping.size() > MAX_TABLE_MAPPINGS) {
            issues.add(issue("TABLE_MAPPING_LIMIT_EXCEEDED", "tableMapping", "单个任务最多配置 1000 条表映射"));
        } else {
            for (int index = 0; index < tableMapping.size(); index++) {
                JsonNode mapping = tableMapping.get(index);
                String field = "tableMapping[" + index + "]";
                if (mapping == null || !mapping.isObject()) {
                    issues.add(issue("TABLE_MAPPING_ENTRY_INVALID", field, "表映射必须是 JSON 对象"));
                    continue;
                }
                if (missingText(mapping.get("source"))) {
                    issues.add(issue("TABLE_MAPPING_SOURCE_REQUIRED", field + ".source", "来源表不能为空"));
                }
                if (missingText(mapping.get("target"))) {
                    issues.add(issue("TABLE_MAPPING_TARGET_REQUIRED", field + ".target", "目标表不能为空"));
                }
            }
        }
        if (StringUtils.hasText(syncSchedule)) {
            try {
                CronExpression.parse(syncSchedule.trim());
            } catch (IllegalArgumentException ex) {
                issues.add(issue("SCHEDULE_INVALID", "syncSchedule", "调度表达式格式不正确"));
            }
        }
        UUID qualityDatasetId = parseDatasetId(policyRef);
        if (qualityEnabled && qualityDatasetId == null) {
            issues.add(issue("QUALITY_DATASET_REQUIRED", "qualityPolicyRef", "启用接入后质量校验时必须绑定数据集资产"));
        } else if (qualityEnabled && !Objects.equals(targetDatasetId, qualityDatasetId)) {
            issues.add(issue("QUALITY_ASSET_MISMATCH", "qualityPolicyRef", "质量校验必须绑定当前目标数据资产"));
        }
        if (!StringUtils.hasText(checksum)) {
            issues.add(issue("PLAN_CHECKSUM_MISSING", "planChecksum", "任务尚未形成可发布的版本校验值"));
        }
        return new IngestionTaskDesignDTO.ValidationResult(issues.isEmpty(), List.copyOf(issues));
    }

    private IngestionTaskDesignDTO.TopologyProjection topology(
        IngestionTaskDTO task,
        boolean assetResolved,
        boolean qualityEnabled
    ) {
        List<IngestionTaskDesignDTO.TopologyNode> nodes = new ArrayList<>();
        nodes.add(node("source", "SOURCE", "读取来源", "CONFIGURED"));
        nodes.add(node("load", "LOAD", "写入目标", "CONFIGURED"));
        nodes.add(node("asset", "ASSET_OBSERVATION", "关联数据资产", assetResolved ? "BOUND" : "UNRESOLVED"));
        if (qualityEnabled) {
            nodes.add(node("quality", "POST_INGESTION_QUALITY", "接入后质量校验", "ENABLED"));
        }
        nodes.add(node("end", "END", "完成", "READY"));

        List<IngestionTaskDesignDTO.TopologyEdge> edges = new ArrayList<>();
        edges.add(edge("source", "load"));
        edges.add(edge("load", "asset"));
        if (qualityEnabled) {
            edges.add(edge("asset", "quality"));
            edges.add(edge("quality", "end"));
        } else {
            edges.add(edge("asset", "end"));
        }
        return new IngestionTaskDesignDTO.TopologyProjection(
            true,
            task.getEffectiveConfigChecksum(),
            List.copyOf(nodes),
            List.copyOf(edges)
        );
    }

    private void requireMatchingChecksum(String actual, String expected) {
        String normalizedExpected = normalizeEtag(expected);
        if (!StringUtils.hasText(normalizedExpected) || !Objects.equals(actual, normalizedExpected)) {
            throw new DesignConflictException("TASK_DESIGN_CONFLICT: 任务设计已变化，请刷新后重试");
        }
    }

    private String normalizeEtag(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private UUID parseDatasetId(String policyRef) {
        if (!StringUtils.hasText(policyRef) || !policyRef.trim().startsWith(DATASET_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(policyRef.trim().substring(DATASET_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private JsonNode copy(JsonNode value) {
        return value == null ? objectMapper.nullNode() : value.deepCopy();
    }

    private boolean missingText(JsonNode value) {
        return value == null || !value.isTextual() || !StringUtils.hasText(value.asText());
    }

    private boolean isFileSource(String sourceType) {
        String value = normalize(sourceType);
        return value.contains("file") || value.contains("excel") || value.contains("csv");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(
        List<IngestionTaskDesignDTO.ValidationIssue> issues,
        String value,
        String code,
        String field,
        String message
    ) {
        if (!StringUtils.hasText(value)) {
            issues.add(issue(code, field, message));
        }
    }

    private IngestionTaskDesignDTO.ValidationResult invalid(String code, String field, String message) {
        return new IngestionTaskDesignDTO.ValidationResult(false, List.of(issue(code, field, message)));
    }

    private IngestionTaskDesignDTO.ValidationIssue issue(String code, String field, String message) {
        return new IngestionTaskDesignDTO.ValidationIssue(code, field, message);
    }

    private IngestionTaskDesignDTO.TopologyNode node(String id, String kind, String label, String state) {
        return new IngestionTaskDesignDTO.TopologyNode(id, kind, label, state);
    }

    private IngestionTaskDesignDTO.TopologyEdge edge(String source, String target) {
        return new IngestionTaskDesignDTO.TopologyEdge(source, target);
    }

    public static class DesignConflictException extends IllegalStateException {
        public DesignConflictException(String message) {
            super(message);
        }
    }

    public static class DesignValidationException extends IllegalArgumentException {
        private final IngestionTaskDesignDTO.ValidationResult validation;

        public DesignValidationException(IngestionTaskDesignDTO.ValidationResult validation) {
            super("TASK_DESIGN_INVALID");
            this.validation = validation;
        }

        public IngestionTaskDesignDTO.ValidationResult getValidation() {
            return validation;
        }
    }
}
