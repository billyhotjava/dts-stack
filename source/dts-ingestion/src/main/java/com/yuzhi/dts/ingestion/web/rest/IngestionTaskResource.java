package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final AddaxJobService addaxJobService;
    private final AddaxProperties addaxProperties;
    private final IngestionSettingsService settingsService;
    private final AuditService auditService;
    private final OpenMetadataAdapter openMetadataAdapter;
    private final AirflowAdapter airflowAdapter;
    private final com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService;

    public IngestionTaskResource(
        AddaxJobService addaxJobService,
        AddaxProperties addaxProperties,
        IngestionSettingsService settingsService,
        AuditService auditService,
        OpenMetadataAdapter openMetadataAdapter,
        AirflowAdapter airflowAdapter,
        com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService
    ) {
        this.addaxJobService = addaxJobService;
        this.addaxProperties = addaxProperties;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.openMetadataAdapter = openMetadataAdapter;
        this.airflowAdapter = airflowAdapter;
        this.ingestionTaskService = ingestionTaskService;
    }

    public record IngestionTaskRequest(
        String name,
        String owner,
        String description,
        SourceSpec source,
        DestinationSpec destination,
        SyncSpec sync,
        StreamsSpec streams,
        SchemaChangeSpec schemaChanges,
        LineageSpec lineage,
        AirflowSpec airflow,
        Boolean runNow,
        Map<String, Object> jobConfig
    ) {}

    public record SourceSpec(
        String type,
        String definitionId,
        String existingSourceId,
        Map<String, Object> config,
        String driverVersion
    ) {}

    public record DestinationSpec(
        Boolean usePlatformDefault,
        String type,
        String definitionId,
        String existingDestinationId,
        Map<String, Object> config
    ) {}

    public record SyncSpec(String mode, String destinationMode, ScheduleSpec schedule, NamespaceSpec namespace, String prefix) {}

    public record ScheduleSpec(String type, String cron, Integer intervalMinutes) {}

    public record NamespaceSpec(String definition, String format) {}

    public record StreamsSpec(String selection, List<String> include, List<String> exclude) {}

    public record SchemaChangeSpec(String mode) {}

    public record LineageSpec(Boolean enabled, String domain, List<String> tags, String owner) {}

    public record AirflowSpec(Boolean enabled, String dagId, String scheduleType, String cron, Integer intervalMinutes) {}

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@Valid @RequestBody IngestionTaskRequest request) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String taskName = request != null ? request.name() : null;
        try {
            if (request == null || !StringUtils.hasText(request.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
            }
            if (request.source() == null || request.destination() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少源端或目标端配置");
            }

            String readerType = resolvePlugin(request.source().type(), request.source().config(), List.of("readerType", "reader", "type"));
            String writerType = resolvePlugin(request.destination().type(), request.destination().config(), List.of("writerType", "writer", "type"));
            if (!StringUtils.hasText(readerType) || !StringUtils.hasText(writerType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Addax Reader/Writer 类型");
            }

            AddaxJobService.AddaxJobResult job = addaxJobService.createJob(
                request.name(),
                readerType,
                request.source().config(),
                writerType,
                request.destination().config(),
                request.jobConfig()
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(
                "job",
                Map.of(
                    "name",
                    job.jobName(),
                    "path",
                    job.jobPath(),
                    "reader",
                    readerType,
                    "writer",
                    writerType
                )
            );

            String namespaceFormat = request.sync() == null || request.sync().namespace() == null ? null : normalize(request.sync().namespace().format());
            String prefix = request.sync() == null ? null : normalize(request.sync().prefix());
            Map<String, Object> lineageResult = openMetadataAdapter.registerLineage(
                toLineageRequest(request.lineage()),
                new OpenMetadataAdapter.LineageContext(
                    request.name(),
                    readerType,
                    namespaceFormat,
                    prefix,
                    safeMap(request.source().config()),
                    safeMap(request.destination().config()),
                    List.of()
                )
            );
            if (lineageResult != null && !lineageResult.isEmpty()) {
                result.put("lineage", lineageResult);
            }

            Map<String, Object> ingestionResult = openMetadataAdapter.ensureMetadataIngestion(
                new OpenMetadataAdapter.IngestionContext(
                    request.name(),
                    safeMap(request.destination().config()),
                    null,
                    Boolean.TRUE.equals(request.runNow())
                )
            );
            if (ingestionResult != null && !ingestionResult.isEmpty()) {
                result.put("openmetadataIngestion", ingestionResult);
            }

            Map<String, Object> airflowConf = new LinkedHashMap<>();
            airflowConf.put("job_path", job.jobPath());
            airflowConf.put("job_name", job.jobName());
            airflowConf.put("taskName", request.name());
            AirflowAdapter.AirflowRequest airflowRequest = buildAirflowRequest(request.airflow());
            Map<String, Object> airflowResult = airflowAdapter.triggerIfRequested(
                airflowRequest,
                airflowConf,
                Boolean.TRUE.equals(request.runNow())
            );
            if (airflowResult != null && !airflowResult.isEmpty()) {
                result.put("airflow", airflowResult);
            }

            auditService.auditAction(
                "INGESTION_TASK_CREATE",
                AuditStage.SUCCESS,
                job.jobName(),
                Map.of("summary", "创建入湖任务", "name", request.name(), "operator", operator)
            );
            return ApiResponses.ok(result);
        } catch (RuntimeException ex) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("summary", "创建入湖任务失败");
            meta.put("name", taskName);
            meta.put("operator", operator);
            String error = trimMessage(ex.getMessage());
            if (StringUtils.hasText(error)) {
                meta.put("error", error);
            }
            auditService.auditAction("INGESTION_TASK_CREATE", AuditStage.FAIL, taskName, meta);
            throw ex;
        }
    }

    private String resolvePlugin(String direct, Map<String, Object> config, List<String> keys) {
        String value = normalize(direct);
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (config == null || config.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object candidate = config.get(key);
            String text = normalize(candidate);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private AirflowAdapter.AirflowRequest buildAirflowRequest(AirflowSpec spec) {
        boolean enabled = spec != null && Boolean.TRUE.equals(spec.enabled());
        String dagId = spec == null ? null : normalize(spec.dagId());
        if (!StringUtils.hasText(dagId)) {
            IngestionSettingsService.SettingsSnapshot settings = settingsService.getSettings(IngestionSettingsService.SERVICE_ADDAX);
            dagId = settings.getString("dagId", addaxProperties.getDagId());
        }
        return new AirflowAdapter.AirflowRequest(
            enabled,
            dagId,
            spec == null ? null : normalize(spec.scheduleType()),
            spec == null ? null : normalize(spec.cron()),
            spec == null ? null : spec.intervalMinutes()
        );
    }

    private OpenMetadataAdapter.LineageRequest toLineageRequest(LineageSpec spec) {
        if (spec == null) {
            return null;
        }
        return new OpenMetadataAdapter.LineageRequest(spec.enabled(), spec.domain(), spec.tags(), spec.owner());
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    // ==================== 新增CRUD端点 ====================

    private final com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService;

    /**
     * GET /api/ingestion/tasks : 获取任务列表
     */
    @GetMapping("/tasks/list")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO>> getTasks(
        @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
        org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> page = 
            ingestionTaskService.findAll(status, pageable);
        return org.springframework.http.ResponseEntity.ok(page);
    }

    /**
     * GET /api/ingestion/tasks/{id} : 获取任务详情
     */
    @org.springframework.web.bind.annotation.GetMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> getTask(
        @org.springframework.web.bind.annotation.PathVariable Long id
    ) {
        return ingestionTaskService.findOne(id)
            .map(org.springframework.http.ResponseEntity::ok)
            .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/ingestion/tasks/{id} : 更新任务
     */
    @org.springframework.web.bind.annotation.PutMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> updateTask(
        @org.springframework.web.bind.annotation.PathVariable Long id,
        @Valid @RequestBody com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO taskDTO
    ) {
        if (!id.equals(taskDTO.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID不匹配");
        }
        com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO updated = ingestionTaskService.update(id, taskDTO);
        return org.springframework.http.ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/ingestion/tasks/{id} : 删除任务（软删除）
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<Void> deleteTask(
        @org.springframework.web.bind.annotation.PathVariable Long id
    ) {
        ingestionTaskService.delete(id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    /**
     * POST /api/ingestion/tasks/{id}/execute : 手动执行任务
     */
    @PostMapping("/tasks/{id}/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> executeTask(
        @org.springframework.web.bind.annotation.PathVariable Long id
    ) {
        try {
            com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO execution = ingestionTaskService.execute(id);
            return org.springframework.http.ResponseEntity.ok(execution);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/executions : 获取任务执行历史
     */
    @org.springframework.web.bind.annotation.GetMapping("/tasks/{id}/executions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO>> getExecutions(
        @org.springframework.web.bind.annotation.PathVariable Long id,
        org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> page = 
            ingestionTaskService.getExecutions(id, pageable);
        return org.springframework.http.ResponseEntity.ok(page);
    }

    /**
     * GET /api/ingestion/tasks/{id}/executions/latest : 获取最新执行记录
     */
    @org.springframework.web.bind.annotation.GetMapping("/tasks/{id}/executions/latest")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> getLatestExecution(
        @org.springframework.web.bind.annotation.PathVariable Long id
    ) {
        return ingestionTaskService.getLatestExecution(id)
            .map(org.springframework.http.ResponseEntity::ok)
            .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }
}
