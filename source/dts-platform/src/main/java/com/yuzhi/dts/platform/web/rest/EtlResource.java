package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.etl.DbtAssetSyncService;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtPreviewService;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import com.yuzhi.dts.platform.service.etl.DbtRunResultService;
import com.yuzhi.dts.platform.service.etl.DbtQualityGateService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/etl")
public class EtlResource {

    private final DbtConfigService dbtConfigService;
    private final DbtManifestService manifestService;
    private final DbtSourceService dbtSourceService;
    private final DbtAssetSyncService dbtAssetSyncService;
    private final DbtDagService dbtDagService;
    private final DbtPreviewService dbtPreviewService;
    private final DbtRunResultService dbtRunResultService;
    private final DbtQualityGateService dbtQualityGateService;
    private final DbtReleaseGateService dbtReleaseGateService;
    private final DbtArtifactSyncState dbtArtifactSyncState;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;
    private final ExternalRunLogService externalRunLogService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EtlResource(
        DbtConfigService dbtConfigService,
        DbtManifestService manifestService,
        DbtSourceService dbtSourceService,
        DbtAssetSyncService dbtAssetSyncService,
        DbtDagService dbtDagService,
        DbtPreviewService dbtPreviewService,
        DbtRunResultService dbtRunResultService,
        DbtQualityGateService dbtQualityGateService,
        DbtReleaseGateService dbtReleaseGateService,
        DbtArtifactSyncState dbtArtifactSyncState,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties,
        ExternalRunLogService externalRunLogService,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.dbtConfigService = dbtConfigService;
        this.manifestService = manifestService;
        this.dbtSourceService = dbtSourceService;
        this.dbtAssetSyncService = dbtAssetSyncService;
        this.dbtDagService = dbtDagService;
        this.dbtPreviewService = dbtPreviewService;
        this.dbtRunResultService = dbtRunResultService;
        this.dbtQualityGateService = dbtQualityGateService;
        this.dbtReleaseGateService = dbtReleaseGateService;
        this.dbtArtifactSyncState = dbtArtifactSyncState;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
        this.externalRunLogService = externalRunLogService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dbt/config")
    public ApiResponse<DbtConfigService.DbtConfigView> getDbtConfig() {
        ApiResponse<DbtConfigService.DbtConfigView> response = ApiResponses.ok(dbtConfigService.loadConfig());
        auditService.audit("READ", "etl.dbt.config", "view");
        return response;
    }

    @PutMapping("/dbt/config")
    public ApiResponse<DbtConfigService.DbtConfigView> updateDbtConfig(
        @RequestBody DbtConfigService.DbtWorkspaceConfigRequest request
    ) {
        ApiResponse<DbtConfigService.DbtConfigView> response = ApiResponses.ok(dbtConfigService.saveConfig(request));
        auditService.audit("UPDATE", "etl.dbt.config", "save");
        return response;
    }

    @GetMapping("/dbt/models")
    public ApiResponse<DbtManifestService.DbtModelResult> listDbtModels() {
        ApiResponse<DbtManifestService.DbtModelResult> response = ApiResponses.ok(manifestService.listModels());
        auditService.audit("READ", "etl.dbt.models", "list");
        return response;
    }

    @GetMapping("/dbt/preview")
    public ApiResponse<DbtPreviewService.PreviewResult> previewModel(
        @RequestParam String model,
        @RequestParam(defaultValue = "100") int limit
    ) {
        DbtPreviewService.PreviewResult result = dbtPreviewService.preview(model, Math.min(limit, 500));
        auditService.audit("READ", "etl.dbt.preview", model);
        return ApiResponses.ok(result);
    }

    @PostMapping("/dbt/sources/refresh")
    public ApiResponse<DbtSourceService.DbtSourceRefreshResult> refreshDbtSources() {
        ApiResponse<DbtSourceService.DbtSourceRefreshResult> response = ApiResponses.ok(dbtSourceService.refreshOdsSources());
        auditService.audit("EXECUTE", "etl.dbt.sources", "refresh");
        return response;
    }

    @PostMapping("/dbt/models/sync")
    public ApiResponse<DbtAssetSyncService.DbtAssetSyncResult> syncDbtModels() {
        DbtAssetSyncService.DbtAssetSyncResult assetResult = dbtAssetSyncService.syncFromManifest();
        DbtRunResultService.DbtRunSyncResult runResult = dbtRunResultService.syncFromRunResults();
        recordDbtSyncState(assetResult, runResult);
        auditService.audit("EXECUTE", "etl.dbt.models", "sync");
        auditService.record(
            "EXECUTE",
            "etl.dbt.runs",
            "etl.dbt.runs",
            "sync",
            runResult.synced() ? "SUCCESS" : "FAILED",
            runResult
        );
        return ApiResponses.ok(assetResult);
    }

    @GetMapping("/dbt/sync/status")
    public ApiResponse<DbtArtifactSyncState.DbtArtifactSyncStatus> getDbtSyncStatus() {
        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestSummary(20);
        if (latestRun != null) {
            dbtArtifactSyncState.recordLatestRun(latestRun);
        }
        return ApiResponses.ok(dbtArtifactSyncState.snapshot());
    }

    @GetMapping("/dbt/runs")
    public ApiResponse<Map<String, Object>> listDbtRuns(
        @RequestParam(defaultValue = "20") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String dagId = airflowProperties.getDagId();
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, Math.max(1, Math.min(limit, 50))).orElse(Map.of());
        try {
            externalRunLogService.syncAirflowRuns(ExternalRunLogService.ENTRY_DBT, dagId, payload, activeDept);
        } catch (RuntimeException ex) {
            // best-effort
        }
        auditService.audit("READ", "etl.dbt.runs", "list");
        return ApiResponses.ok(payload);
    }

