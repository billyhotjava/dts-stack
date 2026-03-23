package com.yuzhi.dts.ingestion.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.IngestionExecutionQueryService;
import com.yuzhi.dts.ingestion.service.IngestionTaskQueryService;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.ConnectorCapabilityService;
import com.yuzhi.dts.ingestion.service.etl.RealtimeTaskStatusService;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import com.yuzhi.dts.ingestion.service.IngestionTaskChangeLogService;
import com.yuzhi.dts.ingestion.service.dto.IngestionConnectorCapabilityDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionObservabilityDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionGovernanceOverviewDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionRealtimeStatusDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskChangeLogDTO;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final List<String> CONNECTION_KEYS = List.of(
        "jdbcUrl",
        "url",
        "host",
        "port",
        "username",
        "password",
        "database",
        "db",
        "driver",
        "driverClass",
        "driverVersion",
        "jdbcProperties"
    );

    private final AddaxJobService addaxJobService;
    private final AuditService auditService;
    private final OpenMetadataAdapter openMetadataAdapter;
    private final AirflowAdapter airflowAdapter;
    private final com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService;
    private final IngestionTaskQueryService ingestionTaskQueryService;
    private final IngestionExecutionQueryService ingestionExecutionQueryService;
    private final JdbcMetadataService jdbcMetadataService;
    private final com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;
    private final IngestionTaskChangeLogService changeLogService;
    private final ConnectorCapabilityService connectorCapabilityService;
    private final RealtimeTaskStatusService realtimeTaskStatusService;
    private final ObjectMapper objectMapper;
    private final AirflowProperties airflowProperties;

    public IngestionTaskResource(
        AddaxJobService addaxJobService,
        AuditService auditService,
        OpenMetadataAdapter openMetadataAdapter,
        AirflowAdapter airflowAdapter,
        com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService,
        IngestionTaskQueryService ingestionTaskQueryService,
        IngestionExecutionQueryService ingestionExecutionQueryService,
        JdbcMetadataService jdbcMetadataService,
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver,
        IngestionTaskChangeLogService changeLogService,
        ConnectorCapabilityService connectorCapabilityService,
        RealtimeTaskStatusService realtimeTaskStatusService,
        ObjectMapper objectMapper,
        AirflowProperties airflowProperties
    ) {
        this.addaxJobService = addaxJobService;
        this.auditService = auditService;
        this.openMetadataAdapter = openMetadataAdapter;
        this.airflowAdapter = airflowAdapter;
        this.ingestionTaskService = ingestionTaskService;
        this.ingestionTaskQueryService = ingestionTaskQueryService;
        this.ingestionExecutionQueryService = ingestionExecutionQueryService;
        this.jdbcMetadataService = jdbcMetadataService;
        this.sourceResolver = sourceResolver;
        this.changeLogService = changeLogService;
        this.connectorCapabilityService = connectorCapabilityService;
        this.realtimeTaskStatusService = realtimeTaskStatusService;
        this.objectMapper = objectMapper;
        this.airflowProperties = airflowProperties;
    }

    public record IngestionTaskRequest(
        @JsonAlias({"taskName", "title"}) String name,
        String owner,
        String description,
        SourceSpec source,
        DestinationSpec destination,
        SyncSpec sync,
        StreamsSpec streams,
        SchemaChangeSpec schemaChanges,
        LineageSpec lineage,
        AirflowSpec airflow,
        DbtSpec dbt,
        Boolean runNow,
        Map<String, Object> jobConfig,
        Boolean draft
    ) {}

    public record SourceSpec(
        java.util.UUID dataSourceId,
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

    public record SyncSpec(
        String mode,
        String destinationMode,
        ScheduleSpec schedule,
        NamespaceSpec namespace,
        String prefix,
        String incrementalColumn,
        String incrementalType,
        String initialWatermark,
        Integer taskConcurrency,
        Integer sourceConcurrency,
        Integer projectConcurrency,
        String projectKey,
        String priority,
        String rejectPolicy,
        String windowStart,
        String windowEnd,
        String windowTimezone
    ) {}

    public record ScheduleSpec(String type, String cron, Integer intervalMinutes) {}

    public record NamespaceSpec(String definition, String format) {}

    public record StreamsSpec(
        String selection,
        List<String> include,
        List<String> exclude,
        String schema,
        String tablePattern,
        Integer limit
    ) {}

    public record SchemaChangeSpec(String mode) {}

    public record LineageSpec(Boolean enabled, String domain, List<String> tags, String owner) {}

    public record AirflowSpec(Boolean enabled, String dagId, String scheduleType, String cron, Integer intervalMinutes) {}

    public record DbtSpec(String modelSelector, String dagSelector) {}

    public record TableDiscoveryRequest(SourceSpec source, TableDiscoveryFilter filter) {}

    public record TableDiscoveryFilter(String schema, String tablePattern, Integer limit, Boolean includeColumns) {}

    public record TableInfo(String schema, String name, String type, List<ColumnInfo> columns) {}

    public record ColumnInfo(String name, Integer jdbcType, String typeName, Integer columnSize, Integer decimalDigits) {}

    public record ChangeLogRequest(
        Long taskId,
        String taskName,
        String changeType,
        String summary,
        String detail,
        String riskLevel,
        String status,
        String assignee,
        String approvalComment
    ) {}

    public record ChangeLogTransitionRequest(
        String action,
        String assignee,
        String approvalComment
    ) {}

    public record IngestionTaskTemplateDTO(
        String id,
        String name,
        String description,
        String sourceCategory,
        String connectorType,
        Map<String, Object> defaults,
        List<String> requiredParams,
        List<String> warnings,
        String version
    ) {}

    public record IngestionTemplateRenderRequest(
        Map<String, Object> params,
        Boolean strictRequired
    ) {}

    public record IngestionTemplateRenderDTO(
        String id,
        String name,
        String version,
        Map<String, Object> renderedDefaults,
        List<String> requiredParams,
        List<String> warnings,
        List<String> errors,
        Boolean canApply
    ) {}

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@Valid @RequestBody IngestionTaskRequest request) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String taskName = request != null ? request.name() : null;
        try {
            if (request == null || !StringUtils.hasText(request.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
            }
            boolean isDraft = Boolean.TRUE.equals(request.draft());
            boolean runNow = Boolean.TRUE.equals(request.runNow());
            if (request.source() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少源端配置");
            }
            if (!isDraft && request.destination() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少目标端配置");
            }

            String syncMode = resolveSyncMode(request.sync());
            boolean isFileSource = isFileSourceType(normalize(request.source().type()));
            if (!isFileSource && request.source().dataSourceId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择数据源连接");
            }
            if (!isFileSource && hasConnectionOverride(request.source().config())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入湖任务必须使用已配置的数据源连接");
            }
            String connectorType = resolveConnectorType(
                isFileSource,
                request.source() == null ? null : request.source().type()
            );
            validateSyncModeCapability(connectorType, syncMode);
            List<String> streamTables = resolveStreamTables(request.streams());
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource =
                isFileSource ? null : sourceResolver.resolve(request.source().dataSourceId(), streamTables);
            String readerType = resolvePlugin(
                request.source().type(),
                safeMap(request.source().config()),
                List.of("readerType", "reader", "type", "name", "sourceType")
            );
            if (!StringUtils.hasText(readerType) && resolvedSource != null) {
                readerType = resolvePlugin(null, resolvedSource.readerConfig(), List.of("readerType", "reader", "type", "name"));
            }
            if (!StringUtils.hasText(readerType) && resolvedSource != null) {
                readerType = resolvedSource.readerType();
            }
            if (!StringUtils.hasText(readerType) && resolvedSource != null) {
                String jdbcUrl = normalize(resolvedSource.detail() == null ? null : resolvedSource.detail().jdbcUrl());
                if (StringUtils.hasText(jdbcUrl)) {
                    readerType = "rdbmsreader";
                }
            }
            if (!StringUtils.hasText(readerType) && isFileSource) {
                readerType = resolveFileReaderType(normalize(request.source().type()));
            }
            readerType = normalizeAddaxPlugin(readerType, true);
            if (!StringUtils.hasText(readerType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Addax Reader 类型");
            }
            Map<String, Object> sourceOverrides = isFileSource
                ? safeMap(request.source().config())
                : sanitizeSourceOverrides(request.source().config());
            Map<String, Object> resolvedReaderConfig = resolvedSource != null ? resolvedSource.readerConfig() : safeMap(request.source().config());
            Map<String, Object> mergedReaderConfig = mergeReaderOverrides(safeMap(resolvedReaderConfig), sourceOverrides);
            if (StringUtils.hasText(readerType)) {
                mergedReaderConfig.putIfAbsent("readerType", readerType);
                if (resolvedReaderConfig != null && resolvedReaderConfig instanceof java.util.LinkedHashMap) {
                    resolvedReaderConfig.putIfAbsent("readerType", readerType);
                }
            }
            Map<String, Object> writerConfig = safeMap(request.destination() == null ? null : request.destination().config());
            boolean useDefault = request.destination() != null && Boolean.TRUE.equals(request.destination().usePlatformDefault());
            String writerType = resolvePlugin(
                request.destination() == null ? null : (useDefault ? request.destination().definitionId() : request.destination().type()),
                writerConfig,
                List.of("writerType", "writer", "type")
            );
            String syncPrefix = request.sync() == null ? null : normalize(request.sync().prefix());
            applySyncPrefixToWriterConfig(writerConfig, syncPrefix);

            if (isDraft) {
                AirflowAdapter.AirflowRequest airflowRequest = buildAirflowRequest(request.airflow());
                com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO taskDTO =
                    new com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO();
                taskDTO.setName(request.name());
                taskDTO.setDescription(request.description());
                taskDTO.setSourceType(readerType);
                taskDTO.setSourceDataSourceId(request.source().dataSourceId());
                if (!sourceOverrides.isEmpty()) {
                    taskDTO.setSourceConfig(toJsonNode(sourceOverrides));
                }
                if (StringUtils.hasText(writerType)) {
                    taskDTO.setDestinationType(writerType);
                }
                if (!writerConfig.isEmpty()) {
                    if (StringUtils.hasText(writerType) && !StringUtils.hasText(normalize(writerConfig.get("writerType")))) {
                        writerConfig.put("writerType", writerType);
                    }
                    taskDTO.setDestinationConfig(toJsonNode(writerConfig));
                }
                taskDTO.setSyncMode(syncMode);
                taskDTO.setSyncSchedule(resolveSyncSchedule(request.sync()));
                taskDTO.setSyncPrefix(syncPrefix);
                taskDTO.setSyncConfig(toJsonNode(buildSyncConfig(request.sync())));
                taskDTO.setAddaxConfig(toJsonNode(request.jobConfig()));
                taskDTO.setAirflowEnabled(airflowRequest == null ? null : airflowRequest.enabled());
                taskDTO.setAirflowDagId(airflowRequest == null ? null : normalize(airflowRequest.dagId()));
                if (request.dbt() != null) {
                    taskDTO.setDbtModelSelector(normalize(request.dbt().modelSelector()));
                    taskDTO.setDbtDagSelector(normalize(request.dbt().dagSelector()));
                }

                com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO createdTask =
                    ingestionTaskService.create(taskDTO, resolvedSource, true);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("task", createdTask);
                auditService.auditAction(
                    "INGESTION_TASK_CREATE",
                    AuditStage.SUCCESS,
                    request.name(),
                    Map.of(
                        "summary",
                        "保存入湖草稿",
                        "name",
                        request.name(),
                        "taskId",
                        createdTask.getId(),
                        "operator",
                        operator
                    )
                );
                return ApiResponses.ok(result);
            }

            boolean hasJobConfig = request.jobConfig() != null && !request.jobConfig().isEmpty();
            String streamSelection = resolveStreamSelection(request.streams());
            List<String> configTables = stripTablePlaceholders(
                mergeTables(extractTables(mergedReaderConfig), extractTables(writerConfig))
            );
            if (streamTables.isEmpty() && !configTables.isEmpty()) {
                streamTables = configTables;
                streamSelection = "manual";
            }
            if (streamTables.isEmpty() && !isAllSelection(streamSelection)) {
                streamTables = configTables;
            }
            if (!isDraft && !isAllSelection(streamSelection) && streamTables.isEmpty() && !hasJobConfig) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择需要入湖的表");
            }
            if (isAllSelection(streamSelection) && streamTables.isEmpty()) {
                List<String> allTables = discoverAllTables(
                    request.source().dataSourceId(),
                    mergedReaderConfig,
                    request.streams()
                );
                streamTables = applyExcludes(allTables, request.streams());
                if (streamTables.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未发现可用表");
                }
            }
            if (!streamTables.isEmpty()) {
                applyTables(resolvedReaderConfig, streamTables);
                applyTables(mergedReaderConfig, streamTables);
                List<String> requestedWriterTables = stripTablePlaceholders(extractTables(writerConfig));
                String tablePrefix = syncPrefix;
                if (!StringUtils.hasText(tablePrefix)) {
                    tablePrefix = resolveTablePrefix(writerConfig);
                }
                if (requestedWriterTables.isEmpty() || isSourceAlignedTables(streamTables, requestedWriterTables)) {
                    List<String> writerTables = applyPrefixToTables(stripSchemaTables(streamTables), tablePrefix);
                    applyTables(writerConfig, writerTables);
                }
            }
            Map<String, Object> readerConfig = safeMap(resolvedReaderConfig);
            if (!hasJobConfig) {
                if (!StringUtils.hasText(writerType)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Addax Writer 类型");
                }
                if (writerConfig.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Addax Writer 配置");
                }
            }
            if (StringUtils.hasText(writerType) && !StringUtils.hasText(normalize(writerConfig.get("writerType")))) {
                writerConfig.put("writerType", writerType);
            }

            AirflowAdapter.AirflowRequest airflowRequest = buildAirflowRequest(request.airflow());
            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO taskDTO =
                new com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO();
            taskDTO.setName(request.name());
            taskDTO.setDescription(request.description());
            taskDTO.setSourceType(readerType);
            taskDTO.setSourceDataSourceId(request.source().dataSourceId());
            if (!sourceOverrides.isEmpty()) {
                taskDTO.setSourceConfig(toJsonNode(sourceOverrides));
            }
            taskDTO.setDestinationType(writerType);
            taskDTO.setDestinationConfig(toJsonNode(safeMap(writerConfig)));
            taskDTO.setSyncMode(syncMode);
            taskDTO.setSyncSchedule(resolveSyncSchedule(request.sync()));
            taskDTO.setSyncPrefix(syncPrefix);
            taskDTO.setSyncConfig(toJsonNode(buildSyncConfig(request.sync())));
            taskDTO.setAddaxConfig(toJsonNode(request.jobConfig()));
            taskDTO.setAirflowEnabled(airflowRequest == null ? null : airflowRequest.enabled());
            taskDTO.setAirflowDagId(airflowRequest == null ? null : normalize(airflowRequest.dagId()));
            if (request.dbt() != null) {
                taskDTO.setDbtModelSelector(normalize(request.dbt().modelSelector()));
                taskDTO.setDbtDagSelector(normalize(request.dbt().dagSelector()));
            }
            List<Map<String, String>> tableMapping = deriveTableMapping(mergedReaderConfig, writerConfig, request.sync());
            if (!tableMapping.isEmpty()) {
                taskDTO.setTableMapping(toJsonNode(tableMapping));
            }

            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO createdTask =
                ingestionTaskService.create(taskDTO, resolvedSource);
            String jobPath = createdTask.getAddaxJobPath();
            String jobName = null;
            if (StringUtils.hasText(jobPath)) {
                Path path = Paths.get(jobPath);
                jobName = path.getFileName().toString();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(
                "job",
                Map.of(
                    "name",
                    jobName,
                    "path",
                    jobPath,
                    "reader",
                    readerType,
                    "writer",
                    writerType
                )
            );
            result.put("task", createdTask);

            String namespaceFormat = request.sync() == null || request.sync().namespace() == null ? null : normalize(request.sync().namespace().format());
            String prefix = request.sync() == null ? null : normalize(request.sync().prefix());
            Map<String, Object> lineageResult = openMetadataAdapter.registerLineage(
                toLineageRequest(request.lineage()),
                new OpenMetadataAdapter.LineageContext(
                    request.name(),
                    readerType,
                    namespaceFormat,
                    prefix,
                    safeMap(readerConfig),
                    safeMap(writerConfig),
                    List.of()
                )
            );
            if (lineageResult != null && !lineageResult.isEmpty()) {
                result.put("lineage", lineageResult);
            }

            Map<String, Object> ingestionResult = openMetadataAdapter.ensureMetadataIngestion(
                new OpenMetadataAdapter.IngestionContext(
                    request.name(),
                    safeMap(writerConfig),
                    null,
                    runNow
                )
            );
            if (ingestionResult != null && !ingestionResult.isEmpty()) {
                result.put("openmetadataIngestion", ingestionResult);
            }

            if (runNow) {
                ingestionTaskService.executeAsync(createdTask.getId());
                Map<String, Object> submitted = new LinkedHashMap<>();
                submitted.put("taskId", createdTask.getId());
                submitted.put("status", "submitted");
                submitted.put("async", true);
                submitted.put("pollIntervalMs", resolveExecutionPollIntervalMs());
                submitted.put("message", "任务已提交，正在后台触发执行");
                result.put("execution", submitted);
            } else {
                String airflowJobPath = addaxJobService.toContainerJobPath(jobPath);
                Map<String, Object> airflowConf = new LinkedHashMap<>();
                airflowConf.put("job_path", airflowJobPath);
                airflowConf.put("job_name", jobName);
                airflowConf.put("taskName", request.name());
                airflowConf.put("taskId", createdTask.getId());
                AirflowAdapter.AirflowRequest triggerRequest = airflowRequest;
                if (airflowRequest != null
                    && Boolean.TRUE.equals(airflowRequest.enabled())
                    && !StringUtils.hasText(airflowRequest.dagId())
                    && StringUtils.hasText(createdTask.getAirflowDagId())) {
                    triggerRequest = new AirflowAdapter.AirflowRequest(
                        airflowRequest.enabled(),
                        createdTask.getAirflowDagId(),
                        airflowRequest.scheduleType(),
                        airflowRequest.cron(),
                        airflowRequest.intervalMinutes()
                    );
                }
                Map<String, Object> airflowResult = airflowAdapter.triggerIfRequested(
                    triggerRequest,
                    airflowConf,
                    false
                );
                if (airflowResult != null && !airflowResult.isEmpty()) {
                    result.put("airflow", airflowResult);
                }
            }

            auditService.auditAction(
                "INGESTION_TASK_CREATE",
                AuditStage.SUCCESS,
                jobName,
                Map.of(
                    "summary",
                    "创建入湖任务",
                    "name",
                    request.name(),
                    "taskId",
                    createdTask.getId(),
                    "operator",
                    operator
                )
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

    /**
     * POST /api/ingestion/metadata/tables : 获取源端表清单
     */
    @PostMapping("/metadata/tables")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<TableInfo>> discoverTables(@RequestBody TableDiscoveryRequest request) {
        if (request == null || request.source() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少源端配置");
        }
        if (request.source().dataSourceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择数据源连接");
        }
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource =
            sourceResolver.resolve(request.source().dataSourceId(), List.of());
        Map<String, Object> readerConfig = safeMap(resolvedSource.readerConfig());
        JdbcMetadataService.JdbcConnectionInfo info = sourceResolver.resolveJdbcInfo(request.source().dataSourceId());
        if (!StringUtils.hasText(info.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 JDBC URL");
        }
        TableDiscoveryFilter filter = request.filter();
        String schema = filter == null ? null : normalize(filter.schema());
        if (!StringUtils.hasText(schema)) {
            schema = resolveSchema(readerConfig);
        }
        String tablePattern = filter == null ? null : normalize(filter.tablePattern());
        Integer limit = filter == null ? null : filter.limit();
        boolean includeColumns = filter != null && Boolean.TRUE.equals(filter.includeColumns());

        List<JdbcMetadataService.TableMeta> tables = jdbcMetadataService.listTables(info, schema, tablePattern, limit);
        List<TableInfo> payload = new java.util.ArrayList<>();
        for (JdbcMetadataService.TableMeta table : tables) {
            List<ColumnInfo> columns = List.of();
            if (includeColumns && StringUtils.hasText(table.name())) {
                List<JdbcMetadataService.ColumnMeta> cols = jdbcMetadataService.getTableColumns(info, buildTableName(table));
                columns = cols.stream()
                    .map(col -> new ColumnInfo(col.name(), col.jdbcType(), col.typeName(), col.columnSize(), col.decimalDigits()))
                    .toList();
            }
            payload.add(new TableInfo(table.schema(), table.name(), table.type(), columns));
        }
        return ApiResponses.ok(payload);
    }

    private String resolvePlugin(String direct, Map<String, Object> config, List<String> keys) {
        String value = normalize(direct);
        if (StringUtils.hasText(value) && !"auto".equalsIgnoreCase(value)) {
            return value;
        }
        if (config == null || config.isEmpty() || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object candidate = config.get(key);
            String text = normalize(candidate);
            if (StringUtils.hasText(text) && !"auto".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private AirflowAdapter.AirflowRequest buildAirflowRequest(AirflowSpec spec) {
        boolean enabled = spec != null && Boolean.TRUE.equals(spec.enabled());
        return new AirflowAdapter.AirflowRequest(
            enabled,
            spec == null ? null : normalize(spec.dagId()),
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
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private boolean hasConnectionOverride(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return false;
        }
        if (containsConnectionKeys(config)) {
            return true;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return containsConnectionKeys(map);
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap && containsConnectionKeys(entryMap)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsConnectionKeys(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (String key : CONNECTION_KEYS) {
            if (map.containsKey(key) && StringUtils.hasText(normalize(map.get(key)))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> sanitizeSourceOverrides(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (isConnectionKey(key)) {
                continue;
            }
            if ("connection".equalsIgnoreCase(key)) {
                continue;
            }
            sanitized.put(key, entry.getValue());
        }
        List<String> tables = extractTables(config);
        if (!tables.isEmpty()) {
            sanitized.put("table", tables);
        }
        return sanitized;
    }

    private boolean isConnectionKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        for (String candidate : CONNECTION_KEYS) {
            if (candidate.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> mergeReaderOverrides(Map<String, Object> readerConfig, Map<String, Object> overrides) {
        Map<String, Object> merged = safeMap(readerConfig);
        if (overrides == null || overrides.isEmpty()) {
            return merged;
        }
        Map<String, Object> copy = new LinkedHashMap<>(overrides);
        List<String> tables = extractTables(copy);
        copy.remove("table");
        copy.remove("tables");
        copy.remove("connection");
        merged.putAll(copy);
        if (!tables.isEmpty()) {
            applyTables(merged, tables);
        }
        return merged;
    }

    private com.fasterxml.jackson.databind.JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }

    private Map<String, String> resolveJdbcProperties(Map<String, Object> config) {
        Object props = config == null ? null : config.get("jdbcProperties");
        if (!(props instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                resolved.put(k.toString(), v.toString());
            }
        });
        return resolved;
    }

    private String resolveJdbcUrl(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String direct = firstStringValue(config.get("jdbcUrl"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            return firstStringValue(map.get("jdbcUrl"));
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    String candidate = firstStringValue(entryMap.get("jdbcUrl"));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private String firstStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return normalize(str);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                String text = normalize(entry);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return normalize(value);
    }

    private String resolveSchema(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        String schema = normalize(config.get("schema"));
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        Object schemas = config.get("schemas");
        if (schemas instanceof List<?> list && !list.isEmpty()) {
            String candidate = normalize(list.get(0));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            String candidate = normalize(map.get("schema"));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveDriverClassFromUrl(String jdbcUrl) {
        String url = normalize(jdbcUrl);
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:dm:")) {
            return "dm.jdbc.driver.DmDriver";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (lower.startsWith("jdbc:clickhouse:")) {
            return "com.clickhouse.jdbc.ClickHouseDriver";
        }
        if (lower.startsWith("jdbc:hive2:")) {
            return "org.apache.hive.jdbc.HiveDriver";
        }
        if (lower.startsWith("jdbc:db2:")) {
            return "com.ibm.db2.jcc.DB2Driver";
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        return null;
    }

    private String buildTableName(JdbcMetadataService.TableMeta table) {
        if (table == null || !StringUtils.hasText(table.name())) {
            return null;
        }
        if (StringUtils.hasText(table.schema())) {
            return table.schema() + "." + table.name();
        }
        return table.name();
    }

    private Map<String, Object> jsonNodeToMap(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException ex) {
            return new LinkedHashMap<>();
        }
    }

    private List<String> resolveStreamTables(StreamsSpec streams) {
        if (streams == null || streams.include() == null) {
            return List.of();
        }
        return streams.include().stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .toList();
    }

    private String resolveStreamSelection(StreamsSpec streams) {
        if (streams == null) {
            return "all";
        }
        String selection = normalize(streams.selection());
        if (StringUtils.hasText(selection)) {
            return selection;
        }
        if (streams.include() != null && !streams.include().isEmpty()) {
            return "manual";
        }
        return "all";
    }

    private boolean isAllSelection(String selection) {
        return !StringUtils.hasText(selection) || "all".equalsIgnoreCase(selection) || "auto".equalsIgnoreCase(selection);
    }

    private List<String> discoverAllTables(
        java.util.UUID sourceId,
        Map<String, Object> readerConfig,
        StreamsSpec streams
    ) {
        JdbcMetadataService.JdbcConnectionInfo info = sourceResolver.resolveJdbcInfo(sourceId);
        if (!StringUtils.hasText(info.jdbcUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 JDBC URL");
        }
        String schema = streams == null ? null : normalize(streams.schema());
        if (!StringUtils.hasText(schema)) {
            schema = resolveSchema(readerConfig);
        }
        String tablePattern = streams == null ? null : normalize(streams.tablePattern());
        Integer limit = streams == null ? null : streams.limit();
        List<JdbcMetadataService.TableMeta> tables = jdbcMetadataService.listTables(info, schema, tablePattern, limit == null ? 0 : limit);
        return tables.stream()
            .map(this::buildTableName)
            .filter(StringUtils::hasText)
            .toList();
    }

    private List<String> applyExcludes(List<String> tables, StreamsSpec streams) {
        if (tables == null || tables.isEmpty() || streams == null || streams.exclude() == null || streams.exclude().isEmpty()) {
            return tables == null ? List.of() : tables;
        }
        java.util.Set<String> excludes = streams.exclude().stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .map(value -> value.toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (String table : tables) {
            String normalized = normalize(table);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            String lower = normalized.toLowerCase(java.util.Locale.ROOT);
            String simple = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : lower;
            if (excludes.contains(lower) || excludes.contains(simple)) {
                continue;
            }
            filtered.add(normalized);
        }
        return filtered;
    }

    private List<Map<String, String>> deriveTableMapping(
        Map<String, Object> readerConfig,
        Map<String, Object> writerConfig,
        SyncSpec sync
    ) {
        List<String> sources = extractTables(readerConfig);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<String> targets = extractTables(writerConfig);
        if (targets.isEmpty()) {
            String prefix = sync == null ? null : normalize(sync.prefix());
            if (!StringUtils.hasText(prefix)) {
                prefix = resolveTablePrefix(writerConfig);
            }
            final String prefixValue = prefix;
            List<String> computedTargets = new java.util.ArrayList<>(sources.size());
            for (String source : sources) {
                String base = stripSchema(source);
                computedTargets.add(StringUtils.hasText(prefixValue) ? prefixValue + base : base);
            }
            targets = computedTargets;
        } else if (isSourceAlignedTables(sources, targets)) {
            String prefix = sync == null ? null : normalize(sync.prefix());
            if (!StringUtils.hasText(prefix)) {
                prefix = resolveTablePrefix(writerConfig);
            }
            final String prefixValue = prefix;
            List<String> computedTargets = new java.util.ArrayList<>(sources.size());
            for (String source : sources) {
                String base = stripSchema(source);
                computedTargets.add(StringUtils.hasText(prefixValue) ? prefixValue + base : base);
            }
            targets = computedTargets;
        } else if (targets.size() == 1 && hasTablePlaceholder(targets.get(0))) {
            String template = targets.get(0);
            String prefix = sync == null ? null : normalize(sync.prefix());
            if (!StringUtils.hasText(prefix)) {
                prefix = resolveTablePrefix(writerConfig);
            }
            final String prefixValue = prefix;
            List<String> computedTargets = new java.util.ArrayList<>(sources.size());
            for (String source : sources) {
                String base = stripSchema(source);
                String tableName = StringUtils.hasText(prefixValue) ? prefixValue + base : base;
                computedTargets.add(replaceTablePlaceholder(template, tableName));
            }
            targets = computedTargets;
        }
        int size = Math.min(sources.size(), targets.size());
        List<Map<String, String>> mappings = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            String source = sources.get(i);
            String target = targets.get(i);
            if (!StringUtils.hasText(source) && !StringUtils.hasText(target)) {
                continue;
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            mapping.put("source", source);
            if (StringUtils.hasText(target)) {
                mapping.put("target", target);
            }
            mappings.add(mapping);
        }
        return mappings;
    }

    private void applyTables(Map<String, Object> config, List<String> tables) {
        if (config == null || tables == null || tables.isEmpty()) {
            return;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            setTableField(map, tables);
            return;
        }
        if (connection instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    setTableField(entryMap, tables);
                }
            }
            return;
        }
        config.put("table", tables);
    }

    private List<String> stripSchemaTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>(tables.size());
        for (String table : tables) {
            String stripped = stripSchema(table);
            if (StringUtils.hasText(stripped)) {
                cleaned.add(stripped);
            }
        }
        return cleaned.isEmpty() ? List.of() : cleaned;
    }

    private List<String> applyPrefixToTables(List<String> tables, String prefix) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        String normalizedPrefix = normalize(prefix);
        java.util.List<String> resolved = new java.util.ArrayList<>(tables.size());
        for (String table : tables) {
            String base = stripSchema(table);
            if (!StringUtils.hasText(base)) {
                continue;
            }
            resolved.add(StringUtils.hasText(normalizedPrefix) ? normalizedPrefix + base : base);
        }
        return resolved.isEmpty() ? List.of() : resolved;
    }

    private List<String> stripTablePlaceholders(List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            return List.of();
        }
        java.util.List<String> cleaned = new java.util.ArrayList<>(tables.size());
        for (String table : tables) {
            String normalized = normalize(table);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (hasTablePlaceholder(normalized)) {
                continue;
            }
            cleaned.add(normalized);
        }
        return cleaned.isEmpty() ? List.of() : cleaned;
    }

    private boolean hasTablePlaceholder(String tableValue) {
        String normalized = normalize(tableValue);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("${table}") || lower.contains("{table}") || lower.contains("{{table}}");
    }

    private String replaceTablePlaceholder(String template, String tableName) {
        String resolvedTemplate = StringUtils.hasText(template) ? template : "${table}";
        String resolvedTable = StringUtils.hasText(tableName) ? tableName : "table";
        return resolvedTemplate
            .replace("${table}", resolvedTable)
            .replace("{table}", resolvedTable)
            .replace("{{table}}", resolvedTable);
    }

    private String stripSchema(String table) {
        String normalized = normalize(table);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        int idx = normalized.lastIndexOf('.');
        if (idx > -1 && idx < normalized.length() - 1) {
            return normalized.substring(idx + 1);
        }
        return normalized;
    }

    private boolean isSourceAlignedTables(List<String> sourceTables, List<String> writerTables) {
        if (sourceTables == null || writerTables == null || sourceTables.isEmpty() || writerTables.isEmpty()) {
            return false;
        }
        if (sourceTables.size() != writerTables.size()) {
            return false;
        }
        for (int i = 0; i < sourceTables.size(); i++) {
            String source = normalize(sourceTables.get(i));
            String writer = normalize(writerTables.get(i));
            if (!StringUtils.hasText(source) || !StringUtils.hasText(writer)) {
                return false;
            }
            String sourceLower = source.toLowerCase(java.util.Locale.ROOT);
            String writerLower = writer.toLowerCase(java.util.Locale.ROOT);
            if (sourceLower.equals(writerLower)) {
                continue;
            }
            String sourceBase = stripSchema(source).toLowerCase(java.util.Locale.ROOT);
            String writerBase = stripSchema(writer).toLowerCase(java.util.Locale.ROOT);
            if (!sourceBase.equals(writerBase)) {
                return false;
            }
        }
        return true;
    }

    @SafeVarargs
    private final List<String> mergeTables(List<String>... sources) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (sources == null) {
            return List.of();
        }
        for (List<String> source : sources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            for (String item : source) {
                String text = normalize(item);
                if (StringUtils.hasText(text)) {
                    merged.add(text);
                }
            }
        }
        return merged.isEmpty() ? List.of() : new java.util.ArrayList<>(merged);
    }

    private void setTableField(Map<?, ?> map, List<String> tables) {
        if (map == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutable = (Map<Object, Object>) map;
        mutable.put("table", tables);
    }

    private String normalizeAddaxPlugin(String pluginType, boolean reader) {
        if (!StringUtils.hasText(pluginType)) {
            return pluginType;
        }
        String normalized = pluginType.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if ("dm".equals(lower) || "dameng".equals(lower) || "dm8".equals(lower) || "dameng8".equals(lower)) {
            return reader ? "rdbmsreader" : "rdbmswriter";
        }
        if ("dmreader".equals(lower)) {
            return "rdbmsreader";
        }
        if ("dmwriter".equals(lower)) {
            return "rdbmswriter";
        }
        if ("rdbms".equals(lower)) {
            return reader ? "rdbmsreader" : "rdbmswriter";
        }
        return normalized;
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<String> direct = extractTableValues(config.get("table"));
        if (!direct.isEmpty()) {
            return direct;
        }
        List<String> alternate = extractTableValues(config.get("tables"));
        if (!alternate.isEmpty()) {
            return alternate;
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            List<String> values = extractTableValues(map.get("table"));
            if (!values.isEmpty()) {
                return values;
            }
            values = extractTableValues(map.get("tables"));
            if (!values.isEmpty()) {
                return values;
            }
        } else if (connection instanceof List<?> list) {
            java.util.LinkedHashSet<String> collected = new java.util.LinkedHashSet<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> entryMap) {
                    collected.addAll(extractTableValues(entryMap.get("table")));
                    collected.addAll(extractTableValues(entryMap.get("tables")));
                }
            }
            if (!collected.isEmpty()) {
                return List.copyOf(collected);
            }
        }
        return List.of();
    }

    private List<String> extractTableValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String str) {
            String normalized = normalize(str);
            return StringUtils.hasText(normalized) ? List.of(normalized) : List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.LinkedHashSet<String> collected = new java.util.LinkedHashSet<>();
            for (Object entry : iterable) {
                String normalized = normalize(entry);
                if (StringUtils.hasText(normalized)) {
                    collected.add(normalized);
                }
            }
            return List.copyOf(collected);
        }
        return List.of();
    }

    private boolean isFileSourceType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return false;
        }
        String lower = sourceType.toLowerCase(java.util.Locale.ROOT);
        return "excel".equals(lower) || "csv".equals(lower) || "excelreader".equals(lower) || "txtfilereader".equals(lower);
    }

    private String resolveFileReaderType(String sourceType) {
        if (!StringUtils.hasText(sourceType)) {
            return null;
        }
        String lower = sourceType.toLowerCase(java.util.Locale.ROOT);
        return switch (lower) {
            case "excel", "excelreader" -> "excelreader";
            case "csv", "txtfilereader" -> "txtfilereader";
            default -> null;
        };
    }

    private String resolveTablePrefix(Map<String, Object> writerConfig) {
        if (writerConfig == null || writerConfig.isEmpty()) {
            return null;
        }
        String prefix = normalize(writerConfig.get("tablePrefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        prefix = normalize(writerConfig.get("prefix"));
        if (StringUtils.hasText(prefix)) {
            return prefix;
        }
        return normalize(writerConfig.get("targetPrefix"));
    }

    private void applySyncPrefixToWriterConfig(Map<String, Object> writerConfig, String syncPrefix) {
        if (writerConfig == null || writerConfig.isEmpty() || !StringUtils.hasText(syncPrefix)) {
            return;
        }
        if (!StringUtils.hasText(normalize(writerConfig.get("tablePrefix")))) {
            writerConfig.put("tablePrefix", syncPrefix);
        }
    }

    private String resolveSyncMode(SyncSpec sync) {
        String mode = sync == null ? null : normalize(sync.mode());
        return connectorCapabilityService.normalizeSyncMode(mode);
    }

    private String resolveSyncSchedule(SyncSpec sync) {
        if (sync == null || sync.schedule() == null) {
            return null;
        }
        String type = normalize(sync.schedule().type());
        String cron = normalize(sync.schedule().cron());
        Integer interval = sync.schedule().intervalMinutes();
        if (!StringUtils.hasText(type)) {
            if (StringUtils.hasText(cron)) {
                validateCronOrThrow(cron);
                return "cron:" + cron;
            }
            if (interval != null) {
                if (interval <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调度间隔必须为正整数分钟");
                }
                return "interval:" + interval;
            }
            return null;
        }
        if ("manual".equalsIgnoreCase(type)) {
            return "manual";
        }
        if ("cron".equalsIgnoreCase(type)) {
            if (!StringUtils.hasText(cron)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调度策略为 cron 时必须填写 Cron 表达式");
            }
            validateCronOrThrow(cron);
            return "cron:" + cron;
        }
        if ("interval".equalsIgnoreCase(type)) {
            if (interval == null || interval <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调度策略为 interval 时必须填写正整数间隔");
            }
            return "interval:" + interval;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的调度策略: " + type);
    }

    private void validateCronOrThrow(String cronText) {
        String cron = normalize(cronText);
        if (!StringUtils.hasText(cron)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron 表达式不能为空");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron 表达式不合法: " + cron);
        }
    }

    private void validateSyncScheduleText(String syncSchedule) {
        String schedule = normalize(syncSchedule);
        if (!StringUtils.hasText(schedule)) {
            return;
        }
        String lower = schedule.toLowerCase(java.util.Locale.ROOT);
        if ("manual".equals(lower)) {
            return;
        }
        if (lower.startsWith("interval:")) {
            String intervalText = normalize(schedule.substring("interval:".length()));
            Integer interval = parsePositiveInt(intervalText);
            if (interval == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interval 调度格式不正确，应为 interval:<正整数分钟>");
            }
            return;
        }
        if (lower.startsWith("cron:")) {
            String cron = normalize(schedule.substring("cron:".length()));
            validateCronOrThrow(cron);
            return;
        }
        // 兼容历史格式：直接写 cron 表达式
        if (schedule.contains(" ")) {
            validateCronOrThrow(schedule);
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调度配置格式不正确，仅支持 manual / interval:* / cron:*");
    }

    private Map<String, Object> buildSyncConfig(SyncSpec sync) {
        if (sync == null) {
            return null;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        String syncMode = resolveSyncMode(sync);
        if ("incremental".equalsIgnoreCase(syncMode)) {
            String incrementalColumn = normalize(sync.incrementalColumn());
            String incrementalType = normalize(sync.incrementalType());
            String initialWatermark = normalize(sync.initialWatermark());
            if (!StringUtils.hasText(incrementalColumn)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "增量同步缺少增量列配置");
            }
            if (StringUtils.hasText(incrementalColumn)) {
                config.put("incrementalColumn", incrementalColumn);
            }
            if (StringUtils.hasText(incrementalType)) {
                config.put("incrementalType", incrementalType);
            }
            if (StringUtils.hasText(initialWatermark)) {
                config.put("initialWatermark", initialWatermark);
            }
        }

        Map<String, Object> governance = new LinkedHashMap<>();
        if (sync.taskConcurrency() != null && sync.taskConcurrency() > 0) {
            governance.put("maxConcurrentRuns", sync.taskConcurrency());
        }
        if (sync.sourceConcurrency() != null && sync.sourceConcurrency() >= 0) {
            governance.put("sourceConcurrencyLimit", sync.sourceConcurrency());
        }
        if (sync.projectConcurrency() != null && sync.projectConcurrency() >= 0) {
            governance.put("projectConcurrencyLimit", sync.projectConcurrency());
        }
        String projectKey = normalize(sync.projectKey());
        if (StringUtils.hasText(projectKey)) {
            governance.put("projectKey", projectKey);
        }
        String priority = normalize(sync.priority());
        if (StringUtils.hasText(priority)) {
            governance.put("priority", priority.toUpperCase(java.util.Locale.ROOT));
        }
        String rejectPolicy = normalize(sync.rejectPolicy());
        if (StringUtils.hasText(rejectPolicy)) {
            governance.put("rejectPolicy", rejectPolicy.toUpperCase(java.util.Locale.ROOT));
        }
        String windowStart = normalize(sync.windowStart());
        String windowEnd = normalize(sync.windowEnd());
        if (StringUtils.hasText(windowStart) && StringUtils.hasText(windowEnd)) {
            governance.put("windowStart", windowStart);
            governance.put("windowEnd", windowEnd);
            String timezone = normalize(sync.windowTimezone());
            governance.put("windowTimezone", StringUtils.hasText(timezone) ? timezone : "Asia/Shanghai");
        }
        if (!governance.isEmpty()) {
            config.put("governance", governance);
        }
        return config.isEmpty() ? null : config;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private String resolveConnectorType(boolean isFileSource, String sourceType) {
        if (isFileSource || isFileSourceType(normalize(sourceType))) {
            return "file";
        }
        String normalized = normalize(sourceType);
        if ("airbyte".equalsIgnoreCase(normalized)) {
            return "airbyte";
        }
        return "addax";
    }

    private void validateSyncModeCapability(String connectorType, String syncMode) {
        try {
            connectorCapabilityService.validateSyncModeOrThrow(connectorType, syncMode);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private List<IngestionTaskTemplateDTO> buildTemplateCatalog() {
        return List.of(
            new IngestionTaskTemplateDTO(
                "erp_jdbc_full",
                "ERP 全量入湖模板",
                "适用于 ERP 多表全量同步，自动推荐 ods_erp_ 前缀。",
                "database",
                "addax",
                Map.of(
                    "sourceCategory",
                    "database",
                    "syncMode",
                    "full_refresh",
                    "tableSelectionMode",
                    "all",
                    "syncPrefix",
                    "ods_erp_",
                    "sourceSystem",
                    "ERP",
                    "scheduleType",
                    "manual"
                ),
                List.of("sourceDataSourceId"),
                List.of("全量任务会先清空目标表，建议安排在低峰时段执行。"),
                "1.1.0"
            ),
            new IngestionTaskTemplateDTO(
                "crm_jdbc_incremental",
                "CRM 增量入湖模板",
                "适用于按更新时间字段增量同步，默认 incrementalColumn=updated_at。",
                "database",
                "addax",
                Map.of(
                    "sourceCategory",
                    "database",
                    "syncMode",
                    "incremental",
                    "incrementalColumn",
                    "updated_at",
                    "incrementalType",
                    "datetime",
                    "syncPrefix",
                    "ods_crm_",
                    "sourceSystem",
                    "CRM",
                    "scheduleType",
                    "interval",
                    "scheduleIntervalMinutes",
                    30
                ),
                List.of("sourceDataSourceId", "incrementalColumn"),
                List.of("增量模式建议优先使用单表验证水位推进，再批量扩展。"),
                "1.1.0"
            ),
            new IngestionTaskTemplateDTO(
                "excel_batch_full",
                "Excel 批量入湖模板",
                "适用于文件批量导入场景，默认全量模式与 ods_file_ 前缀。",
                "file",
                "file",
                Map.of(
                    "sourceCategory",
                    "file",
                    "syncMode",
                    "full_refresh",
                    "syncPrefix",
                    "ods_file_",
                    "scheduleType",
                    "manual"
                ),
                List.of("file"),
                List.of("请先上传文件并确认字段映射后再执行。"),
                "1.1.0"
            )
        );
    }

    private java.util.Optional<IngestionTaskTemplateDTO> findTemplate(String templateId) {
        String id = normalize(templateId);
        if (!StringUtils.hasText(id)) {
            return java.util.Optional.empty();
        }
        return buildTemplateCatalog().stream()
            .filter(item -> id.equalsIgnoreCase(item.id()))
            .findFirst();
    }

    private IngestionTemplateRenderDTO renderTemplate(
        IngestionTaskTemplateDTO template,
        Map<String, Object> params,
        boolean strictRequired
    ) {
        Map<String, Object> renderedDefaults = safeMap(template.defaults());
        Map<String, Object> requestParams = safeMap(params);
        for (Map.Entry<String, Object> entry : requestParams.entrySet()) {
            String key = normalize(entry.getKey());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (!hasTemplateValue(value)) {
                continue;
            }
            renderedDefaults.put(key, value);
        }
        List<String> warnings = new ArrayList<>();
        if (template.warnings() != null) {
            warnings.addAll(template.warnings());
        }
        List<String> errors = new ArrayList<>();
        List<String> requiredParams = template.requiredParams() == null ? List.of() : template.requiredParams();

        // File template: sourceSystem 默认跟随文件名，避免写死 unknown/file。
        if ("file".equalsIgnoreCase(normalize(template.sourceCategory()))) {
            String fileName = normalize(requestParams.get("fileName"));
            if (StringUtils.hasText(fileName) && !hasTemplateValue(renderedDefaults.get("sourceSystem"))) {
                renderedDefaults.put("sourceSystem", fileName);
            }
        }

        for (String requiredKey : requiredParams) {
            if (!hasTemplateValue(renderedDefaults.get(requiredKey))) {
                String fallback = "file".equalsIgnoreCase(requiredKey) ? normalize(requestParams.get("fileName")) : null;
                if (!StringUtils.hasText(fallback)) {
                    errors.add("缺少必填参数: " + requiredKey);
                }
            }
        }

        String syncMode = normalize(renderedDefaults.get("syncMode"));
        String scheduleType = normalize(renderedDefaults.get("scheduleType"));

        if ("incremental".equalsIgnoreCase(syncMode) && !hasTemplateValue(renderedDefaults.get("incrementalColumn"))) {
            errors.add("增量模板必须提供 incrementalColumn");
        }
        if ("full_refresh".equalsIgnoreCase(syncMode) && warnings.stream().noneMatch(it -> it.contains("全量"))) {
            warnings.add("全量模式会先 DROP/清空目标表，请确认低峰执行窗口。");
        }
        if ("interval".equalsIgnoreCase(scheduleType)) {
            Integer minutes = parsePositiveInt(renderedDefaults.get("scheduleIntervalMinutes"));
            if (minutes == null) {
                errors.add("scheduleType=interval 时必须提供正整数 scheduleIntervalMinutes");
            }
        }
        if ("cron".equalsIgnoreCase(scheduleType)) {
            String cron = normalize(renderedDefaults.get("scheduleCron"));
            if (!isLikelyCronExpression(cron)) {
                errors.add("scheduleType=cron 时必须提供合法的 scheduleCron（5-7 段）");
            }
        }

        boolean canApply = errors.isEmpty() || !strictRequired;
        return new IngestionTemplateRenderDTO(
            template.id(),
            template.name(),
            template.version(),
            renderedDefaults,
            requiredParams,
            warnings,
            errors,
            canApply
        );
    }

    private boolean hasTemplateValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private Integer parsePositiveInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        String text = normalize(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isLikelyCronExpression(String cron) {
        if (!StringUtils.hasText(cron)) {
            return false;
        }
        String[] parts = cron.trim().split("\\s+");
        return parts.length >= 5 && parts.length <= 7;
    }

    // ==================== 新增CRUD端点 ====================

    /**
     * GET /api/ingestion/tasks : 获取任务列表
     */
    @GetMapping("/tasks/list")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO>> getTasks(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) java.util.UUID sourceDataSourceId,
        org.springframework.data.domain.Pageable pageable
    ) {
        if (sourceDataSourceId != null) {
            java.util.List<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> list =
                ingestionTaskQueryService.findBySourceDataSourceId(sourceDataSourceId, false);
            org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> page =
                new org.springframework.data.domain.PageImpl<>(list, pageable, list.size());
            return ResponseEntity.ok(page);
        }
        org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> page =
            ingestionTaskQueryService.findAll(status, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/ingestion/tasks/by-source : 根据数据源ID获取任务列表
     */
    @GetMapping("/tasks/by-source")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO>> listTasksBySource(
        @RequestParam java.util.UUID sourceDataSourceId,
        @RequestParam(required = false, defaultValue = "false") boolean includeDeleted
    ) {
        List<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> list =
            ingestionTaskQueryService.findBySourceDataSourceId(sourceDataSourceId, includeDeleted);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/ingestion/tasks/{id} : 获取任务详情
     */
    @GetMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> getTask(
        @PathVariable Long id
    ) {
        return ingestionTaskQueryService.findOne(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO>notFound().build());
    }

    /**
     * PUT /api/ingestion/tasks/{id} : 更新任务
     */
    @PutMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> updateTask(
        @PathVariable Long id,
        @Valid @RequestBody com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO taskDTO
    ) {
        if (!id.equals(taskDTO.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID不匹配");
        }
        validateSyncScheduleText(taskDTO.getSyncSchedule());
        String syncMode = connectorCapabilityService.normalizeSyncMode(taskDTO.getSyncMode());
        taskDTO.setSyncMode(syncMode);
        boolean isFileSourceUpdate = isFileSourceType(normalize(taskDTO.getSourceType()));
        if (!isFileSourceUpdate && taskDTO.getSourceDataSourceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择数据源连接");
        }
        String connectorType = resolveConnectorType(isFileSourceUpdate, taskDTO.getSourceType());
        validateSyncModeCapability(connectorType, syncMode);
        Map<String, Object> syncConfig = safeMap(jsonNodeToMap(taskDTO.getSyncConfig()));
        if ("incremental".equalsIgnoreCase(syncMode)) {
            if (!StringUtils.hasText(normalize(syncConfig.get("incrementalColumn")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "增量同步缺少增量列配置");
            }
        }
        if (!"incremental".equalsIgnoreCase(syncMode)) {
            syncConfig.remove("incrementalColumn");
            syncConfig.remove("incrementalType");
            syncConfig.remove("initialWatermark");
        }
        taskDTO.setSyncConfig(syncConfig.isEmpty() ? null : toJsonNode(syncConfig));
        Map<String, Object> sourceOverrides = isFileSourceUpdate
            ? safeMap(jsonNodeToMap(taskDTO.getSourceConfig()))
            : sanitizeSourceOverrides(jsonNodeToMap(taskDTO.getSourceConfig()));
        if (!isFileSourceUpdate && hasConnectionOverride(jsonNodeToMap(taskDTO.getSourceConfig()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入湖任务必须使用已配置的数据源连接");
        }
        taskDTO.setSourceConfig(sourceOverrides.isEmpty() ? null : toJsonNode(sourceOverrides));
        if (!StringUtils.hasText(taskDTO.getSourceType())) {
            String inferred = resolvePlugin(null, sourceOverrides, List.of("readerType", "reader", "type", "name", "sourceType"));
            if (StringUtils.hasText(inferred)) {
                taskDTO.setSourceType(inferred);
            }
        }
        String syncPrefix = normalize(taskDTO.getSyncPrefix());
        Map<String, Object> writerConfigForUpdate = jsonNodeToMap(taskDTO.getDestinationConfig());
        List<String> requestedTables = stripTablePlaceholders(
            mergeTables(extractTables(sourceOverrides), extractTables(writerConfigForUpdate))
        );
        boolean rebuildMapping = taskDTO.getTableMapping() == null || taskDTO.getTableMapping().isNull();
        if (StringUtils.hasText(syncPrefix) || !requestedTables.isEmpty()) {
            rebuildMapping = true;
        }
        com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolvedSource = null;
        if ((!StringUtils.hasText(taskDTO.getSourceType()) || rebuildMapping)
                && !isFileSourceUpdate && taskDTO.getSourceDataSourceId() != null) {
            resolvedSource = sourceResolver.resolve(taskDTO.getSourceDataSourceId(), List.of());
        }
        if (!StringUtils.hasText(taskDTO.getSourceType())) {
            String inferred = resolvePlugin(null, sourceOverrides, List.of("readerType", "reader", "type", "name", "sourceType"));
            if (!StringUtils.hasText(inferred) && resolvedSource != null) {
                inferred = resolvePlugin(null, safeMap(resolvedSource.readerConfig()), List.of("readerType", "reader", "type", "name"));
                if (!StringUtils.hasText(inferred)) {
                    inferred = resolvedSource.readerType();
                }
            }
            if (!StringUtils.hasText(inferred) && resolvedSource != null) {
                String jdbcUrl = normalize(resolvedSource.detail() == null ? null : resolvedSource.detail().jdbcUrl());
                if (StringUtils.hasText(jdbcUrl)) {
                    inferred = "rdbmsreader";
                }
            }
            if (StringUtils.hasText(inferred)) {
                taskDTO.setSourceType(inferred);
            }
        }
        if (StringUtils.hasText(taskDTO.getSourceType())) {
            taskDTO.setSourceType(normalizeAddaxPlugin(taskDTO.getSourceType(), true));
        }
        if (StringUtils.hasText(taskDTO.getDestinationType())) {
            taskDTO.setDestinationType(normalizeAddaxPlugin(taskDTO.getDestinationType(), false));
        }
        if (rebuildMapping) {
            com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver.ResolvedSource resolved =
                (resolvedSource != null) ? resolvedSource
                : (!isFileSourceUpdate && taskDTO.getSourceDataSourceId() != null)
                    ? sourceResolver.resolve(taskDTO.getSourceDataSourceId(), List.of())
                    : null;
            Map<String, Object> readerConfig = resolved != null
                ? mergeReaderOverrides(safeMap(resolved.readerConfig()), sourceOverrides)
                : new java.util.LinkedHashMap<>(sourceOverrides);
            if (StringUtils.hasText(taskDTO.getSourceType())) {
                readerConfig.putIfAbsent("readerType", taskDTO.getSourceType());
            }
            Map<String, Object> writerConfig = jsonNodeToMap(taskDTO.getDestinationConfig());
            List<String> selectionTables = mergeTables(extractTables(readerConfig), extractTables(writerConfig));
            if (!selectionTables.isEmpty()) {
                applyTables(readerConfig, selectionTables);
                List<String> existingWriterTables = stripTablePlaceholders(extractTables(writerConfig));
                String prefix = StringUtils.hasText(syncPrefix) ? syncPrefix : resolveTablePrefix(writerConfig);
                if (existingWriterTables.isEmpty() || isSourceAlignedTables(selectionTables, existingWriterTables)) {
                    List<String> writerTables = applyPrefixToTables(stripSchemaTables(selectionTables), prefix);
                    applyTables(writerConfig, writerTables);
                }
            }
            SyncSpec syncSpec = StringUtils.hasText(syncPrefix)
                ? new SyncSpec(null, null, null, null, syncPrefix, null, null, null, null, null, null, null, null, null, null, null, null)
                : null;
            List<Map<String, String>> tableMapping = deriveTableMapping(readerConfig, writerConfig, syncSpec);
            if (!tableMapping.isEmpty()) {
                taskDTO.setTableMapping(toJsonNode(tableMapping));
            }
            if (!writerConfig.isEmpty()) {
                taskDTO.setDestinationConfig(toJsonNode(writerConfig));
            }
        }
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO updated = ingestionTaskService.update(id, taskDTO);
            auditService.auditAction(
                "INGESTION_TASK_UPDATE",
                AuditStage.SUCCESS,
                updated.getName(),
                Map.of("summary", "更新入湖任务", "taskId", id, "operator", operator)
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("summary", "更新入湖任务失败");
            meta.put("taskId", id);
            meta.put("operator", operator);
            String error = trimMessage(ex.getMessage());
            if (StringUtils.hasText(error)) {
                meta.put("error", error);
            }
            auditService.auditAction(
                "INGESTION_TASK_UPDATE",
                AuditStage.FAIL,
                taskDTO.getName(),
                meta
            );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    /**
     * DELETE /api/ingestion/tasks/{id} : 删除任务（软删除）
     */
    @DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteTask(
        @PathVariable Long id
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO task;
        try {
            task = ingestionTaskService.delete(id);
        } catch (IllegalArgumentException ex) {
            auditService.auditAction(
                "INGESTION_TASK_DELETE",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "删除入湖任务失败", "taskId", id, "operator", operator)
            );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        auditService.auditAction(
            "INGESTION_TASK_DELETE",
            AuditStage.SUCCESS,
            task.getName(),
            Map.of("summary", "删除入湖任务", "taskId", id, "operator", operator)
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", task);
        payload.put("taskId", id);
        payload.put("status", "deleted");
        return ResponseEntity.ok(ApiResponses.ok(payload));
    }

    /**
     * GET /api/ingestion/tasks/changes : 获取接入变更记录
     */
    @GetMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<org.springframework.data.domain.Page<IngestionTaskChangeLogDTO>> listChangeLogs(
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) String objType,
        @RequestParam(required = false) String changeType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String assignee,
        @RequestParam(required = false) String keyword,
        org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<IngestionTaskChangeLogDTO> page =
            changeLogService.search(taskId, objType, changeType, status, assignee, keyword, pageable);
        auditService.auditAction(
            "INGESTION_CHANGELOG_LIST",
            AuditStage.SUCCESS,
            String.valueOf(taskId == null ? "all" : taskId),
            Map.of("summary", "查看接入变更记录", "taskId", taskId == null ? "all" : taskId, "assignee", assignee == null ? "all" : assignee)
        );
        return ResponseEntity.ok(page);
    }

    /**
     * POST /api/ingestion/tasks/changes : 手工登记接入变更
     */
    @PostMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionTaskChangeLogDTO> createChangeLog(
        @Valid @RequestBody ChangeLogRequest request
    ) {
        if (request == null || request.taskId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务ID不能为空");
        }
        if (!StringUtils.hasText(request.changeType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "变更类型不能为空");
        }
        if (!StringUtils.hasText(request.summary())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "变更摘要不能为空");
        }
        java.util.Optional<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> taskOpt =
            ingestionTaskQueryService.findOne(request.taskId());
        if (taskOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        String taskName = StringUtils.hasText(request.taskName()) ? request.taskName() : taskOpt.get().getName();
        IngestionTaskChangeLogDTO dto = new IngestionTaskChangeLogDTO();
        dto.setTaskId(request.taskId());
        dto.setTaskName(taskName);
        dto.setObjType(IngestionTaskChangeLogService.OBJ_TYPE_INGEST_JOB);
        dto.setChangeType(request.changeType());
        dto.setSummary(request.summary());
        dto.setDetail(request.detail());
        dto.setRiskLevel(request.riskLevel());
        dto.setStatus(request.status());
        dto.setAssignee(request.assignee());
        dto.setApprovalComment(request.approvalComment());
        IngestionTaskChangeLogDTO saved = changeLogService.createManualLog(dto);
        auditService.auditAction(
            "INGESTION_CHANGELOG_CREATE",
            AuditStage.SUCCESS,
            String.valueOf(saved.getTaskId()),
            Map.of("summary", "登记接入变更", "taskId", saved.getTaskId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * POST /api/ingestion/tasks/changes/{id}/transition : 执行变更流转动作
     */
    @PostMapping("/tasks/changes/{id}/transition")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionTaskChangeLogDTO> transitionChangeLog(
        @PathVariable Long id,
        @RequestBody(required = false) ChangeLogTransitionRequest request
    ) {
        String action = request != null ? request.action() : null;
        String assignee = request != null ? request.assignee() : null;
        String approvalComment = request != null ? request.approvalComment() : null;
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            IngestionTaskChangeLogDTO updated = changeLogService.transition(id, action, assignee, approvalComment, operator);
            auditService.auditAction(
                "INGESTION_CHANGELOG_TRANSITION",
                AuditStage.SUCCESS,
                String.valueOf(updated.getTaskId()),
                Map.of(
                    "summary",
                    "变更流转",
                    "changeLogId",
                    id,
                    "action",
                    action == null ? "" : action,
                    "status",
                    updated.getStatus(),
                    "operator",
                    operator
                )
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            auditService.auditAction(
                "INGESTION_CHANGELOG_TRANSITION",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "变更流转失败", "changeLogId", id, "action", action == null ? "" : action, "operator", operator)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            auditService.auditAction(
                "INGESTION_CHANGELOG_TRANSITION",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "变更流转失败", "changeLogId", id, "action", action == null ? "" : action, "operator", operator)
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/execute : 手动执行任务
     */
    @PostMapping("/tasks/{id}/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> executeTask(
        @PathVariable Long id
    ) {
        try {
            com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO execution = ingestionTaskService.execute(id);
            return ResponseEntity.ok(execution);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (TaskRejectedException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台执行队列繁忙，请稍后重试");
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/execute/async : 异步触发任务执行
     */
    @PostMapping("/tasks/{id}/execute/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> executeTaskAsync(
        @PathVariable Long id
    ) {
        try {
            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO task = ingestionTaskService.validateAsyncExecutionRequest(id);
            ingestionTaskService.executeAsync(id);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", id);
            payload.put("taskName", task.getName());
            payload.put("status", "submitted");
            payload.put("async", true);
            payload.put("pollIntervalMs", resolveExecutionPollIntervalMs());
            payload.put("message", "任务已提交，正在后台触发执行");
            return ResponseEntity.accepted().body(payload);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (TaskRejectedException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台执行队列繁忙，请稍后重试");
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/executions/{executionId}/retry : 重试执行
     */
    @PostMapping("/tasks/{id}/executions/{executionId}/retry")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> retryExecution(
        @PathVariable Long id,
        @PathVariable Long executionId,
        @RequestParam(value = "mode", required = false, defaultValue = "FAILED_ONLY") String mode
    ) {
        try {
            return ResponseEntity.ok(ingestionTaskService.retryExecution(id, executionId, mode));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/executions/{executionId}/retry/async : 异步重试执行
     */
    @PostMapping("/tasks/{id}/executions/{executionId}/retry/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> retryExecutionAsync(
        @PathVariable Long id,
        @PathVariable Long executionId,
        @RequestParam(value = "mode", required = false, defaultValue = "FAILED_ONLY") String mode
    ) {
        try {
            ingestionTaskService.validateAsyncRetryRequest(id, executionId, mode);
            ingestionTaskService.retryExecutionAsync(id, executionId, mode);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", id);
            payload.put("executionId", executionId);
            payload.put("status", "submitted");
            payload.put("async", true);
            payload.put("pollIntervalMs", resolveExecutionPollIntervalMs());
            payload.put("message", "重试已提交，正在后台执行");
            return ResponseEntity.accepted().body(payload);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (TaskRejectedException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台执行队列繁忙，请稍后重试");
        }
    }

    /**
     * POST /api/ingestion/tasks/{id}/dag/rebuild : 强制重建 DAG 文件
     */
    @PostMapping("/tasks/{id}/dag/rebuild")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> rebuildDag(
        @PathVariable Long id
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        try {
            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO updated = ingestionTaskService.rebuildDag(id);
            auditService.auditAction(
                "INGESTION_TASK_DAG_REBUILD",
                AuditStage.SUCCESS,
                updated.getName(),
                Map.of("summary", "重建入湖 DAG", "taskId", id, "operator", operator)
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            auditService.auditAction(
                "INGESTION_TASK_DAG_REBUILD",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "重建入湖 DAG 失败", "taskId", id, "operator", operator, "error", trimMessage(e.getMessage()))
            );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        } catch (IllegalStateException e) {
            auditService.auditAction(
                "INGESTION_TASK_DAG_REBUILD",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "重建入湖 DAG 失败", "taskId", id, "operator", operator, "error", trimMessage(e.getMessage()))
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/executions : 获取任务执行历史
     */
    @GetMapping("/tasks/{id}/executions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO>> getExecutions(
        @PathVariable Long id,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "failureCategory", required = false) String failureCategory,
        org.springframework.data.domain.Pageable pageable
    ) {
        org.springframework.data.domain.Page<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> page =
            ingestionTaskService.getExecutions(id, pageable, status, failureCategory);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/ingestion/tasks/executions/observability : 获取执行可观测指标
     */
    @GetMapping("/tasks/executions/observability")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionExecutionObservabilityDTO> getExecutionObservability(
        @RequestParam(value = "taskId", required = false) Long taskId,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "sourceDataSourceId", required = false) java.util.UUID sourceDataSourceId,
        @RequestParam(value = "from", required = false) Instant from,
        @RequestParam(value = "to", required = false) Instant to,
        @RequestParam(value = "days", required = false) Integer days,
        @RequestParam(value = "timeoutMinutes", required = false) Integer timeoutMinutes
    ) {
        try {
            return ResponseEntity.ok(
                ingestionTaskQueryService.getExecutionObservability(taskId, sourceType, sourceDataSourceId, from, to, days, timeoutMinutes)
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * GET /api/ingestion/tasks/executions/governance-overview : 获取运行资源治理概览
     */
    @GetMapping("/tasks/executions/governance-overview")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionGovernanceOverviewDTO> getGovernanceOverview(
        @RequestParam(value = "hours", required = false) Integer hours
    ) {
        return ResponseEntity.ok(ingestionTaskQueryService.getGovernanceOverview(hours));
    }

    /**
     * GET /api/ingestion/tasks/{id}/executions/latest : 获取最新执行记录
     */
    @GetMapping("/tasks/{id}/executions/latest")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO> getLatestExecution(
        @PathVariable Long id
    ) {
        return ingestionTaskService.getLatestExecution(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO>notFound().build());
    }

    /**
     * GET /api/ingestion/templates : 获取入湖模板清单
     */
    @GetMapping("/templates")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<IngestionTaskTemplateDTO>> listIngestionTemplates() {
        return ResponseEntity.ok(buildTemplateCatalog());
    }

    /**
     * POST /api/ingestion/templates/{templateId}/render : 渲染模板默认值并返回预检结果
     */
    @PostMapping("/templates/{templateId}/render")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionTemplateRenderDTO> renderIngestionTemplate(
        @PathVariable String templateId,
        @RequestBody(required = false) IngestionTemplateRenderRequest request
    ) {
        IngestionTaskTemplateDTO template = findTemplate(templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        Map<String, Object> params = safeMap(request == null ? null : request.params());
        IngestionTemplateRenderDTO rendered = renderTemplate(template, params, request != null && Boolean.TRUE.equals(request.strictRequired()));
        return ResponseEntity.ok(rendered);
    }

    /**
     * GET /api/ingestion/connectors/capabilities : 获取连接器能力清单
     */
    @GetMapping("/connectors/capabilities")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<IngestionConnectorCapabilityDTO>> listConnectorCapabilities() {
        return ResponseEntity.ok(connectorCapabilityService.listEnabled());
    }

    /**
     * GET /api/ingestion/connectors/capabilities/{connectorType} : 获取单个连接器能力
     */
    @GetMapping("/connectors/capabilities/{connectorType}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionConnectorCapabilityDTO> getConnectorCapability(@PathVariable String connectorType) {
        return connectorCapabilityService
            .getByConnectorType(connectorType)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/ingestion/tasks/{id}/realtime-status : 获取任务实时状态（预留）
     */
    @GetMapping("/tasks/{id}/realtime-status")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<IngestionRealtimeStatusDTO> getRealtimeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(realtimeTaskStatusService.getTaskStatus(id));
    }

    /**
     * GET /api/ingestion/tasks/{id}/incremental-states : 获取增量检查点
     */
    @GetMapping("/tasks/{id}/incremental-states")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO>> getIncrementalStates(
        @PathVariable Long id
    ) {
        try {
            return ResponseEntity.ok(ingestionTaskQueryService.getIncrementalStates(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/incremental-audits : 获取增量水位审计
     */
    @GetMapping("/tasks/{id}/incremental-audits")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<List<com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO>> getIncrementalAudits(
        @PathVariable Long id,
        @RequestParam(value = "executionId", required = false) Long executionId
    ) {
        try {
            return ResponseEntity.ok(ingestionTaskQueryService.getIncrementalAudits(id, executionId));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/incremental-audits/page : 分页获取增量水位审计
     */
    @GetMapping("/tasks/{id}/incremental-audits/page")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Page<com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO>> getIncrementalAuditsPage(
        @PathVariable Long id,
        @RequestParam(value = "executionId", required = false) Long executionId,
        @RequestParam(value = "executionIds", required = false) List<Long> executionIds,
        @RequestParam(value = "from", required = false) Instant from,
        @RequestParam(value = "to", required = false) Instant to,
        @RequestParam(value = "tableName", required = false) String tableName,
        @RequestParam(value = "status", required = false) String status,
        Pageable pageable
    ) {
        try {
            Page<com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO> page =
                ingestionTaskQueryService.getIncrementalAuditsPage(id, executionId, executionIds, from, to, pageable, tableName, status);
            return ResponseEntity.ok(page);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/incremental-audits/summary : 获取增量水位审计汇总
     */
    @GetMapping("/tasks/{id}/incremental-audits/summary")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditSummaryDTO> getIncrementalAuditsSummary(
        @PathVariable Long id,
        @RequestParam(value = "executionId", required = false) Long executionId,
        @RequestParam(value = "executionIds", required = false) List<Long> executionIds,
        @RequestParam(value = "from", required = false) Instant from,
        @RequestParam(value = "to", required = false) Instant to,
        @RequestParam(value = "tableName", required = false) String tableName,
        @RequestParam(value = "status", required = false) String status
    ) {
        try {
            return ResponseEntity.ok(
                ingestionTaskQueryService.getIncrementalAuditsSummary(id, executionId, executionIds, from, to, tableName, status)
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
    }

    /**
     * GET /api/ingestion/tasks/{id}/executions/{executionId}/logs : 获取执行日志
     */
    @GetMapping("/tasks/{id}/executions/{executionId}/logs")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<Map<String, Object>> getExecutionLog(
        @PathVariable Long id,
        @PathVariable Long executionId,
        @RequestParam(value = "tryNumber", required = false) Integer tryNumber,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "scope", required = false) String scope
    ) {
        try {
            Map<String, Object> result = ingestionExecutionQueryService.fetchExecutionLog(id, executionId, tryNumber, keyword, scope);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private long resolveExecutionPollIntervalMs() {
        Long configured = airflowProperties == null ? null : airflowProperties.getExecutionPollIntervalMs();
        long value = configured == null ? 3000L : configured.longValue();
        if (value < 1000L) {
            return 1000L;
        }
        if (value > 30000L) {
            return 30000L;
        }
        return value;
    }
}
