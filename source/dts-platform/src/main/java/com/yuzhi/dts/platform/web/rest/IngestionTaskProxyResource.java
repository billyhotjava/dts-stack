package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskProxyResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final IngestionServiceClient ingestionClient;
    private final DefaultDestinationSyncService destinationSyncService;
    private final OdsTableMappingSyncService odsTableMappingSyncService;
    private final AuditService auditService;
    private final ExternalRunLogService externalRunLogService;

    public IngestionTaskProxyResource(
        IngestionServiceClient ingestionClient,
        DefaultDestinationSyncService destinationSyncService,
        OdsTableMappingSyncService odsTableMappingSyncService,
        AuditService auditService,
        ExternalRunLogService externalRunLogService
    ) {
        this.ingestionClient = ingestionClient;
        this.destinationSyncService = destinationSyncService;
        this.odsTableMappingSyncService = odsTableMappingSyncService;
        this.auditService = auditService;
        this.externalRunLogService = externalRunLogService;
    }

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        boolean draft = payload != null && Boolean.parseBoolean(String.valueOf(payload.getOrDefault("draft", "false")));
        String taskName = payload == null ? null : String.valueOf(payload.getOrDefault("name", ""));
        if (!StringUtils.hasText(taskName) && payload != null) {
            Object alt = payload.getOrDefault("taskName", payload.getOrDefault("title", ""));
            String fallback = String.valueOf(alt == null ? "" : alt).trim();
            if (StringUtils.hasText(fallback)) {
                payload.put("name", fallback);
                taskName = fallback;
            }
        }
        Map<String, Object> resolvedPayload = payload;
        if (!draft) {
            DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = destinationSyncService.ensureDefaultDestination();
            resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
        }
        ApiResponse<Map<String, Object>> response = ingestionClient.createIngestionTask(resolvedPayload);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        if (response != null && response.getStatus() >= 200 && response.getStatus() < 300) {
            auditService.auditAction(
                "INGESTION_TASK_CREATE",
                AuditStage.SUCCESS,
                taskName,
                Map.of("summary", "创建入湖任务", "name", taskName, "operator", operator)
            );
            try {
                odsTableMappingSyncService.syncFromIngestionPayload(response.getData());
            } catch (RuntimeException ex) {
                auditService.auditAction(
                    "INGESTION_MAPPING_SYNC",
                    AuditStage.FAIL,
                    taskName,
                    Map.of("summary", "同步 ODS 映射失败", "name", taskName, "operator", operator, "error", ex.getMessage())
                );
            }
        } else {
            auditService.auditAction(
                "INGESTION_TASK_CREATE",
                AuditStage.FAIL,
                taskName,
                Map.of("summary", "创建入湖任务失败", "name", taskName, "operator", operator)
            );
        }
        return response;
    }

    @GetMapping("/default-destination")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<DefaultDestinationSyncService.DefaultDestinationStatus> getDefaultDestinationStatus() {
        DefaultDestinationSyncService.DefaultDestinationStatus status = destinationSyncService.checkDefaultDestinationStatus();
        return ApiResponses.ok(status);
    }

    @GetMapping("/tasks/list")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTasks(@RequestParam Map<String, String> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.listTasks(query));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTask(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.getTask(id));
    }

    @PutMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTask(
        @PathVariable("id") Long id,
        @RequestBody Map<String, Object> payload
    ) {
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = destinationSyncService.ensureDefaultDestination();
        Map<String, Object> resolvedPayload = applyDefaultDestinationUpdatePayload(payload, snapshot);
        // Preserve the existing task's password when the update payload does not include one
        preserveExistingPassword(id, resolvedPayload, payload);
        ApiResponse<Map<String, Object>> response = ingestionClient.updateTask(id, resolvedPayload);
        if (response != null && response.getStatus() >= 200 && response.getStatus() < 300) {
            try {
                odsTableMappingSyncService.syncFromIngestionPayload(response.getData());
            } catch (RuntimeException ex) {
                String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
                auditService.auditAction(
                    "INGESTION_MAPPING_SYNC",
                    AuditStage.FAIL,
                    String.valueOf(id),
                    Map.of("summary", "同步 ODS 映射失败", "taskId", id, "operator", operator, "error", ex.getMessage())
                );
            }
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteTask(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> response = ingestionClient.deleteTask(id);
        if (response != null && response.getStatus() >= 200 && response.getStatus() < 300) {
            try {
                odsTableMappingSyncService.removeFromIngestionPayload(response.getData());
            } catch (RuntimeException ex) {
                String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
                auditService.auditAction(
                    "INGESTION_MAPPING_DELETE",
                    AuditStage.FAIL,
                    String.valueOf(id),
                    Map.of("summary", "删除 ODS 映射失败", "taskId", id, "operator", operator, "error", ex.getMessage())
                );
            }
            try {
                String taskName = null;
                Map<String, Object> data = response.getData();
                if (data != null) {
                    Object taskObj = data.get("task");
                    if (taskObj instanceof Map<?, ?> map) {
                        Object nameObj = map.get("name");
                        if (nameObj != null) {
                            taskName = String.valueOf(nameObj).trim();
                        }
                    }
                    if (!StringUtils.hasText(taskName)) {
                        Object nameObj = data.get("name");
                        if (nameObj != null) {
                            taskName = String.valueOf(nameObj).trim();
                        }
                    }
                }
                externalRunLogService.deleteIngestionRuns(taskName, id);
            } catch (RuntimeException ex) {
                String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
                auditService.auditAction(
                    "INGESTION_RUNLOG_DELETE",
                    AuditStage.FAIL,
                    String.valueOf(id),
                    Map.of("summary", "删除入湖运行日志失败", "taskId", id, "operator", operator, "error", ex.getMessage())
                );
            }
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{id}/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeTask(
        @PathVariable("id") Long id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = ingestionClient.executeTask(id);
        if (response != null && response.getStatus() >= 200 && response.getStatus() < 300) {
            String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
            try {
                externalRunLogService.recordIngestionExecution(response.getData(), activeDept);
                auditService.auditAction(
                    "INFRA_EXTERNAL_RUN_SYNC",
                    AuditStage.SUCCESS,
                    String.valueOf(id),
                    Map.of("summary", "同步入湖执行实例", "taskId", id, "operator", operator)
                );
            } catch (RuntimeException ex) {
                auditService.auditAction(
                    "INFRA_EXTERNAL_RUN_SYNC",
                    AuditStage.FAIL,
                    String.valueOf(id),
                    Map.of("summary", "同步入湖执行实例失败", "taskId", id, "operator", operator, "error", ex.getMessage())
                );
            }
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{id}/dag/rebuild")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> rebuildDag(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.rebuildDag(id));
    }

    @GetMapping("/tasks/{id}/executions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExecutions(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        ApiResponse<Map<String, Object>> response = ingestionClient.listExecutions(id, query);
        if (response != null && response.getData() != null) {
            try {
                externalRunLogService.syncIngestionExecutions(response.getData(), activeDept);
            } catch (RuntimeException ex) {
                // best-effort sync
            }
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{id}/executions/latest")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> latestExecution(
        @PathVariable("id") Long id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = ingestionClient.latestExecution(id);
        if (response != null && response.getData() != null) {
            try {
                externalRunLogService.syncIngestionExecutions(response.getData(), activeDept);
            } catch (RuntimeException ex) {
                // best-effort sync
            }
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{id}/executions/{executionId}/logs")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionLog(
        @PathVariable("id") Long id,
        @PathVariable("executionId") Long executionId,
        @RequestParam Map<String, String> params
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.getExecutionLog(id, executionId, query));
    }

    @PostMapping("/metadata/tables")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> discoverTables(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ingestionClient.discoverTables(payload));
    }

    @GetMapping("/connectors/capabilities")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> listConnectorCapabilities() {
        return ResponseEntity.ok(ingestionClient.listConnectorCapabilities());
    }

    @GetMapping("/connectors/capabilities/{connectorType}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getConnectorCapability(@PathVariable("connectorType") String connectorType) {
        return ResponseEntity.ok(ingestionClient.getConnectorCapability(connectorType));
    }

    @GetMapping("/tasks/{id}/realtime-status")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getRealtimeStatus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.getRealtimeStatus(id));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> uploadFile(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ingestionClient.uploadFile(file));
    }

    @GetMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listChangeLogs(@RequestParam Map<String, String> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.listChangeLogs(query));
    }

    @PostMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChangeLog(@RequestBody Map<String, Object> payload) {
        ApiResponse<Map<String, Object>> response = ingestionClient.createChangeLog(payload);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String taskId = payload == null ? null : String.valueOf(payload.getOrDefault("taskId", ""));
        if (response != null && response.getStatus() == 200) {
            auditService.auditAction(
                "INGESTION_CHANGELOG_CREATE",
                AuditStage.SUCCESS,
                taskId,
                Map.of("summary", "登记接入变更", "taskId", taskId, "operator", operator)
            );
        } else {
            auditService.auditAction(
                "INGESTION_CHANGELOG_CREATE",
                AuditStage.FAIL,
                taskId,
                Map.of("summary", "登记接入变更失败", "taskId", taskId, "operator", operator)
            );
        }
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> applyDefaultDestinationPayload(
        Map<String, Object> payload,
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot
    ) {
        if (payload == null) {
            return payload;
        }
        DefaultDestinationSyncService.DefaultDestinationSnapshot resolved = requireDefaultDestination(snapshot);
        Object destinationObj = payload.get("destination");
        if (!(destinationObj instanceof Map<?, ?> destinationMap)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "缺少目标端配置"
            );
        }
        Map<String, Object> overrides = extractConfig(destinationMap.get("config"));
        Map<String, Object> mergedConfig = mergeDestinationConfig(resolved.destinationConfig(), overrides);
        ensureWriterJdbcUrl(mergedConfig);
        ensureWriterTables(mergedConfig);
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("usePlatformDefault", true);
        destination.put("definitionId", resolved.destinationDefinitionId());
        destination.put("config", mergedConfig);
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        merged.put("destination", destination);
        return merged;
    }

    private Map<String, Object> applyDefaultDestinationUpdatePayload(
        Map<String, Object> payload,
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot
    ) {
        if (payload == null) {
            return payload;
        }
        DefaultDestinationSyncService.DefaultDestinationSnapshot resolved = requireDefaultDestination(snapshot);
        Map<String, Object> overrides = extractConfig(payload.get("destinationConfig"));
        Map<String, Object> mergedConfig = mergeDestinationConfig(resolved.destinationConfig(), overrides);
        ensureWriterJdbcUrl(mergedConfig);
        ensureWriterTables(mergedConfig);
        Map<String, Object> merged = new LinkedHashMap<>(payload);
        merged.put("destinationType", resolved.destinationDefinitionId());
        merged.put("destinationConfig", mergedConfig);
        return merged;
    }

    private DefaultDestinationSyncService.DefaultDestinationSnapshot requireDefaultDestination(
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot
    ) {
        if (snapshot == null || snapshot.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "未配置默认数据湖"
            );
        }
        if (snapshot.destinationConfig() == null || snapshot.destinationConfig().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "默认数据湖未配置写入器参数"
            );
        }
        if (!org.springframework.util.StringUtils.hasText(snapshot.destinationDefinitionId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "默认数据湖未配置写入器类型"
            );
        }
        return snapshot;
    }

    private Map<String, Object> extractConfig(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> output = new LinkedHashMap<>();
        map.forEach((key, val) -> output.put(String.valueOf(key), val));
        return output;
    }

    private Map<String, Object> mergeDestinationConfig(Map<String, Object> base, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overrides == null || overrides.isEmpty()) {
            return merged;
        }
        Object overrideConn = overrides.get("connection");
        if (overrideConn != null) {
            merged.put("connection", mergeConnection(merged.get("connection"), overrideConn));
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if ("connection".equals(entry.getKey())) {
                continue;
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        if (!merged.containsKey("writerType")) {
            Object candidate = merged.get("type");
            if (candidate != null) {
                merged.put("writerType", candidate);
            }
        }
        return merged;
    }

    private Object mergeConnection(Object baseConn, Object overrideConn) {
        if (overrideConn == null) {
            return baseConn;
        }
        if (baseConn instanceof List<?> baseList) {
            List<Object> result = new ArrayList<>();
            if (overrideConn instanceof Map<?, ?> overrideMap) {
                Map<String, Object> first = new LinkedHashMap<>();
                if (!baseList.isEmpty() && baseList.get(0) instanceof Map<?, ?> baseMap) {
                    baseMap.forEach((key, value) -> first.put(String.valueOf(key), value));
                }
                overrideMap.forEach((key, value) -> first.put(String.valueOf(key), value));
                result.add(first);
                for (int i = 1; i < baseList.size(); i++) {
                    result.add(baseList.get(i));
                }
                return result;
            }
            if (overrideConn instanceof List<?> overrideList) {
                if (overrideList.isEmpty()) {
                    return baseList;
                }
                Object firstOverride = overrideList.get(0);
                if (!baseList.isEmpty() && baseList.get(0) instanceof Map<?, ?> baseMap && firstOverride instanceof Map<?, ?> overrideMap) {
                    Map<String, Object> first = new LinkedHashMap<>();
                    baseMap.forEach((key, value) -> first.put(String.valueOf(key), value));
                    overrideMap.forEach((key, value) -> first.put(String.valueOf(key), value));
                    result.add(first);
                    for (int i = 1; i < overrideList.size(); i++) {
                        result.add(overrideList.get(i));
                    }
                    return result;
                }
                return overrideList;
            }
            return overrideConn;
        }
        if (baseConn instanceof Map<?, ?> baseMap && overrideConn instanceof Map<?, ?> overrideMap) {
            Map<String, Object> merged = new LinkedHashMap<>();
            baseMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            overrideMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            return merged;
        }
        return overrideConn;
    }

    /**
     * When the update payload does not include a password, fetch the existing
     * task and keep its stored password instead of falling back to the default
     * data-lake template value.
     */
    private void preserveExistingPassword(Long id, Map<String, Object> resolvedPayload, Map<String, Object> originalPayload) {
        Map<String, Object> userDestConfig = extractConfig(originalPayload.get("destinationConfig"));
        boolean userProvidedPassword = userDestConfig.containsKey("password")
            && StringUtils.hasText(String.valueOf(userDestConfig.get("password")));
        if (userProvidedPassword) {
            return;
        }
        Object destCfgObj = resolvedPayload.get("destinationConfig");
        if (!(destCfgObj instanceof Map<?, ?> destCfg)) {
            return;
        }
        try {
            ApiResponse<Map<String, Object>> existing = ingestionClient.getTask(id);
            if (existing == null || existing.getData() == null) {
                return;
            }
            Map<String, Object> existingData = existing.getData();
            Object existingDestCfg = existingData.get("destinationConfig");
            String existingPassword = null;
            if (existingDestCfg instanceof Map<?, ?> m) {
                Object pw = m.get("password");
                existingPassword = pw == null ? null : String.valueOf(pw).trim();
            }
            if (StringUtils.hasText(existingPassword)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mutable = (Map<String, Object>) destCfg;
                mutable.put("password", existingPassword);
            }
        } catch (Exception ex) {
            // best-effort: if we can't fetch the task, proceed with whatever we have
        }
    }

    private void ensureWriterTables(Map<String, Object> config) {
        List<String> tables = extractWriterTables(config);
        if (!tables.isEmpty() || config == null) {
            return;
        }
        // 当任务选择“全部表”时，允许先用占位符，后续由 ingestion 按实际表清单替换
        String placeholder = "${table}";
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> connMap = (Map<String, Object>) map;
            connMap.putIfAbsent("table", java.util.List.of(placeholder));
            return;
        }
        if (connection instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> connMap = (Map<String, Object>) map;
            connMap.putIfAbsent("table", java.util.List.of(placeholder));
            java.util.ArrayList<Object> next = new java.util.ArrayList<>(list);
            next.set(0, connMap);
            config.put("connection", next);
            return;
        }
        config.put("table", java.util.List.of(placeholder));
    }

    private void ensureWriterJdbcUrl(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        Object jdbcUrl = firstNonBlank(
            config.get("jdbcUrl"),
            config.get("jdbc_url"),
            config.get("url"),
            config.get("jdbc"),
            config.get("jdbcURL")
        );
        if (jdbcUrl != null) {
            config.put("jdbcUrl", jdbcUrl);
        }
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> connMap = (Map<String, Object>) map;
            Object connJdbc = firstNonBlank(
                connMap.get("jdbcUrl"),
                connMap.get("jdbc_url"),
                connMap.get("url"),
                connMap.get("jdbc"),
                connMap.get("jdbcURL"),
                jdbcUrl
            );
            if (connJdbc != null) {
                connMap.put("jdbcUrl", connJdbc);
            }
            return;
        }
        if (connection instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> connMap = new LinkedHashMap<>((Map<String, Object>) map);
            Object connJdbc = firstNonBlank(
                connMap.get("jdbcUrl"),
                connMap.get("jdbc_url"),
                connMap.get("url"),
                connMap.get("jdbc"),
                connMap.get("jdbcURL"),
                jdbcUrl
            );
            if (connJdbc != null) {
                connMap.put("jdbcUrl", connJdbc);
            }
            java.util.ArrayList<Object> next = new java.util.ArrayList<>(list);
            next.set(0, connMap);
            config.put("connection", next);
        }
    }

    private Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof java.util.List<?> list) {
                if (!list.isEmpty()) {
                    return value;
                }
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private List<String> extractWriterTables(Map<String, Object> config) {
        List<String> tables = new ArrayList<>();
        if (config == null || config.isEmpty()) {
            return tables;
        }
        tables.addAll(asStringList(config.get("table")));
        tables.addAll(asStringList(config.get("tables")));
        Object connection = config.get("connection");
        if (connection instanceof Map<?, ?> map) {
            tables.addAll(asStringList(map.get("table")));
            tables.addAll(asStringList(map.get("tables")));
        } else if (connection instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                tables.addAll(asStringList(map.get("table")));
                tables.addAll(asStringList(map.get("tables")));
            }
        }
        return tables;
    }

    private List<String> asStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value == null) {
            return list;
        }
        if (value instanceof List<?> items) {
            for (Object item : items) {
                String text = asText(item);
                if (text != null) {
                    list.add(text);
                }
            }
            return list;
        }
        String text = asText(value);
        if (text != null) {
            list.add(text);
        }
        return list;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

}
