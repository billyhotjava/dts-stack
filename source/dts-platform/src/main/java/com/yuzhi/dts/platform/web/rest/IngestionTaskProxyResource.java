package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationException;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationService;
import com.yuzhi.dts.platform.service.ingestion.IngestionClassificationAdmissionService;
import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.infra.DefaultDestinationSyncService;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
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
    private final InfraDataSourceRepository dataSourceRepository;
    private final CatalogClassificationService classificationService;
    private final IngestionClassificationAdmissionService classificationAdmissionService;
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
        this.objectMapper = objectMapper;
    }

    @GetMapping("/templates")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<?> listTemplates() {
        return ResponseEntity.ok(ingestionClient.listTemplates());
    }

    @PostMapping("/templates/{templateId}/render")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<?> renderTemplate(@PathVariable String templateId, @RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.ok(ingestionClient.renderTemplate(templateId, payload));
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
        if (!draft || usesPlatformDefaultDestination(payload)) {
            DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = resolveDestinationSnapshot(payload);
            resolvedPayload = applyDefaultDestinationPayload(payload, snapshot);
        }
        resolvedPayload = attachClassificationSeal(resolvedPayload, !draft);
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
        DefaultDestinationSyncService.DefaultDestinationSnapshot snapshot = resolveDestinationSnapshot(payload);
        Map<String, Object> resolvedPayload = applyDefaultDestinationUpdatePayload(payload, snapshot);
        resolvedPayload = attachClassificationSeal(resolvedPayload, false);
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

    @PostMapping("/tasks/{id}/admit")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> admitTask(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> existing = ingestionClient.getTask(id);
        if (existing == null) {
            return ResponseEntity.ok(new ApiResponse<>(503, "接入服务暂不可用", null));
        }
        if (existing.getStatus() < 200 || existing.getStatus() >= 300 || existing.getData() == null) {
            return ResponseEntity.ok(existing);
        }

        Map<String, Object> sealedTask = attachClassificationSeal(existing.getData(), true);
        Map<String, Object> admission = new LinkedHashMap<>();
        admission.put("classificationSeal", sealedTask.get("classificationSeal"));
        if (sealedTask.get("fieldClassifications") != null) {
            admission.put("fieldClassifications", sealedTask.get("fieldClassifications"));
        }
        ApiResponse<Map<String, Object>> response = ingestionClient.admitTask(id, admission);
        String operator = SecurityUtils.getCurrentUserLogin().orElse("system");
        boolean success = response != null && response.getStatus() >= 200 && response.getStatus() < 300;
        auditService.auditAction(
            "INGESTION_TASK_UPDATE",
            success ? AuditStage.SUCCESS : AuditStage.FAIL,
            String.valueOf(id),
            Map.of(
                "summary",
                success ? "完成密级封存与生产准入" : "密级封存与生产准入失败",
                "taskId",
                id,
                "operator",
                operator
            )
        );
        return ResponseEntity.ok(response == null ? new ApiResponse<>(503, "接入服务暂不可用", null) : response);
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

    @PostMapping("/tasks/{id}/execute/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeTaskAsync(@PathVariable("id") Long id) {
        ApiResponse<Map<String, Object>> response = ingestionClient.executeTaskAsync(id);
        return buildAsyncProxyResponse(response);
    }

    @PostMapping("/tasks/{id}/backfill")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> backfillTask(
        @PathVariable("id") Long id,
        @RequestBody Map<String, Object> payload
    ) {
        ApiResponse<Map<String, Object>> response = ingestionClient.backfillTask(id, payload);
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
        ApiResponse<Map<String, Object>> response = ingestionClient.retryExecution(id, executionId, Map.of("mode", mode));
        if (response != null && response.getData() != null) {
            try {
                externalRunLogService.recordIngestionExecution(response.getData(), activeDept);
            } catch (RuntimeException ex) {
                // best-effort sync
            }
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{id}/executions/{executionId}/retry/async")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> retryExecutionAsync(
        @PathVariable("id") Long id,
        @PathVariable("executionId") Long executionId,
        @RequestParam(value = "mode", required = false, defaultValue = "FAILED_ONLY") String mode
    ) {
        ApiResponse<Map<String, Object>> response = ingestionClient.retryExecutionAsync(id, executionId, Map.of("mode", mode));
        return buildAsyncProxyResponse(response);
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

    @GetMapping("/api/contract")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getApiConnectorContract() {
        return ResponseEntity.ok(ingestionClient.getApiConnectorContract());
    }

    @GetMapping("/api/auth-providers")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> listApiAuthProviders() {
        return ResponseEntity.ok(ingestionClient.listApiAuthProviders());
    }

    @PostMapping("/api/test-connection")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> testApiConnection(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ingestionClient.testApiConnection(payload));
    }

    @GetMapping("/tasks/{id}/realtime-status")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getRealtimeStatus(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.getRealtimeStatus(id));
    }

    @PostMapping("/tasks/{id}/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> parseStagingFile(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.parseStagingFile(id));
    }

    @PostMapping("/tasks/{id}/pre-check")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> preCheckStaging(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.preCheckStaging(id));
    }

    @PutMapping("/tasks/{id}/staging/{rowNum}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> updateStagingCell(
        @PathVariable("id") Long id,
        @PathVariable("rowNum") Integer rowNum,
        @RequestBody Map<String, Object> payload
    ) {
        return ResponseEntity.ok(ingestionClient.updateStagingCell(id, rowNum, payload));
    }

    @PostMapping("/tasks/{id}/re-check")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> reCheckStaging(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.reCheckStaging(id));
    }

    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> submitStaging(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.submitStaging(id));
    }

    @DeleteMapping("/tasks/{id}/staging")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> dropStaging(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ingestionClient.dropStaging(id));
    }

    @GetMapping("/tasks/{id}/staging")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getStagingData(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.getStagingData(id, query));
    }

    @GetMapping("/tasks/{id}/staging/errors/summary")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getStagingErrorSummary(
        @PathVariable("id") Long id,
        @RequestParam Map<String, String> params
    ) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.getStagingErrorSummary(id, query));
    }

    @GetMapping("/tasks/{id}/staging/errors/download")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<byte[]> downloadStagingErrors(@PathVariable("id") Long id) {
        ResponseEntity<byte[]> response = ingestionClient.downloadStagingErrors(id);
        return ResponseEntity
            .status(response.getStatusCode())
            .headers(response.getHeaders())
            .body(response.getBody());
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> uploadFile(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ingestionClient.uploadFile(file));
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
        ApiResponse<Object> response = ingestionClient.uploadAndParse(file, previewLimit, sheetIndex, sheetName);
        if (response == null) {
            return ResponseEntity.ok(new ApiResponse<>(503, "接入服务暂不可用", null));
        }
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            try {
                response.setData(
                    classificationAdmissionService.sealEncryptedUpload(
                        response.getData(),
                        declaredLevel
                    )
                );
            } catch (CatalogClassificationException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
            }
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/files/parse")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> parseUploadedFile(@RequestBody FileParseRequest request) {
        if (request == null || request.fileId() == null || request.fileId().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, "fileId 不能为空", null));
        }
        return ResponseEntity.ok(
            ingestionClient.parseUploadedFile(
                request.fileId(),
                request.previewLimit(),
                request.sheetIndex(),
                request.sheetName(),
                request.originalName()
            )
        );
    }

    @GetMapping("/tasks/executions/observability")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getExecutionsObservability(@RequestParam Map<String, String> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.getExecutionsObservability(query));
    }

    @GetMapping("/tasks/executions/governance-overview")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<Object>> getGovernanceOverview(@RequestParam Map<String, String> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (params != null) {
            query.putAll(params);
        }
        return ResponseEntity.ok(ingestionClient.getGovernanceOverview(query));
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

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildAsyncProxyResponse(ApiResponse<Map<String, Object>> response) {
        if (response == null) {
            return ResponseEntity.ok(new ApiResponse<>(200, "accepted", null));
        }
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            ApiResponse<Map<String, Object>> normalized = new ApiResponse<>(200, response.getMessage(), response.getData());
            normalized.setCode(response.getCode());
            return ResponseEntity.ok(normalized);
        }
        return ResponseEntity.status(status > 0 ? status : 500).body(response);
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
