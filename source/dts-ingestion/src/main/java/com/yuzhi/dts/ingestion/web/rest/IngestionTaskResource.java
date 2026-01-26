package com.yuzhi.dts.ingestion.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.JdbcMetadataService;
import com.yuzhi.dts.ingestion.service.openmetadata.OpenMetadataAdapter;
import jakarta.validation.Valid;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final AuditService auditService;
    private final OpenMetadataAdapter openMetadataAdapter;
    private final AirflowAdapter airflowAdapter;
    private final com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService;
    private final JdbcMetadataService jdbcMetadataService;
    private final ObjectMapper objectMapper;

    public IngestionTaskResource(
        AddaxJobService addaxJobService,
        AuditService auditService,
        OpenMetadataAdapter openMetadataAdapter,
        AirflowAdapter airflowAdapter,
        com.yuzhi.dts.ingestion.service.IngestionTaskService ingestionTaskService,
        JdbcMetadataService jdbcMetadataService,
        ObjectMapper objectMapper
    ) {
        this.addaxJobService = addaxJobService;
        this.auditService = auditService;
        this.openMetadataAdapter = openMetadataAdapter;
        this.airflowAdapter = airflowAdapter;
        this.ingestionTaskService = ingestionTaskService;
        this.jdbcMetadataService = jdbcMetadataService;
        this.objectMapper = objectMapper;
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

    public record TableDiscoveryRequest(SourceSpec source, TableDiscoveryFilter filter) {}

    public record TableDiscoveryFilter(String schema, String tablePattern, Integer limit, Boolean includeColumns) {}

    public record TableInfo(String schema, String name, String type, List<ColumnInfo> columns) {}

    public record ColumnInfo(String name, Integer jdbcType, String typeName, Integer columnSize, Integer decimalDigits) {}

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

            boolean useDefault = Boolean.TRUE.equals(request.destination().usePlatformDefault());
            boolean hasJobConfig = request.jobConfig() != null && !request.jobConfig().isEmpty();
            Map<String, Object> readerConfig = safeMap(request.source().config());
            Map<String, Object> writerConfig = safeMap(request.destination().config());
            List<String> streamTables = resolveStreamTables(request.streams());
            if (!streamTables.isEmpty()) {
                applyTables(readerConfig, streamTables);
            }
            String writerType = resolvePlugin(
                useDefault ? request.destination().definitionId() : request.destination().type(),
                writerConfig,
                List.of("writerType", "writer", "type")
            );

            if (!StringUtils.hasText(readerType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Addax Reader 类型");
            }
            if (!hasJobConfig) {
                if (!StringUtils.hasText(writerType)) {
                    String message = useDefault
                        ? "缺少 Addax Writer 类型，请在管理端数据湖配置中设置写入器类型"
                        : "缺少 Addax Writer 类型";
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
                }
                if (writerConfig.isEmpty()) {
                    String message = useDefault
                        ? "缺少 Addax Writer 配置，请在管理端数据湖配置中完善写入器参数"
                        : "缺少 Addax Writer 配置";
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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
            taskDTO.setSourceConfig(toJsonNode(readerConfig));
            taskDTO.setDestinationType(writerType);
            taskDTO.setDestinationConfig(toJsonNode(safeMap(writerConfig)));
            taskDTO.setSyncMode(resolveSyncMode(request.sync()));
            taskDTO.setSyncSchedule(resolveSyncSchedule(request.sync()));
            taskDTO.setAddaxConfig(toJsonNode(request.jobConfig()));
            taskDTO.setAirflowEnabled(airflowRequest == null ? null : airflowRequest.enabled());
            taskDTO.setAirflowDagId(airflowRequest == null ? null : normalize(airflowRequest.dagId()));
            List<Map<String, String>> tableMapping = deriveTableMapping(readerConfig, writerConfig, request.sync());
            if (!tableMapping.isEmpty()) {
                taskDTO.setTableMapping(toJsonNode(tableMapping));
            }

            com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO createdTask = ingestionTaskService.create(taskDTO);
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
                    safeMap(request.source().config()),
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
                    Boolean.TRUE.equals(request.runNow())
                )
            );
            if (ingestionResult != null && !ingestionResult.isEmpty()) {
                result.put("openmetadataIngestion", ingestionResult);
            }

            String airflowJobPath = addaxJobService.toContainerJobPath(jobPath);
            Map<String, Object> airflowConf = new LinkedHashMap<>();
            airflowConf.put("job_path", airflowJobPath);
            airflowConf.put("job_name", jobName);
            airflowConf.put("taskName", request.name());
            airflowConf.put("taskId", createdTask.getId());
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
        Map<String, Object> readerConfig = safeMap(request.source().config());
        String jdbcUrl = resolveJdbcUrl(readerConfig);
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 JDBC URL");
        }
        String driverClass = normalize(readerConfig.get("driver"));
        if (!StringUtils.hasText(driverClass)) {
            driverClass = normalize(readerConfig.get("driverClass"));
        }
        if (!StringUtils.hasText(driverClass)) {
            driverClass = resolveDriverClassFromUrl(jdbcUrl);
        }
        JdbcMetadataService.JdbcConnectionInfo info = new JdbcMetadataService.JdbcConnectionInfo(
            jdbcUrl,
            normalize(readerConfig.get("username")),
            normalize(readerConfig.get("password")),
            driverClass,
            normalize(request.source().driverVersion()),
            resolveJdbcProperties(readerConfig)
        );
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
            targets = sources.stream()
                .map(source -> StringUtils.hasText(prefixValue) ? prefixValue + source : source)
                .toList();
        } else if (targets.size() == 1 && targets.get(0).contains("${table}")) {
            String template = targets.get(0);
            targets = sources.stream()
                .map(source -> template.replace("${table}", source))
                .toList();
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

    private void setTableField(Map<?, ?> map, List<String> tables) {
        if (map == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> mutable = (Map<Object, Object>) map;
        mutable.put("table", tables);
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

    private String resolveSyncMode(SyncSpec sync) {
        String mode = sync == null ? null : normalize(sync.mode());
        return StringUtils.hasText(mode) ? mode : "full_refresh";
    }

    private String resolveSyncSchedule(SyncSpec sync) {
        if (sync == null || sync.schedule() == null) {
            return null;
        }
        String type = normalize(sync.schedule().type());
        String cron = normalize(sync.schedule().cron());
        Integer interval = sync.schedule().intervalMinutes();
        if (!StringUtils.hasText(type)) {
            return cron;
        }
        if ("cron".equalsIgnoreCase(type) && StringUtils.hasText(cron)) {
            return "cron:" + cron;
        }
        if ("interval".equalsIgnoreCase(type) && interval != null) {
            return "interval:" + interval;
        }
        return type;
    }

    private String trimMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    // ==================== 新增CRUD端点 ====================

    /**
     * GET /api/ingestion/tasks : 获取任务列表
     */
    @org.springframework.web.bind.annotation.GetMapping("/tasks/list")
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
            .orElse(org.springframework.http.ResponseEntity.<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO>notFound().build());
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
        if (taskDTO.getTableMapping() == null || taskDTO.getTableMapping().isNull()) {
            Map<String, Object> readerConfig = jsonNodeToMap(taskDTO.getSourceConfig());
            Map<String, Object> writerConfig = jsonNodeToMap(taskDTO.getDestinationConfig());
            List<Map<String, String>> tableMapping = deriveTableMapping(readerConfig, writerConfig, null);
            if (!tableMapping.isEmpty()) {
                taskDTO.setTableMapping(toJsonNode(tableMapping));
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
            return org.springframework.http.ResponseEntity.ok(updated);
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
    @org.springframework.web.bind.annotation.DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<Void> deleteTask(
        @org.springframework.web.bind.annotation.PathVariable Long id
    ) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        java.util.Optional<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> taskOpt = ingestionTaskService.findOne(id);
        if (taskOpt.isEmpty()) {
            auditService.auditAction(
                "INGESTION_TASK_DELETE",
                AuditStage.FAIL,
                String.valueOf(id),
                Map.of("summary", "删除入湖任务失败", "taskId", id, "operator", operator)
            );
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }

        com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO task = taskOpt.get();
        ingestionTaskService.delete(id);
        auditService.auditAction(
            "INGESTION_TASK_DELETE",
            AuditStage.SUCCESS,
            task.getName(),
            Map.of("summary", "删除入湖任务", "taskId", id, "operator", operator)
        );
        return org.springframework.http.ResponseEntity.<Void>noContent().build();
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
     * POST /api/ingestion/tasks/{id}/dag/rebuild : 强制重建 DAG 文件
     */
    @PostMapping("/tasks/{id}/dag/rebuild")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public org.springframework.http.ResponseEntity<com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO> rebuildDag(
        @org.springframework.web.bind.annotation.PathVariable Long id
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
            return org.springframework.http.ResponseEntity.ok(updated);
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
            .orElse(org.springframework.http.ResponseEntity.<com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO>notFound().build());
    }
}
