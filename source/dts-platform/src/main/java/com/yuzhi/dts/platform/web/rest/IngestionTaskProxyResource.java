package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = destinationSyncService.ensureDefaultDestination();
        Map<String, Object> resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
        ApiResponse<Map<String, Object>> response = ingestionClient.createIngestionTask(resolvedPayload);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String taskName = payload == null ? null : String.valueOf(payload.getOrDefault("name", ""));
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
        ApiResponse<Map<String, Object>> response = ingestionClient.updateTask(id, payload);
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
        return ResponseEntity.ok(ingestionClient.deleteTask(id));
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

    @PostMapping("/metadata/tables")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> discoverTables(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ingestionClient.discoverTables(payload));
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
        if (payload == null || snapshot == null || snapshot.isEmpty()) {
            return payload;
        }
        Object destinationObj = payload.get("destination");
        if (!(destinationObj instanceof Map<?, ?> destinationMap)) {
            return payload;
        }
        Map<String, Object> destination = new java.util.LinkedHashMap<>();
        destinationMap.forEach((key, value) -> destination.put(String.valueOf(key), value));
        Object useDefaultObj = destination.get("usePlatformDefault");
        if (Boolean.FALSE.equals(asBoolean(useDefaultObj))) {
            return payload;
        }
        boolean hasDefinition = hasText(destination.get("definitionId"));
        boolean hasConfig = destination.get("config") instanceof Map<?, ?> config && !config.isEmpty();
        if (!hasDefinition && hasText(snapshot.destinationDefinitionId())) {
            destination.put("definitionId", snapshot.destinationDefinitionId());
        }
        if (!hasConfig && snapshot.destinationConfig() != null && !snapshot.destinationConfig().isEmpty()) {
            destination.put("config", snapshot.destinationConfig());
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(payload);
        merged.put("destination", destination);
        return merged;
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }
}
