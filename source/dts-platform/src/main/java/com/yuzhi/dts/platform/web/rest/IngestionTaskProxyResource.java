package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionTaskProxyResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final IngestionServiceClient ingestionClient;
    private final DefaultDestinationSyncService destinationSyncService;
    private final AuditService auditService;

    public IngestionTaskProxyResource(
        IngestionServiceClient ingestionClient,
        DefaultDestinationSyncService destinationSyncService,
        AuditService auditService
    ) {
        this.ingestionClient = ingestionClient;
        this.destinationSyncService = destinationSyncService;
        this.auditService = auditService;
    }

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = destinationSyncService.ensureDefaultDestination();
        Map<String, Object> resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
        ApiResponse<Map<String, Object>> response = ingestionClient.createIngestionTask(resolvedPayload);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        String taskName = payload == null ? null : String.valueOf(payload.getOrDefault("name", ""));
        if (response != null && response.getStatus() == 200) {
            auditService.auditAction(
                "INGESTION_TASK_CREATE",
                AuditStage.SUCCESS,
                taskName,
                Map.of("summary", "创建入湖任务", "name", taskName, "operator", operator)
            );
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
        return ResponseEntity.ok(ingestionClient.updateTask(id, payload));
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteTask(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.deleteTask(id));
    }

    @PostMapping("/tasks/{id}/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeTask(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.executeTask(id));
    }

    @GetMapping("/tasks/{id}/executions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExecutions(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.listExecutions(id, query));
    }

    @GetMapping("/tasks/{id}/executions/latest")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> latestExecution(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.latestExecution(id));
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
