package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationException;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ingestion.IngestionAccessDecisionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionClassificationAdmissionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
    private final InfraDataSourceRepository dataSourceRepository;
    private final CatalogClassificationService classificationService;
    private final IngestionClassificationAdmissionService classificationAdmissionService;
    private final IngestionAccessDecisionService accessDecisionService;
    private final ClassificationUtils classificationUtils;
    private final ObjectMapper objectMapper;

    public IngestionTaskProxyResource(
        IngestionServiceClient ingestionClient,
        DefaultDestinationSyncService destinationSyncService,
        OdsTableMappingSyncService odsTableMappingSyncService,
        AuditService auditService,
        ExternalRunLogService externalRunLogService,
        InfraDataSourceRepository dataSourceRepository,
        CatalogClassificationService classificationService,
        IngestionClassificationAdmissionService classificationAdmissionService,
        IngestionAccessDecisionService accessDecisionService,
        ClassificationUtils classificationUtils,
        ObjectMapper objectMapper
    ) {
        this.ingestionClient = ingestionClient;
        this.destinationSyncService = destinationSyncService;
        this.odsTableMappingSyncService = odsTableMappingSyncService;
        this.auditService = auditService;
        this.externalRunLogService = externalRunLogService;
        this.dataSourceRepository = dataSourceRepository;
        this.classificationService = classificationService;
        this.classificationAdmissionService = classificationAdmissionService;
        this.accessDecisionService = accessDecisionService;
        this.classificationUtils = classificationUtils;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/templates")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<?> listTemplates() {
        return ResponseEntity.ok(sanitizeJsonResponse(ingestionClient.listTemplates()));
    }

    @GetMapping("/access/default-policy")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getAccessDefaultPolicy() {
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getAccessDefaultPolicy()));
    }

    @PostMapping("/templates/{templateId}/render")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<?> renderTemplate(@PathVariable String templateId, @RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.ok(sanitizeJsonResponse(ingestionClient.renderTemplate(templateId, payload)));
    }

    @PostMapping("/tasks")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> payload) {
        payload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(payload);
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
        String resourceId = StringUtils.hasText(taskName) ? taskName : "create";
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        putAuditText(auditDetails, "taskName", taskName);
        auditDetails.put("draft", draft);
        UUID beginAuditReceipt = strictAudit(
            "INGESTION_TASK_CREATE",
            AuditStage.BEGIN,
            resourceId,
            auditOperationId,
            "开始创建接入任务",
            auditDetails
        );
        boolean terminalAuditWritten = false;
        try {
            Map<String, Object> resolvedPayload = payload;
            if (!draft || usesPlatformDefaultDestination(payload)) {
                DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = resolveDestinationSnapshot(payload);
                resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
            }
            resolvedPayload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(resolvedPayload);
            accessDecisionService.requireCreateOrUpdateAccess(resolvedPayload, true);
            resolvedPayload = attachCurrentSourceClassificationSeal(resolvedPayload, !draft);
            ApiResponse<Map<String, Object>> response = ingestionClient.createIngestionTask(resolvedPayload);
            Map<String, Object> outcome = safeAuditDetails(auditDetails);
            if (response != null) {
                outcome.put("downstreamStatus", response.getStatus());
                putAuditText(outcome, "downstreamCode", response.getCode());
                Map<String, Object> responseData = response.getData();
                if (responseData != null) {
                    putAuditText(outcome, "taskId", responseData.get("id"));
                }
            }
            boolean success = isSuccessful(response);
            terminalStrictAudit(
                "INGESTION_TASK_CREATE",
                success ? AuditStage.SUCCESS : AuditStage.FAIL,
                resourceId,
                auditOperationId,
                beginAuditReceipt,
                success ? "创建接入任务成功" : "创建接入任务失败",
                outcome
            );
            terminalAuditWritten = true;
            if (success) {
                try {
                    odsTableMappingSyncService.syncFromIngestionPayload(response.getData());
                } catch (RuntimeException ex) {
                    auditService.auditAction(
                        "INGESTION_MAPPING_SYNC",
                        AuditStage.FAIL,
                        resourceId,
                        Map.of(
                            "summary", "同步 ODS 映射失败",
                            "name", resourceId,
                            "operator", SecurityUtils.getCurrentUserLogin().orElse("system"),
                            "errorType", ex.getClass().getSimpleName()
                        )
                    );
                }
            }
            return accessDecisionService.sanitizeResponse(response);
        } catch (RuntimeException failure) {
            if (!(failure instanceof AuditFinalizationException) && !terminalAuditWritten) {
                Map<String, Object> failed = safeAuditDetails(auditDetails);
                failed.put("errorType", failure.getClass().getSimpleName());
                failed.put("operationOutcome", "UNKNOWN");
                failed.put("doNotRetry", true);
                terminalStrictAudit(
                    "INGESTION_TASK_CREATE",
                    AuditStage.FAIL,
                    resourceId,
                    auditOperationId,
                    beginAuditReceipt,
                    "创建接入任务异常",
                    failed
                );
            }
            throw failure;
        }
    }

    @GetMapping("/default-destination")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<DefaultDestinationSyncService.DefaultDestinationStatus> getDefaultDestinationStatus() {
        DefaultDestinationSyncService.DefaultDestinationStatus status = destinationSyncService.checkDefaultDestinationStatus();
        return ApiResponses.ok(status);
    }

    @GetMapping("/tasks/list")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTasks(@RequestParam MultiValueMap<String, String> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((key, values) -> {
                if (values == null || values.isEmpty()) {
                    return;
                }
                query.put(key, values.size() == 1 ? values.get(0) : List.copyOf(values));
            });
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(accessDecisionService.listVisibleTasks(query)));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTask(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> response = ingestionClient.getTask(id);
        if (response != null && response.getStatus() >= 200 && response.getStatus() < 300 && response.getData() != null) {
            Map<String, Object> canonicalTask = accessDecisionService.canonicalizeTaskPayloadIdentifiers(response.getData());
            accessDecisionService.requireTaskPayloadAccess(canonicalTask, false);
            response.setData(canonicalTask);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @GetMapping("/tasks/{id}/revisions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getTaskRevisions(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, false);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getTaskRevisions(id)));
    }

    @GetMapping("/tasks/{id}/effective-config")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getTaskEffectiveConfig(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, false);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getTaskEffectiveConfig(id)));
    }

    @PutMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTask(
        @PathVariable("id") Long id,
        @RequestBody Map<String, Object> payload
    ) {
        String resourceId = String.valueOf(id);
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> auditDetails = Map.of("taskId", id);
        UUID beginAuditReceipt = strictAudit(
            "INGESTION_TASK_UPDATE",
            AuditStage.BEGIN,
            resourceId,
            auditOperationId,
            "开始更新接入任务",
            auditDetails
        );
        boolean terminalAuditWritten = false;
        try {
            payload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(payload);
            Map<String, Object> existingTask = accessDecisionService.requireTaskAuthorizationAccess(id, true);
            DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = resolveDestinationSnapshot(payload);
            Map<String, Object> resolvedPayload = applyDefaultDestinationUpdatePayload(payload, snapshot);
            resolvedPayload.put(
                "destinationConfig",
                accessDecisionService.retainExplicitSecrets(
                    resolvedPayload.get("destinationConfig"),
                    payload.get("destinationConfig")
                )
            );
            resolvedPayload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(resolvedPayload);
            Map<String, Object> accessPayload = new LinkedHashMap<>(existingTask);
            if (resolvedPayload.containsKey("sourceDataSourceId")) {
                if (!resolvedPayload.containsKey("source")) {
                    accessPayload.remove("source");
                }
                if (!resolvedPayload.containsKey("sourceConfig")) {
                    accessPayload.remove("sourceConfig");
                }
            }
            accessPayload.putAll(resolvedPayload);
            accessPayload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(accessPayload);
            accessDecisionService.requireCreateOrUpdateAccess(accessPayload, true);
            resolvedPayload = attachCurrentSourceClassificationSeal(resolvedPayload, false);
            ApiResponse<Map<String, Object>> response = ingestionClient.updateTask(id, resolvedPayload);
            Map<String, Object> outcome = new LinkedHashMap<>(auditDetails);
            if (response != null) {
                outcome.put("downstreamStatus", response.getStatus());
                putAuditText(outcome, "downstreamCode", response.getCode());
            }
            boolean success = isSuccessful(response);
            terminalStrictAudit(
                "INGESTION_TASK_UPDATE",
                success ? AuditStage.SUCCESS : AuditStage.FAIL,
                resourceId,
                auditOperationId,
                beginAuditReceipt,
                success ? "更新接入任务成功" : "更新接入任务失败",
                outcome
            );
            terminalAuditWritten = true;
            if (success) {
                try {
                    odsTableMappingSyncService.syncFromIngestionPayload(response.getData());
                } catch (RuntimeException ex) {
                    auditService.auditAction(
                        "INGESTION_MAPPING_SYNC",
                        AuditStage.FAIL,
                        resourceId,
                        Map.of(
                            "summary", "同步 ODS 映射失败",
                            "taskId", id,
                            "operator", SecurityUtils.getCurrentUserLogin().orElse("system"),
                            "errorType", ex.getClass().getSimpleName()
                        )
                    );
                }
            }
            return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
        } catch (RuntimeException failure) {
            if (!(failure instanceof AuditFinalizationException) && !terminalAuditWritten) {
                terminalStrictAudit(
                    "INGESTION_TASK_UPDATE",
                    AuditStage.FAIL,
                    resourceId,
                    auditOperationId,
                    beginAuditReceipt,
                    "更新接入任务异常",
                    Map.of(
                        "taskId", id,
                        "errorType", failure.getClass().getSimpleName(),
                        "operationOutcome", "UNKNOWN",
                        "doNotRetry", true
                    )
                );
            }
            throw failure;
        }
    }

    @PostMapping("/tasks/{id}/admit")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> admitTask(@PathVariable("id") Long id) {
        String resourceId = String.valueOf(id);
        String auditOperationId = UUID.randomUUID().toString();
        UUID beginAuditReceipt = strictAudit(
            "INGESTION_TASK_ADMIT",
            AuditStage.BEGIN,
            resourceId,
            auditOperationId,
            "开始确认任务密级并准入",
            Map.of("taskId", id)
        );
        boolean terminalAuditWritten = false;
        try {
            ApiResponse<Map<String, Object>> existing = ingestionClient.getTask(id);
            if (existing == null || existing.getStatus() < 200 || existing.getStatus() >= 300 || existing.getData() == null) {
                Map<String, Object> unavailable = new LinkedHashMap<>();
                unavailable.put("taskId", id);
                unavailable.put("downstreamStatus", existing == null ? "UNAVAILABLE" : existing.getStatus());
                terminalStrictAudit(
                    "INGESTION_TASK_ADMIT",
                    AuditStage.FAIL,
                    resourceId,
                    auditOperationId,
                    beginAuditReceipt,
                    "任务密级准入前置读取失败",
                    unavailable
                );
                terminalAuditWritten = true;
                ApiResponse<Map<String, Object>> safeResponse = existing == null
                    ? new ApiResponse<>(503, "接入服务暂不可用", null)
                    : accessDecisionService.sanitizeResponse(existing);
                return ResponseEntity.ok(safeResponse);
            }
            Map<String, Object> canonicalTask = accessDecisionService.canonicalizeTaskPayloadIdentifiers(existing.getData());
            existing.setData(canonicalTask);
            accessDecisionService.requireTaskPayloadAccess(canonicalTask, true);

            Map<String, Object> sealedTask = attachCurrentSourceClassificationSeal(canonicalTask, true);
            Map<String, Object> admission = new LinkedHashMap<>();
            admission.put("classificationSeal", sealedTask.get("classificationSeal"));
            if (sealedTask.get("fieldClassifications") != null) {
                admission.put("fieldClassifications", sealedTask.get("fieldClassifications"));
            }
            ApiResponse<Map<String, Object>> response = ingestionClient.admitTask(id, admission);
            boolean success = isSuccessful(response);
            Map<String, Object> evidence = classificationAuditEvidence(sealedTask);
            evidence.put("taskId", id);
            if (response != null) {
                evidence.put("downstreamStatus", response.getStatus());
                putAuditText(evidence, "downstreamCode", response.getCode());
                if (response.getData() != null) {
                    classificationAuditEvidence(response.getData()).forEach(evidence::putIfAbsent);
                }
            }
            terminalStrictAudit(
                "INGESTION_TASK_ADMIT",
                success ? AuditStage.SUCCESS : AuditStage.FAIL,
                resourceId,
                auditOperationId,
                beginAuditReceipt,
                success ? "确认任务密级并准入成功" : "确认任务密级并准入失败",
                evidence
            );
            terminalAuditWritten = true;
            return ResponseEntity.ok(
                accessDecisionService.sanitizeResponse(
                    response == null ? new ApiResponse<>(503, "接入服务暂不可用", null) : response
                )
            );
        } catch (RuntimeException failure) {
            if (!(failure instanceof AuditFinalizationException) && !terminalAuditWritten) {
                terminalStrictAudit(
                    "INGESTION_TASK_ADMIT",
                    AuditStage.FAIL,
                    resourceId,
                    auditOperationId,
                    beginAuditReceipt,
                    "确认任务密级并准入异常",
                    Map.of(
                        "taskId", id,
                        "errorType", failure.getClass().getSimpleName(),
                        "operationOutcome", "UNKNOWN",
                        "doNotRetry", true
                    )
                );
            }
            throw failure;
        }
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteTask(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_TASK_DELETE",
            String.valueOf(id),
            "开始删除接入任务",
            "删除接入任务成功",
            "删除接入任务失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.deleteTask(id);
            }
        );
        auditRuntimeArtifactCleanup(id, response);
        // Deleting a plan is a logical retirement. ODS mappings, lineage and
        // external run logs remain available as audit and traceability evidence.
        return buildAsyncProxyResponse(response);
    }

    private void auditRuntimeArtifactCleanup(Long taskId, ApiResponse<Map<String, Object>> response) {
        if (!isSuccessful(response) || response.getData() == null) {
            return;
        }
        Object cleanupValue = response.getData().get("runtimeArtifactCleanup");
        Map<String, Object> cleanup = mapValue(cleanupValue);
        boolean evidenceMissing = cleanup.isEmpty();
        boolean retryable = evidenceMissing || Boolean.parseBoolean(String.valueOf(cleanup.getOrDefault("retryable", false)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", retryable ? "接入任务运行制品清理未完成" : "接入任务运行制品清理完成");
        payload.put("taskId", taskId);
        payload.put("subOperation", "RUNTIME_ARTIFACT_CLEANUP");
        payload.put("operationOutcome", retryable ? "PARTIAL" : "SUCCESS");
        payload.put("retryable", retryable);
        payload.put("evidenceMissing", evidenceMissing);
        copyCleanupAuditValue(cleanup, payload, "addaxCleaned");
        copyCleanupAuditValue(cleanup, payload, "airflowCleaned");
        copyCleanupAuditValue(cleanup, payload, "addaxErrorType");
        copyCleanupAuditValue(cleanup, payload, "airflowErrorType");
        auditService.auditActionStrict(
            "INGESTION_TASK_DELETE",
            retryable ? AuditStage.FAIL : AuditStage.SUCCESS,
            String.valueOf(taskId),
            Map.copyOf(payload)
        );
    }

    private void copyCleanupAuditValue(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    @PostMapping("/tasks/{id}/execute")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeTask(
        @PathVariable("id") Long id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_TASK_EXECUTE",
            String.valueOf(id),
            "开始执行接入任务",
            "接入任务执行请求已提交",
            "接入任务执行请求失败",
            Map.of("taskId", id, "mode", "SYNC"),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.executeTask(id);
            }
        );
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
                    Map.of("summary", "同步入湖执行实例失败", "taskId", id, "operator", operator, "errorType", ex.getClass().getSimpleName())
                );
            }
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PostMapping("/tasks/{id}/execute/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeTaskAsync(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_TASK_EXECUTE",
            String.valueOf(id),
            "开始异步执行接入任务",
            "接入任务异步执行请求已提交",
            "接入任务异步执行请求失败",
            Map.of("taskId", id, "mode", "ASYNC"),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.executeTaskAsync(id);
            }
        );
        return buildAsyncProxyResponse(response);
    }

    @PostMapping("/tasks/{id}/backfill")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillTask(
        @PathVariable("id") Long id,
        @RequestBody Map<String, Object> payload
    ) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_BACKFILL_RUN",
            String.valueOf(id),
            "开始提交接入补数任务",
            "接入补数任务已提交",
            "接入补数任务提交失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.backfillTask(id, payload);
            }
        );
        return buildAsyncProxyResponse(response);
    }

    @PostMapping("/tasks/{id}/executions/{executionId}/retry")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryExecution(
        @PathVariable("id") Long id,
        @PathVariable("executionId") Long executionId,
        @RequestParam(value = "mode", required = false, defaultValue = "FAILED_ONLY") String mode,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_EXECUTION_RETRY",
            String.valueOf(executionId),
            "开始重试接入执行",
            "接入执行重试请求已提交",
            "接入执行重试请求失败",
            Map.of("taskId", id, "executionId", executionId, "mode", mode),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.retryExecution(id, executionId, Map.of("mode", mode));
            }
        );
        if (response != null && response.getData() != null) {
            try {
                externalRunLogService.recordIngestionExecution(response.getData(), activeDept);
            } catch (RuntimeException ex) {
                // best-effort sync
            }
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PostMapping("/tasks/{id}/executions/{executionId}/retry/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryExecutionAsync(
        @PathVariable("id") Long id,
        @PathVariable("executionId") Long executionId,
        @RequestParam(value = "mode", required = false, defaultValue = "FAILED_ONLY") String mode
    ) {
        ApiResponse<Map<String, Object>> response = auditedIngestionCall(
            "INGESTION_EXECUTION_RETRY",
            String.valueOf(executionId),
            "开始异步重试接入执行",
            "接入执行异步重试请求已提交",
            "接入执行异步重试请求失败",
            Map.of("taskId", id, "executionId", executionId, "mode", mode),
            () -> {
                accessDecisionService.requireTaskAccess(id, true);
                return ingestionClient.retryExecutionAsync(id, executionId, Map.of("mode", mode));
            }
        );
        return buildAsyncProxyResponse(response);
    }

    @PostMapping("/tasks/{id}/dag/rebuild")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> rebuildDag(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, true);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.rebuildDag(id)));
    }

    @GetMapping("/tasks/{id}/executions")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExecutions(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        accessDecisionService.requireTaskAccess(id, false);
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
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @GetMapping("/tasks/{id}/executions/latest")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> latestExecution(
        @PathVariable("id") Long id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        accessDecisionService.requireTaskAccess(id, false);
        ApiResponse<Map<String, Object>> response = ingestionClient.latestExecution(id);
        if (response != null && response.getData() != null) {
            try {
                externalRunLogService.syncIngestionExecutions(response.getData(), activeDept);
            } catch (RuntimeException ex) {
                // best-effort sync
            }
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @GetMapping("/tasks/{id}/executions/{executionId}/logs")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getExecutionLog(
        @PathVariable("id") Long id,
        @PathVariable("executionId") Long executionId,
        @RequestParam Map<String, String> params
    ) {
        accessDecisionService.requireTaskAccess(id, false);
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getExecutionLog(id, executionId, query)));
    }

    @PostMapping("/metadata/tables")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> discoverTables(@RequestBody Map<String, Object> payload) {
        payload = accessDecisionService.canonicalizeTaskPayloadIdentifiers(payload);
        accessDecisionService.requireDiscoveryAccess(payload);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.discoverTables(payload)));
    }

    @GetMapping("/connectors/capabilities")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> listConnectorCapabilities() {
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.listConnectorCapabilities()));
    }

    @GetMapping("/connectors/capabilities/{connectorType}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getConnectorCapability(@PathVariable("connectorType") String connectorType) {
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getConnectorCapability(connectorType)));
    }

    @GetMapping("/api/contract")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getApiConnectorContract() {
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getApiConnectorContract()));
    }

    @GetMapping("/api/auth-providers")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> listApiAuthProviders() {
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.listApiAuthProviders()));
    }

    @PostMapping("/api/test-connection")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> testApiConnection(@RequestBody Map<String, Object> payload) {
        Map<String, Object> details = new LinkedHashMap<>();
        putAuditText(details, "dataSourceId", payload == null ? null : payload.get("dataSourceId"));
        String resourceId = String.valueOf(details.getOrDefault("dataSourceId", "api-draft"));
        ApiResponse<Object> response = auditedIngestionCall(
            "FOUNDATION_DATASOURCE_TEST",
            resourceId,
            "开始测试 API 数据连接",
            "API 数据连接测试成功",
            "API 数据连接测试失败",
            details,
            () -> ingestionClient.testApiConnection(accessDecisionService.normalizeApiConnectionTest(payload))
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @GetMapping("/tasks/{id}/realtime-status")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getRealtimeStatus(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, false);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getRealtimeStatus(id)));
    }

    @PostMapping("/tasks/{id}/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> parseStagingFile(@PathVariable("id") Long id) {
        ApiResponse<Object> response = auditedIngestionCall(
            "INGESTION_FILE_PARSE",
            String.valueOf(id),
            "开始解析任务文件",
            "任务文件解析完成",
            "任务文件解析失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, false);
                return ingestionClient.parseStagingFile(id);
            }
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PostMapping("/tasks/{id}/pre-check")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> preCheckStaging(@PathVariable("id") Long id) {
        ApiResponse<Object> response = auditedIngestionCall(
            "INGESTION_FILE_PRECHECK",
            String.valueOf(id),
            "开始执行文件落地前预检",
            "文件落地前预检完成",
            "文件落地前预检失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, false);
                return ingestionClient.preCheckStaging(id);
            }
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PutMapping("/tasks/{id}/staging/{rowNum}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> updateStagingCell(
        @PathVariable("id") Long id,
        @PathVariable("rowNum") Integer rowNum,
        @RequestBody Map<String, Object> payload
    ) {
        ApiResponse<Object> response = auditedIngestionCall(
            "INGESTION_STAGING_CELL_UPDATE",
            String.valueOf(id),
            "开始修订文件暂存数据",
            "文件暂存数据修订完成",
            "文件暂存数据修订失败",
            Map.of("taskId", id, "rowNum", rowNum),
            () -> {
                accessDecisionService.requireTaskAccess(id, false);
                return ingestionClient.updateStagingCell(id, rowNum, payload);
            }
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PostMapping("/tasks/{id}/re-check")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> reCheckStaging(@PathVariable("id") Long id) {
        ApiResponse<Object> response = auditedIngestionCall(
            "INGESTION_FILE_RECHECK",
            String.valueOf(id),
            "开始重新检查文件暂存数据",
            "文件暂存数据重新检查完成",
            "文件暂存数据重新检查失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, false);
                return ingestionClient.reCheckStaging(id);
            }
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> submitStaging(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, false);
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.submitStaging(id)));
    }

    @DeleteMapping("/tasks/{id}/staging")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> dropStaging(@PathVariable("id") Long id) {
        ApiResponse<Object> response = auditedIngestionCall(
            "INGESTION_STAGING_CLEAR",
            String.valueOf(id),
            "开始清除文件暂存数据",
            "文件暂存数据清除完成",
            "文件暂存数据清除失败",
            Map.of("taskId", id),
            () -> {
                accessDecisionService.requireTaskAccess(id, false);
                return ingestionClient.dropStaging(id);
            }
        );
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    @GetMapping("/tasks/{id}/staging")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getStagingData(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params
    ) {
        accessDecisionService.requireTaskAccess(id, false);
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getStagingData(id, query)));
    }

    @GetMapping("/tasks/{id}/staging/errors/summary")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getStagingErrorSummary(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params
    ) {
        accessDecisionService.requireTaskAccess(id, false);
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getStagingErrorSummary(id, query)));
    }

    @GetMapping("/tasks/{id}/staging/errors/download")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<byte[]> downloadStagingErrors(@PathVariable("id") Long id) {
        accessDecisionService.requireTaskAccess(id, false);
        ResponseEntity<byte[]> response = ingestionClient.downloadStagingErrors(id);
        return ResponseEntity
            .status(response.getStatusCode())
            .headers(response.getHeaders())
            .body(response.getBody());
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> uploadFile(@RequestPart("file") MultipartFile file) {
        return ResponseEntity
            .status(HttpStatus.GONE)
            .body(new ApiResponse<>(410, "旧文件上传接口已停用，请使用带显式密级的 upload-and-parse", null));
    }

    public record FileParseRequest(String fileId, Integer previewLimit, Integer sheetIndex, String sheetName, String originalName) {}

    @PostMapping(value = "/files/upload-and-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> uploadAndParseFile(
        @RequestPart("file") MultipartFile file,
        @RequestParam("classification") String classification,
        @RequestParam(value = "previewLimit", required = false) Integer previewLimit,
        @RequestParam(value = "sheetIndex", required = false) Integer sheetIndex,
        @RequestParam(value = "sheetName", required = false) String sheetName
    ) {
        String declaredLevel;
        try {
            declaredLevel = SecurityLevelCatalog.requireDataLevel(classification).code();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必须明确选择文件密级", ex);
        }
        String userMaxLevel = classificationUtils
            .getCurrentUserExplicitMaxLevel()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前用户未配置密级，禁止上传文件"));
        if (!SecurityLevelCatalog.isDataAtLeast(userMaxLevel, declaredLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "所选文件密级超出当前用户密级");
        }
        String auditOperationId = UUID.randomUUID().toString();
        Map<String, Object> uploadEvidence = new LinkedHashMap<>();
        uploadEvidence.put("declaredLevel", declaredLevel);
        uploadEvidence.put("fileSize", file.getSize());
        putAuditText(uploadEvidence, "contentType", file.getContentType());
        if (previewLimit != null) {
            uploadEvidence.put("previewLimit", previewLimit);
        }
        if (sheetIndex != null) {
            uploadEvidence.put("sheetIndex", sheetIndex);
        }
        UUID beginAuditReceipt = strictAudit(
            "INGESTION_FILE_CLASSIFICATION_SEAL",
            AuditStage.BEGIN,
            auditOperationId,
            auditOperationId,
            "开始上传、解析并封存文件密级",
            uploadEvidence
        );
        boolean terminalAuditWritten = false;
        try {
            ApiResponse<Object> response = ingestionClient.uploadAndParse(file, previewLimit, sheetIndex, sheetName);
            if (response == null) {
                terminalStrictAudit(
                    "INGESTION_FILE_CLASSIFICATION_SEAL",
                    AuditStage.FAIL,
                    auditOperationId,
                    auditOperationId,
                    beginAuditReceipt,
                    "文件上传解析服务暂不可用",
                    uploadEvidence
                );
                terminalAuditWritten = true;
                return ResponseEntity.ok(new ApiResponse<>(503, "接入服务暂不可用", null));
            }
            if (isSuccessful(response)) {
                Object sealed = classificationAdmissionService.sealEncryptedUpload(response.getData(), declaredLevel);
                response.setData(accessDecisionService.removeInternalFilePaths(sealed));
                Map<String, Object> outcome = safeAuditDetails(uploadEvidence);
                classificationAuditEvidence(mapValue(sealed)).forEach(outcome::putIfAbsent);
                outcome.put("downstreamStatus", response.getStatus());
                putAuditText(outcome, "downstreamCode", response.getCode());
                terminalStrictAudit(
                    "INGESTION_FILE_CLASSIFICATION_SEAL",
                    AuditStage.SUCCESS,
                    auditOperationId,
                    auditOperationId,
                    beginAuditReceipt,
                    "文件上传解析及密级封存成功",
                    outcome
                );
                terminalAuditWritten = true;
            } else {
                Map<String, Object> outcome = safeAuditDetails(uploadEvidence);
                outcome.put("downstreamStatus", response.getStatus());
                putAuditText(outcome, "downstreamCode", response.getCode());
                terminalStrictAudit(
                    "INGESTION_FILE_CLASSIFICATION_SEAL",
                    AuditStage.FAIL,
                    auditOperationId,
                    auditOperationId,
                    beginAuditReceipt,
                    "文件上传解析失败，未生成密级封存",
                    outcome
                );
                terminalAuditWritten = true;
            }
            return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
        } catch (CatalogClassificationException failure) {
            if (!terminalAuditWritten) {
                terminalStrictAudit(
                    "INGESTION_FILE_CLASSIFICATION_SEAL",
                    AuditStage.FAIL,
                    auditOperationId,
                    auditOperationId,
                    beginAuditReceipt,
                    "文件密级封存失败",
                    Map.of(
                        "declaredLevel", declaredLevel,
                        "errorType", failure.getClass().getSimpleName(),
                        "operationOutcome", "UNKNOWN",
                        "doNotRetry", true
                    )
                );
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failure.getMessage(), failure);
        } catch (RuntimeException failure) {
            if (!(failure instanceof AuditFinalizationException) && !terminalAuditWritten) {
                terminalStrictAudit(
                    "INGESTION_FILE_CLASSIFICATION_SEAL",
                    AuditStage.FAIL,
                    auditOperationId,
                    auditOperationId,
                    beginAuditReceipt,
                    "文件上传解析及密级封存异常",
                    Map.of(
                        "declaredLevel", declaredLevel,
                        "errorType", failure.getClass().getSimpleName(),
                        "operationOutcome", "UNKNOWN",
                        "doNotRetry", true
                    )
                );
            }
            throw failure;
        }
    }

    @PostMapping("/files/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> parseUploadedFile(@RequestBody FileParseRequest request) {
        return ResponseEntity
            .status(HttpStatus.GONE)
            .body(new ApiResponse<>(410, "旧文件解析接口已停用，请重新上传并明确选择文件密级", null));
    }

    @GetMapping("/tasks/executions/observability")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getExecutionsObservability(@RequestParam Map<String, String> params) {
        accessDecisionService.requireAggregateScope(params, "查看全局接入执行观测");
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getExecutionsObservability(query)));
    }

    @GetMapping("/tasks/executions/governance-overview")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getGovernanceOverview(@RequestParam Map<String, String> params) {
        accessDecisionService.requireAggregateScope(params, "查看全局接入治理概览");
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.getGovernanceOverview(query)));
    }

    @GetMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listChangeLogs(@RequestParam Map<String, String> params) {
        Long taskId = parseLong(params == null ? null : params.get("taskId"));
        if (taskId == null) {
            accessDecisionService.requireInstituteScope("查看全局接入变更记录");
        } else {
            accessDecisionService.requireTaskAccess(taskId, false);
        }
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(ingestionClient.listChangeLogs(query)));
    }

    @PostMapping("/tasks/changes")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChangeLog(@RequestBody Map<String, Object> payload) {
        Long resolvedTaskId = parseLong(payload == null ? null : payload.get("taskId"));
        if (resolvedTaskId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId 不能为空");
        }
        accessDecisionService.requireTaskAccess(resolvedTaskId, true);
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
        return ResponseEntity.ok(accessDecisionService.sanitizeResponse(response));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildAsyncProxyResponse(ApiResponse<Map<String, Object>> response) {
        if (response == null) {
            return ResponseEntity.ok(new ApiResponse<>(200, "accepted", null));
        }
        response = accessDecisionService.sanitizeResponse(response);
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            ApiResponse<Map<String, Object>> normalized = new ApiResponse<>(200, response.getMessage(), response.getData());
            normalized.setCode(response.getCode());
            return ResponseEntity.ok(normalized);
        }
        return ResponseEntity.status(status > 0 ? status : 500).body(response);
    }

    private Object sanitizeJsonResponse(Object response) {
        if (response instanceof ApiResponse<?> apiResponse) {
            return accessDecisionService.sanitizeResponse(apiResponse);
        }
        return accessDecisionService.removeInternalFilePaths(response);
    }

    private <T> ApiResponse<T> auditedIngestionCall(
        String actionCode,
        String resourceId,
        String beginSummary,
        String successSummary,
        String failureSummary,
        Map<String, Object> details,
        Supplier<ApiResponse<T>> operation
    ) {
        String auditOperationId = UUID.randomUUID().toString();
        UUID beginAuditReceipt = strictAudit(
            actionCode,
            AuditStage.BEGIN,
            resourceId,
            auditOperationId,
            beginSummary,
            details
        );
        ApiResponse<T> response;
        try {
            response = operation.get();
        } catch (RuntimeException failure) {
            Map<String, Object> failed = safeAuditDetails(details);
            failed.put("errorType", failure.getClass().getSimpleName());
            failed.put("operationOutcome", "UNKNOWN");
            failed.put("doNotRetry", true);
            if (failure instanceof ResponseStatusException statusFailure) {
                failed.put("httpStatus", statusFailure.getStatusCode().value());
            }
            terminalStrictAudit(
                actionCode,
                AuditStage.FAIL,
                resourceId,
                auditOperationId,
                beginAuditReceipt,
                failureSummary,
                failed
            );
            throw failure;
        }
        boolean success = isSuccessful(response);
        Map<String, Object> outcome = safeAuditDetails(details);
        if (response != null) {
            outcome.put("downstreamStatus", response.getStatus());
            putAuditText(outcome, "downstreamCode", response.getCode());
        } else {
            outcome.put("downstreamStatus", "UNAVAILABLE");
        }
        terminalStrictAudit(
            actionCode,
            success ? AuditStage.SUCCESS : AuditStage.FAIL,
            resourceId,
            auditOperationId,
            beginAuditReceipt,
            success ? successSummary : failureSummary,
            outcome
        );
        return response;
    }

    private boolean isSuccessful(ApiResponse<?> response) {
        return response != null && response.getStatus() >= 200 && response.getStatus() < 300;
    }

    private UUID strictAudit(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String auditOperationId,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> payload = safeAuditDetails(details);
        payload.put("summary", summary);
        payload.put("auditOperationId", auditOperationId);
        payload.put("operator", SecurityUtils.getCurrentUserLogin().orElse("system"));
        return auditService.auditActionStrict(actionCode, stage, resourceId, Map.copyOf(payload));
    }

    private void terminalStrictAudit(
        String actionCode,
        AuditStage stage,
        String resourceId,
        String auditOperationId,
        UUID beginAuditReceipt,
        String summary,
        Map<String, Object> details
    ) {
        Map<String, Object> correlated = safeAuditDetails(details);
        correlated.put("beginAuditReceipt", beginAuditReceipt.toString());
        try {
            strictAudit(actionCode, stage, resourceId, auditOperationId, summary, correlated);
        } catch (RuntimeException auditFailure) {
            throw new AuditFinalizationException(auditOperationId, beginAuditReceipt, auditFailure);
        }
    }

    private Map<String, Object> safeAuditDetails(Map<String, Object> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    safe.put(key, value);
                }
            });
        }
        return safe;
    }

    private void putAuditText(Map<String, Object> target, String key, Object value) {
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private static final class AuditFinalizationException extends ResponseStatusException {

        private AuditFinalizationException(String operationId, UUID beginAuditReceipt, RuntimeException cause) {
            super(
                HttpStatus.CONFLICT,
                "操作结果已经产生，但审计终态尚未确认；请勿重复执行，请使用 BEGIN 回执核对。operationId=" +
                operationId +
                ", beginReceipt=" +
                beginAuditReceipt,
                cause
            );
        }
    }

    private Map<String, Object> classificationAuditEvidence(Map<String, Object> source) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return evidence;
        }
        Map<String, Object> seal = mapValue(source.get("classificationSeal"));
        if (seal.isEmpty()) {
            Map<String, Object> sourceConfig = mapValue(source.get("sourceConfig"));
            seal = mapValue(sourceConfig.get("classificationSeal"));
        }
        copyAuditEvidence(seal, evidence, "sealId", "sealId");
        copyAuditEvidence(seal, evidence, "subjectType", "subjectType");
        copyAuditEvidence(seal, evidence, "subjectKey", "subjectKey");
        copyAuditEvidence(seal, evidence, "effectiveLevel", "effectiveLevel");
        copyAuditEvidence(seal, evidence, "fileFloor", "fileFloor");
        copyAuditEvidence(seal, evidence, "snapshotVersion", "snapshotVersion");
        copyAuditEvidence(seal, evidence, "propagationStatus", "propagationStatus");
        Object checksum = firstNonBlank(
            seal.get("evidenceChecksum"),
            seal.get("checksum"),
            seal.get("fileChecksum")
        );
        putAuditText(evidence, "evidenceChecksum", checksum);
        Object rawFields = source.get("fieldClassifications");
        if (rawFields instanceof Map<?, ?> fields) {
            evidence.put("fieldClassificationCount", fields.size());
            Map<String, Integer> levelCounts = new java.util.TreeMap<>();
            fields.values().forEach(level -> {
                String normalized = String.valueOf(level).trim();
                if (StringUtils.hasText(normalized)) {
                    levelCounts.merge(normalized, 1, Integer::sum);
                }
            });
            if (!levelCounts.isEmpty()) {
                evidence.put("fieldClassificationLevelCounts", Map.copyOf(levelCounts));
            }
        }
        copyAuditEvidence(source, evidence, "preCheckStatus", "preCheckStatus");
        copyAuditEvidence(source, evidence, "preCheckRunId", "preCheckRunId");
        copyAuditEvidence(source, evidence, "revisionNumber", "revisionNumber");
        copyAuditEvidence(source, evidence, "revisionState", "revisionState");
        copyAuditEvidence(source, evidence, "activeRevisionId", "activeRevisionId");
        copyAuditEvidence(source, evidence, "activeRevisionNumber", "activeRevisionNumber");
        copyAuditEvidence(source, evidence, "effectiveConfigChecksum", "effectiveConfigChecksum");
        copyAuditEvidence(source, evidence, "defaultPolicyVersion", "defaultPolicyVersion");
        copyAuditEvidence(source, evidence, "defaultPolicyChecksum", "defaultPolicyChecksum");
        copyAuditEvidence(source, evidence, "qualityPolicyRef", "qualityPolicyRef");
        return evidence;
    }

    private void copyAuditEvidence(
        Map<String, Object> source,
        Map<String, Object> target,
        String sourceKey,
        String targetKey
    ) {
        if (source != null) {
            putAuditText(target, targetKey, source.get(sourceKey));
        }
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少目标端配置");
        }
        Map<String, Object> overrides = extractConfig(destinationMap.get("config"));
        Map<String, Object> safeOverrides = extractConfig(accessDecisionService.retainExplicitSecrets(overrides, null));
        if (!safeOverrides.equals(overrides)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "托管目标数据源不得提交原始凭据，请仅使用数据源引用");
        }
        Map<String, Object> mergedConfig = mergeDestinationConfig(resolved.destinationConfig(), safeOverrides);
        mergedConfig = extractConfig(accessDecisionService.retainExplicitSecrets(mergedConfig, null));
        applyTargetDataSourceId(mergedConfig, resolved);
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

    private boolean usesPlatformDefaultDestination(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        Object destinationObj = payload.get("destination");
        if (!(destinationObj instanceof Map<?, ?> destinationMap)) {
            return false;
        }
        Object flag = destinationMap.get("usePlatformDefault");
        return flag instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(flag));
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
        applyTargetDataSourceId(mergedConfig, resolved);
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

    private DefaultDestinationSyncService.DefaultDestinationSnapshot resolveDestinationSnapshot(Map<String, Object> payload) {
        String targetDataSourceId = extractTargetDataSourceId(payload);
        if (StringUtils.hasText(targetDataSourceId)) {
            return destinationSyncService.ensureDestination(targetDataSourceId);
        }
        return destinationSyncService.ensureDefaultDestination();
    }

    private void applyTargetDataSourceId(
        Map<String, Object> config,
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot
    ) {
        if (config == null || snapshot == null || !StringUtils.hasText(snapshot.dataSourceId())) {
            return;
        }
        config.put("targetDataSourceId", snapshot.dataSourceId());
    }

    private String extractTargetDataSourceId(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String topLevel = firstText(payload.get("targetDataSourceId"), payload.get("destinationDataSourceId"));
        if (StringUtils.hasText(topLevel)) {
            return topLevel;
        }
        Object destinationObj = payload.get("destination");
        if (destinationObj instanceof Map<?, ?> destinationMap) {
            String direct = firstText(
                destinationMap.get("targetDataSourceId"),
                destinationMap.get("destinationDataSourceId"),
                destinationMap.get("dataSourceId")
            );
            if (StringUtils.hasText(direct)) {
                return direct;
            }
            String fromConfig = extractTargetDataSourceIdFromConfig(extractConfig(destinationMap.get("config")));
            if (StringUtils.hasText(fromConfig)) {
                return fromConfig;
            }
        }
        return extractTargetDataSourceIdFromConfig(extractConfig(payload.get("destinationConfig")));
    }

    private String extractTargetDataSourceIdFromConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        return firstText(
            config.get("targetDataSourceId"),
            config.get("destinationDataSourceId"),
            config.get("dataSourceId")
        );
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
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

    private Map<String, Object> attachClassificationSeal(Map<String, Object> payload, boolean required) {
        Map<String, Object> resolved = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        Map<String, Object> source = mapValue(resolved.get("source"));
        Map<String, Object> sourceConfig = source.get("config") instanceof Map<?, ?>
            ? mapValue(source.get("config"))
            : mapValue(resolved.get("sourceConfig"));
        boolean fileTask = isFileTask(resolved, source, sourceConfig);
        if (
            fileTask &&
            !(sourceConfig.get("classificationSeal") instanceof Map<?, ?>)
        ) {
            throw new CatalogClassificationException(
                "FILE_CLASSIFICATION_SEAL_REQUIRED",
                "文件接入任务必须保留上传阶段生成的文件密级封存"
            );
        }
        if (sourceConfig.get("classificationSeal") instanceof Map<?, ?> fileSeal) {
            Object rawFields = resolved.get("fieldClassifications") != null
                ? resolved.get("fieldClassifications")
                : sourceConfig.get("fieldClassifications");
            Object rawColumns = sourceConfig.get("columns") != null
                ? sourceConfig.get("columns")
                : sourceConfig.get("_fileColumns");
            return classificationAdmissionService.attachFileSeal(
                resolved,
                mapValue(fileSeal),
                rawFields,
                rawColumns,
                sourceConfig,
                required
            );
        }
        if (resolved.get("classificationSeal") instanceof Map<?, ?> providedSeal) {
            return attachProvidedClassificationSeal(
                resolved,
                mapValue(providedSeal),
                resolved.get("fieldClassifications")
            );
        }

        UUID sourceId = parseUuid(
            source.get("dataSourceId") != null
                ? source.get("dataSourceId")
                : resolved.get("sourceDataSourceId")
        );
        if (sourceId == null) {
            if (required) {
                throw new CatalogClassificationException(
                    "CLASSIFICATION_SEAL_REQUIRED",
                    "生产接入任务缺少数据源标识，无法生成密级封存"
                );
            }
            return resolved;
        }
        InfraDataSource dataSource = dataSourceRepository
            .findById(sourceId)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_SOURCE_NOT_FOUND",
                    "密级封存引用的数据源不存在: " + sourceId
                )
            );
        Map<String, Object> props = readDataSourceProps(dataSource);
        Object rawLevel = props.get("classification");
        String declared;
        try {
            declared = SecurityLevelCatalog.requireDataLevel(rawLevel).code();
        } catch (IllegalArgumentException ex) {
            if (!required) {
                return resolved;
            }
            throw new CatalogClassificationException(
                "SOURCE_CLASSIFICATION_REQUIRED",
                "请先为数据源明确选择密级，再创建生产接入任务"
            );
        }
        Map<String, String> fieldClassifications = mergeFieldClassifications(
            props.get("fieldClassifications"),
            props.get("columnClassifications")
        );
        String subjectKey = "data-source:" + sourceId;
        String evidenceSource =
            subjectKey + ":" + declared + ":" + new java.util.TreeMap<>(fieldClassifications);
        CatalogClassificationSnapshot snapshot = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                subjectKey,
                null,
                declared,
                null,
                null,
                fieldClassifications.values(),
                "SOURCE_DECLARATION",
                subjectKey,
                sha256(evidenceSource),
                "{\"sourceId\":\"" +
                sourceId +
                "\",\"declaredLevel\":\"" +
                declared +
                "\",\"fieldClassifications\":" +
                toJson(fieldClassifications) +
                "}"
            )
        );
        if (!subjectKey.equals(snapshot.getSubjectKey())) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SOURCE_MISMATCH",
                "密级封存与当前数据源不匹配，请重新确认数据源"
            );
        }
        Map<String, Object> seal = new LinkedHashMap<>();
        seal.put("sealId", snapshot.getId());
        seal.put("subjectType", snapshot.getSubjectType());
        seal.put("subjectKey", snapshot.getSubjectKey());
        seal.put("assetType", snapshot.getAssetType());
        seal.put("effectiveLevel", snapshot.getEffectiveLevel());
        seal.put("snapshotVersion", snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion());
        seal.put("checksum", snapshot.getEvidenceChecksum());
        seal.put("sealedAt", snapshot.getSealedAt());
        seal.put("propagationStatus", snapshot.getPropagationStatus());
        resolved.put("classificationSeal", seal);
        if (!resolved.containsKey("fieldClassifications") && !fieldClassifications.isEmpty()) {
            resolved.put("fieldClassifications", new LinkedHashMap<>(fieldClassifications));
        }
        return resolved;
    }

    /**
     * Update and admission derive non-file classification evidence from the
     * currently selected managed data source. Client-provided evidence may
     * belong to the previous source, so it is never reused here.
     */
    private Map<String, Object> attachCurrentSourceClassificationSeal(
        Map<String, Object> payload,
        boolean required
    ) {
        Map<String, Object> resolved = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        Map<String, Object> source = mapValue(resolved.get("source"));
        Map<String, Object> sourceConfig = source.get("config") instanceof Map<?, ?>
            ? mapValue(source.get("config"))
            : mapValue(resolved.get("sourceConfig"));
        Map<String, Object> topLevelSourceConfig = mapValue(resolved.get("sourceConfig"));
        if (isFileTask(resolved, source, sourceConfig)) {
            return attachClassificationSeal(resolved, required);
        }

        boolean hadClassificationSeal = resolved.get("classificationSeal") instanceof Map<?, ?> ||
            sourceConfig.get("classificationSeal") instanceof Map<?, ?> ||
            topLevelSourceConfig.get("classificationSeal") instanceof Map<?, ?>;
        resolved.remove("classificationSeal");
        resolved.remove("fieldClassifications");
        if (resolved.get("source") instanceof Map<?, ?>) {
            Map<String, Object> cleanSource = mapValue(resolved.get("source"));
            if (cleanSource.get("config") instanceof Map<?, ?>) {
                Map<String, Object> cleanConfig = mapValue(cleanSource.get("config"));
                cleanConfig.remove("classificationSeal");
                cleanConfig.remove("fieldClassifications");
                cleanSource.put("config", cleanConfig);
            }
            resolved.put("source", cleanSource);
        }
        if (resolved.get("sourceConfig") instanceof Map<?, ?>) {
            Map<String, Object> cleanConfig = mapValue(resolved.get("sourceConfig"));
            cleanConfig.remove("classificationSeal");
            cleanConfig.remove("fieldClassifications");
            resolved.put("sourceConfig", cleanConfig);
        }
        return attachClassificationSeal(resolved, required || hadClassificationSeal);
    }

    private Map<String, Object> attachProvidedClassificationSeal(
        Map<String, Object> resolved,
        Map<String, Object> reference,
        Object rawFieldClassifications
    ) {
        String subjectType = String.valueOf(reference.getOrDefault("subjectType", "")).trim().toUpperCase();
        String subjectKey = String.valueOf(reference.getOrDefault("subjectKey", "")).trim();
        if (!StringUtils.hasText(subjectType) || !StringUtils.hasText(subjectKey)) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_SEAL_INVALID",
                "接入密级封存引用无效"
            );
        }
        CatalogClassificationSnapshot sourceSnapshot = classificationService
            .resolve(subjectType, subjectKey)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_SEAL_NOT_FOUND",
                    "接入密级封存不存在或已失效"
                )
            );
        assertCurrentSeal(reference, sourceSnapshot, "CLASSIFICATION_SEAL_STALE");
        Map<String, String> fields = normalizeFieldClassifications(rawFieldClassifications);
        if (fields.isEmpty()) {
            return resolved;
        }
        String fileFloor = reference.get("fileFloor") == null
            ? null
            : SecurityLevelCatalog.requireDataLevel(reference.get("fileFloor")).code();
        if (StringUtils.hasText(fileFloor)) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (SecurityLevelCatalog.isDataDowngrade(fileFloor, entry.getValue())) {
                    throw new CatalogClassificationException(
                        "CLASSIFICATION_DOWNGRADE_FORBIDDEN",
                        "字段 " + entry.getKey() + " 的密级不能低于文件密级 " + fileFloor
                    );
                }
            }
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(SecurityLevelCatalog.requireDataLevel(sourceSnapshot.getEffectiveLevel()).code());
        candidates.addAll(fields.values());
        String compositeKey = "ingestion-seal:" + subjectType.toLowerCase() + ":" + subjectKey;
        String evidenceSource =
            sourceSnapshot.getId() +
            ":" +
            sourceSnapshot.getRecordVersion() +
            ":" +
            sourceSnapshot.getEvidenceChecksum() +
            ":" +
            new java.util.TreeMap<>(fields);
        CatalogClassificationSnapshot taskSnapshot = classificationService.sealOrRaise(
            new CatalogClassificationService.SealCommand(
                "ASSET",
                compositeKey,
                "DATASET",
                null,
                null,
                null,
                candidates,
                "UPSTREAM_INHERITANCE",
                subjectKey,
                sha256(evidenceSource),
                toJson(
                    Map.of(
                        "sourceSealId",
                        sourceSnapshot.getId(),
                        "sourceSubjectType",
                        subjectType,
                        "sourceSubjectKey",
                        subjectKey,
                        "fieldClassifications",
                        fields
                    )
                )
            )
        );
        Map<String, Object> seal = sealReference(taskSnapshot);
        if (StringUtils.hasText(fileFloor)) {
            seal.put("fileFloor", fileFloor);
        }
        resolved.put("classificationSeal", seal);
        resolved.put("fieldClassifications", new LinkedHashMap<>(fields));
        return resolved;
    }

    private void assertCurrentSeal(
        Map<String, Object> reference,
        CatalogClassificationSnapshot snapshot,
        String errorCode
    ) {
        UUID sealId = parseUuid(reference.get("sealId"));
        Long version = parseLong(reference.get("snapshotVersion"));
        String checksum = String.valueOf(reference.getOrDefault("checksum", "")).trim();
        if (
            sealId == null ||
            !sealId.equals(snapshot.getId()) ||
            version == null ||
            !version.equals(snapshot.getRecordVersion()) ||
            !StringUtils.hasText(checksum) ||
            !checksum.equals(snapshot.getEvidenceChecksum())
        ) {
            throw new CatalogClassificationException(
                errorCode,
                "密级封存已变化，请重新确认后再创建任务"
            );
        }
    }

    private Map<String, Object> sealReference(CatalogClassificationSnapshot snapshot) {
        Map<String, Object> seal = new LinkedHashMap<>();
        seal.put("sealId", snapshot.getId());
        seal.put("subjectType", snapshot.getSubjectType());
        seal.put("subjectKey", snapshot.getSubjectKey());
        seal.put("assetType", snapshot.getAssetType());
        seal.put("effectiveLevel", snapshot.getEffectiveLevel());
        seal.put("snapshotVersion", snapshot.getRecordVersion() == null ? 0L : snapshot.getRecordVersion());
        seal.put("checksum", snapshot.getEvidenceChecksum());
        seal.put("sealedAt", snapshot.getSealedAt());
        seal.put("propagationStatus", snapshot.getPropagationStatus());
        return seal;
    }

    private Map<String, String> normalizeFieldClassifications(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> fields)) {
            throw new CatalogClassificationException(
                "SOURCE_FIELD_CLASSIFICATION_INVALID",
                "字段密级配置格式无效"
            );
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        fields.forEach((column, level) -> {
            if (!StringUtils.hasText(String.valueOf(column))) {
                throw new CatalogClassificationException(
                    "SOURCE_FIELD_CLASSIFICATION_INVALID",
                    "字段密级配置包含空字段名"
                );
            }
            try {
                normalized.put(
                    String.valueOf(column).trim(),
                    SecurityLevelCatalog.requireDataLevel(level).code()
                );
            } catch (IllegalArgumentException ex) {
                throw new CatalogClassificationException(
                    "SOURCE_FIELD_CLASSIFICATION_INVALID",
                    "字段 " + column + " 的密级无效"
                );
            }
        });
        return Map.copyOf(normalized);
    }

    private Map<String, String> mergeFieldClassifications(Object... values) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (values == null) {
            return Map.of();
        }
        for (Object value : values) {
            normalizeFieldClassifications(value)
                .forEach((field, level) ->
                    merged.merge(
                        field,
                        level,
                        (current, candidate) ->
                            SecurityLevelCatalog.maxDataCode(current, candidate)
                    )
                );
        }
        return Map.copyOf(merged);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new CatalogClassificationException(
                "SOURCE_CLASSIFICATION_INVALID",
                "无法生成数据源密级证据"
            );
        }
    }

    private Map<String, Object> readDataSourceProps(InfraDataSource dataSource) {
        if (dataSource == null || !StringUtils.hasText(dataSource.getProps())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(dataSource.getProps(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new CatalogClassificationException(
                "SOURCE_CLASSIFICATION_INVALID",
                "数据源扩展配置无法解析，不能生成密级封存"
            );
        }
    }

    private Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private boolean isFileTask(
        Map<String, Object> task,
        Map<String, Object> source,
        Map<String, Object> sourceConfig
    ) {
        if (
            sourceConfig.containsKey("_fileId") ||
            sourceConfig.containsKey("_fileHash") ||
            sourceConfig.containsKey("_encrypted") ||
            sourceConfig.containsKey("_fileColumns")
        ) {
            return true;
        }
        Object rawType = firstNonBlank(
            task.get("sourceType"),
            source.get("sourceType"),
            source.get("type"),
            sourceConfig.get("readerType")
        );
        if (rawType == null) {
            return false;
        }
        String sourceType = rawType.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return switch (sourceType) {
            case "file", "csv", "txt", "excel", "txtfilereader", "excelreader" -> true;
            default -> sourceType.contains("file");
        };
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成密级证据摘要", ex);
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