    @GetMapping("/dbt/runs/{dagRunId}/logs")
    public ResponseEntity<Map<String, Object>> getDbtRunLog(
        @PathVariable String dagRunId,
        @RequestParam(defaultValue = "dbt_load") String dagId,
        @RequestParam(defaultValue = "dbt_run") String taskId,
        @RequestParam(defaultValue = "1") int tryNumber
    ) {
        if (!airflowProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Airflow integration is not enabled"));
        }
        String log = airflowClient.getTaskInstanceLog(dagId, dagRunId, taskId, tryNumber);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dagId", dagId);
        result.put("dagRunId", dagRunId);
        result.put("taskId", taskId);
        result.put("tryNumber", tryNumber);
        result.put("log", log != null ? log : "");
        auditService.audit("READ", "etl.dbt.logs", dagRunId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dbt/run")
    public ApiResponse<Map<String, Object>> triggerDbtRun(
        @RequestBody DbtRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String operation = normalizeDbtOperation(request == null ? null : request.operation());
        boolean requireSelector = !"docs".equals(operation);
        String selector = resolveSelector(request, requireSelector);
        ApiResponse<Map<String, Object>> response = triggerDbtOperation(operation, request, activeDept, requireSelector);
        auditService.audit("EXECUTE", "etl.dbt." + operation, selector);
        return response;
    }

    @PostMapping("/dbt/compile")
    public ApiResponse<Map<String, Object>> triggerDbtCompile(
        @RequestBody(required = false) DbtRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = triggerDbtOperation("compile", request, activeDept, false);
        auditService.audit("EXECUTE", "etl.dbt.compile", resolveSelector(request, false));
        return response;
    }

    @PostMapping("/dbt/test")
    public ApiResponse<Map<String, Object>> triggerDbtTest(
        @RequestBody(required = false) DbtRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = triggerDbtOperation("test", request, activeDept, false);
        auditService.audit("EXECUTE", "etl.dbt.test", resolveSelector(request, false));
        return response;
    }

    @PostMapping("/dbt/docs")
    public ApiResponse<Map<String, Object>> triggerDbtDocs(
        @RequestBody(required = false) DbtRunRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ApiResponse<Map<String, Object>> response = triggerDbtOperation("docs", request, activeDept, false);
        auditService.audit("EXECUTE", "etl.dbt.docs", resolveSelector(request, false));
        return response;
    }

    @PostMapping("/dbt/quality-gate/check")
    public ApiResponse<DbtQualityGateService.DbtQualityGateResult> checkDbtQualityGate(
        @RequestBody(required = false) DbtQualityGateRequest request
    ) {
        String selector = request == null ? null : request.models();
        DbtQualityGateService.DbtQualityGateResult result = dbtQualityGateService.evaluate(selector);
        auditService.audit("READ", "etl.dbt.quality-gate", StringUtils.hasText(selector) ? selector : "all");
        return ApiResponses.ok(result);
    }

    @PostMapping("/dbt/release-gate/check")
    public ApiResponse<DbtReleaseGateService.DbtReleaseGateResult> checkDbtReleaseGate(
        @RequestBody(required = false) DbtReleaseGateRequest request
    ) {
        String selector = request == null ? null : request.models();
        DbtReleaseGateService.DbtReleaseGateResult result = dbtReleaseGateService.evaluate(
            selector,
            request == null ? null : request.gitRef(),
            request == null ? null : request.commitSha(),
            request == null ? null : request.strictMode()
        );
        auditService.audit("READ", "etl.dbt.release-gate", StringUtils.hasText(selector) ? selector : "all");
        return ApiResponses.ok(result);
    }

    private void recordDbtSyncState(
        DbtAssetSyncService.DbtAssetSyncResult assetResult,
        DbtRunResultService.DbtRunSyncResult runResult
    ) {
        if (assetResult != null) {
            Long modified = fileModified(assetResult.manifestPath());
            dbtArtifactSyncState.recordManifest(modified, assetResult.synced(), assetResult.message());
            if (assetResult.stats() != null) {
                dbtArtifactSyncState.recordStats(assetResult.stats(), assetResult.message());
            }
        }
        if (runResult != null) {
            Long modified = fileModified(runResult.runResultsPath());
            dbtArtifactSyncState.recordRunResults(modified, runResult.synced(), runResult.message());
            if (runResult.summary() != null) {
                dbtArtifactSyncState.recordLatestRun(runResult.summary());
            }
        }
    }

    private Long fileModified(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        try {
            java.io.File file = java.nio.file.Path.of(path).toFile();
            if (!file.exists()) {
                return null;
            }
            long modified = file.lastModified();
            return modified > 0 ? modified : null;
        } catch (Exception ex) {
            return null;
        }
    }

    @GetMapping("/airflow/jobs")
    public ApiResponse<List<Map<String, Object>>> listAirflowJobs(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> payload = airflowClient.listDags(Math.max(1, Math.min(limit, 200))).orElse(Map.of());
        List<Map<String, Object>> dags = asListOfMaps(payload.get("dags"));
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> dag : dags) {
            String dagId = stringVal(dag.get("dag_id"));
            if (dagId == null || dagId.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dagId", dagId);
            row.put("name", defaultString(dag.get("dag_display_name"), dagId));
            row.put("owners", dag.get("owners"));
            row.put("isPaused", dag.get("is_paused"));
            row.put("schedule", defaultString(dag.get("timetable_summary"), dag.get("schedule_interval")));
            row.put("tags", dag.get("tags"));
            Map<String, Object> runsPayload = airflowClient.listDagRuns(dagId, 1).orElse(Map.of());
            Map<String, Object> latestRun = firstMap(runsPayload.get("dag_runs"));
            if (latestRun != null) {
                row.put("lastState", defaultString(latestRun.get("state"), null));
                row.put("lastRun", defaultString(latestRun.get("logical_date"), latestRun.get("execution_date")));
                row.put("lastDuration", latestRun.get("duration"));
                row.put("lastRunId", defaultString(latestRun.get("dag_run_id"), latestRun.get("run_id")));
            }
            results.add(row);
        }
        auditService.audit("READ", "etl.airflow.jobs", "list");
        return ApiResponses.ok(results);
    }

    @GetMapping("/airflow/jobs/{dagId}/runs")
    public ApiResponse<Map<String, Object>> listAirflowJobRuns(
        @PathVariable String dagId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, Math.max(1, Math.min(limit, 200))).orElse(Map.of());
        try {
            externalRunLogService.syncAirflowRuns(ExternalRunLogService.ENTRY_AIRFLOW, dagId, payload, activeDept);
        } catch (RuntimeException ex) {
            // best-effort
        }
        auditService.audit("READ", "etl.airflow.runs", dagId);
        return ApiResponses.ok(payload);
    }

    @PostMapping("/airflow/jobs/{dagId}/trigger")
    public ApiResponse<Map<String, Object>> triggerAirflowJob(
        @PathVariable String dagId,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> payload = normalizeTriggerPayload(body);
        Map<String, Object> result = airflowClient.triggerDag(dagId, payload).orElse(Map.of("status", "queued"));
        try {
            externalRunLogService.recordAirflowRun(ExternalRunLogService.ENTRY_AIRFLOW, dagId, result, payload, activeDept);
        } catch (RuntimeException ex) {
            // best-effort sync
        }
        auditService.audit("EXECUTE", "etl.airflow.trigger", dagId);
        return ApiResponses.ok(result);
    }

    public record DbtRunRequest(
        String models,
        String dagSelector,
        String target,
        String operation,
        Map<String, Object> vars,
        String gitRef,
        String commitSha,
        String buildInvocationId
    ) {}
    public record DbtQualityGateRequest(String models) {}
    public record DbtReleaseGateRequest(String models, String gitRef, String commitSha, Boolean strictMode) {}

    private String normalizeDbtOperation(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "run";
        }
        String op = raw.trim().toLowerCase();
        if ("run".equals(op) || "test".equals(op) || "compile".equals(op) || "docs".equals(op) || "build".equals(op)) {
            return op;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "operation 仅支持 run/test/compile/docs/build");
    }

    private ApiResponse<Map<String, Object>> triggerDbtOperation(
        String operation,
        DbtRunRequest request,
        String activeDept,
        boolean selectorRequired
    ) {
        String selector = resolveSelector(request, selectorRequired);
        String dagSelector = resolveDagSelector(request, selector);
        String dagId = dbtDagService.ensureDagForSelector(dagSelector);
        if (!StringUtils.hasText(dagId)) {
            dagId = airflowProperties.getDagId();
        }
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("operation", operation);
        if (StringUtils.hasText(selector)) {
            conf.put("models", selector);
        }
        if (StringUtils.hasText(request == null ? null : request.dagSelector())) {
            conf.put("dagSelector", dagSelector);
        }
        if (StringUtils.hasText(request == null ? null : request.target())) {
            conf.put("target", request.target().trim());
        }
        if (request != null && request.vars() != null && !request.vars().isEmpty()) {
            conf.put("vars", toJsonString(request.vars()));
        }
        if (StringUtils.hasText(request == null ? null : request.gitRef())) {
            conf.put("gitRef", request.gitRef().trim());
        }
        if (StringUtils.hasText(request == null ? null : request.commitSha())) {
            conf.put("commitSha", request.commitSha().trim());
        }
        if (StringUtils.hasText(request == null ? null : request.buildInvocationId())) {
            conf.put("buildInvocationId", request.buildInvocationId().trim());
        }
        Map<String, Object> payload = Map.of("conf", conf, "logical_date", Instant.now().toString());
        Map<String, Object> result = airflowClient.triggerDag(dagId, payload).orElse(Map.of("status", "queued"));
        try {
            externalRunLogService.recordAirflowRun(ExternalRunLogService.ENTRY_DBT, dagId, result, conf, activeDept);
        } catch (RuntimeException ex) {
            // best-effort sync
        }
        return ApiResponses.ok(result);
    }

    private String resolveSelector(DbtRunRequest request, boolean required) {
        String selector = request == null ? null : stringVal(request.models());
        if (required && !StringUtils.hasText(selector)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供模型选择器");
        }
        return StringUtils.hasText(selector) ? selector : "all";
    }

    private String resolveDagSelector(DbtRunRequest request, String selector) {
        String dagSelector = request == null ? null : stringVal(request.dagSelector());
        if (StringUtils.hasText(dagSelector)) {
            return dagSelector;
        }
        if (StringUtils.hasText(selector) && selector.trim().toLowerCase().contains("tag:")) {
            return selector;
        }
        if (StringUtils.hasText(selector)) {
            return "tag:dbt " + selector;
        }
        return "tag:dbt";
    }

    private String toJsonString(Map<String, Object> vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (JsonProcessingException ex) {
            return String.valueOf(vars);
        }
    }

    private Map<String, Object> normalizeTriggerPayload(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return Map.of("logical_date", Instant.now().toString());
        }
        if (body.containsKey("conf") || body.containsKey("logical_date") || body.containsKey("data_interval_start")) {
            return body;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conf", body);
        payload.put("logical_date", Instant.now().toString());
        return payload;
    }

    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap(map));
            }
        }
        return out;
    }

    private Map<String, Object> firstMap(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return new LinkedHashMap(map);
        }
        return null;
    }

    private String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String defaultString(Object value, Object fallback) {
        String text = stringVal(value);
        if (text != null) {
            return text;
        }
        return stringVal(fallback);
    }
}
