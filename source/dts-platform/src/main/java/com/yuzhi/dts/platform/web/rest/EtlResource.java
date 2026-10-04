package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.etl.DbtModelDiagnosticsService;
import com.yuzhi.dts.platform.service.etl.DbtAssetSyncService;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import com.yuzhi.dts.platform.service.etl.DbtRunResultService;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.governance.IndicatorRunTracker;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/etl")
public class EtlResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String AIRFLOW_SYNC_EXPRESSION =
        INFRA_MAINTAINER_EXPRESSION +
        " or (hasAuthority('" +
        AuthoritiesConstants.SERVICE_INTERNAL +
        "') and authentication.name == 'service:dts-airflow')";

    private final DbtConfigService dbtConfigService;
    private final DbtManifestService manifestService;
    private final DbtModelDiagnosticsService dbtModelDiagnosticsService;
    private final DbtSourceService dbtSourceService;
    private final DbtAssetSyncService dbtAssetSyncService;
    private final DbtRunResultService dbtRunResultService;
    private final DbtArtifactSyncState dbtArtifactSyncState;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;
    private final ExternalRunLogService externalRunLogService;
    private final AuditService auditService;
    private final IndicatorRunTracker indicatorRunTracker;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EtlResource.class);

    public EtlResource(
        DbtConfigService dbtConfigService,
        DbtManifestService manifestService,
        DbtModelDiagnosticsService dbtModelDiagnosticsService,
        DbtSourceService dbtSourceService,
        DbtAssetSyncService dbtAssetSyncService,
        DbtRunResultService dbtRunResultService,
        DbtArtifactSyncState dbtArtifactSyncState,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties,
        ExternalRunLogService externalRunLogService,
        AuditService auditService,
        IndicatorRunTracker indicatorRunTracker
    ) {
        this.dbtConfigService = dbtConfigService;
        this.manifestService = manifestService;
        this.dbtModelDiagnosticsService = dbtModelDiagnosticsService;
        this.dbtSourceService = dbtSourceService;
        this.dbtAssetSyncService = dbtAssetSyncService;
        this.dbtRunResultService = dbtRunResultService;
        this.dbtArtifactSyncState = dbtArtifactSyncState;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
        this.externalRunLogService = externalRunLogService;
        this.auditService = auditService;
        this.indicatorRunTracker = indicatorRunTracker;
    }

    @GetMapping("/dbt/config")
    public ApiResponse<DbtConfigService.DbtConfigView> getDbtConfig() {
        ApiResponse<DbtConfigService.DbtConfigView> response = ApiResponses.ok(dbtConfigService.loadConfig());
        auditService.auditAction("ETL_DBT_CONFIG_READ", AuditStage.SUCCESS, "view", null);
        return response;
    }

    @PutMapping("/dbt/config")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<DbtConfigService.DbtConfigView> updateDbtConfig(
        @RequestBody DbtConfigService.DbtWorkspaceConfigRequest request
    ) {
        ApiResponse<DbtConfigService.DbtConfigView> response = ApiResponses.ok(dbtConfigService.saveConfig(request));
        auditService.auditAction("ETL_DBT_CONFIG_UPDATE", AuditStage.SUCCESS, "save", null);
        return response;
    }

    @GetMapping("/dbt/models")
    public ApiResponse<DbtManifestService.DbtModelResult> listDbtModels() {
        ApiResponse<DbtManifestService.DbtModelResult> response = ApiResponses.ok(manifestService.listModels());
        auditService.auditAction("ETL_DBT_MODELS_READ", AuditStage.SUCCESS, "list", null);
        return response;
    }

    @GetMapping("/dbt/models/{model}/diagnostics")
    public ApiResponse<DbtModelDiagnosticsService.ModelDiagnostics> diagnoseDbtModel(@PathVariable String model) {
        ApiResponse<DbtModelDiagnosticsService.ModelDiagnostics> response = ApiResponses.ok(dbtModelDiagnosticsService.diagnose(model));
        auditService.auditAction("ETL_DBT_MODEL_DIAGNOSTICS_READ", AuditStage.SUCCESS, model, null);
        return response;
    }

    @PostMapping("/dbt/sources/refresh")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<DbtSourceService.DbtSourceRefreshResult> refreshDbtSources() {
        ApiResponse<DbtSourceService.DbtSourceRefreshResult> response = ApiResponses.ok(dbtSourceService.refreshOdsSources());
        auditService.auditAction("ETL_DBT_SOURCES_EXECUTE", AuditStage.SUCCESS, "refresh", null);
        return response;
    }

    @PostMapping("/dbt/models/sync")
    @PreAuthorize(AIRFLOW_SYNC_EXPRESSION)
    public ApiResponse<DbtAssetSyncService.DbtAssetSyncResult> syncDbtModels(
        @RequestBody(required = false) DbtArtifactSyncRequest request
    ) {
        String projectDir = normalizeText(request == null ? null : request.projectDir());
        boolean syncManifest = request == null || request.syncManifest() == null || request.syncManifest();
        DbtAssetSyncService.DbtAssetSyncResult assetResult = syncManifest
            ? (StringUtils.hasText(projectDir) ? dbtAssetSyncService.syncFromManifest(projectDir) : dbtAssetSyncService.syncFromManifest())
            : new DbtAssetSyncService.DbtAssetSyncResult(true, false, "已跳过 scoped manifest 同步", null, new DbtAssetSyncService.SyncStats());
        DbtRunResultService.DbtRunSyncResult runResult = StringUtils.hasText(projectDir)
            ? dbtRunResultService.syncFromRunResults(projectDir)
            : dbtRunResultService.syncFromRunResults();
        recordDbtSyncState(assetResult, runResult);
        // 指标运行追踪：dbt run 完成后采集指标计算值
        if (runResult.synced()) {
            try {
                indicatorRunTracker.captureResults(runResult.invocationId());
            } catch (Exception ex) {
                LOG.warn("Indicator run capture failed: {}", ex.getMessage());
            }
        }
        auditService.auditAction("ETL_DBT_MODELS_EXECUTE", AuditStage.SUCCESS, "sync", null);
        auditService.auditAction(
            "ETL_DBT_RUNS_EXECUTE",
            runResult.synced() ? AuditStage.SUCCESS : AuditStage.FAIL,
            "sync",
            runResult
        );
        return ApiResponses.ok(assetResult);
    }

    @GetMapping("/dbt/sync/status")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<DbtArtifactSyncState.DbtArtifactSyncStatus> getDbtSyncStatus(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        syncDbtBuildRuns(activeDept);
        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestBuildSummary(20);
        if (latestRun != null) {
            dbtArtifactSyncState.recordLatestRun(latestRun);
        }
        return ApiResponses.ok(dbtArtifactSyncState.snapshot());
    }

    @GetMapping("/dbt/runs")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> listDbtRuns(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String dagId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String effectiveDagId = resolveRunDagId(dagId);
        Map<String, Object> payload = new LinkedHashMap<>(
            airflowClient.listDagRuns(effectiveDagId, Math.max(1, Math.min(limit, 50))).orElse(Map.of())
        );
        payload.putIfAbsent("dagId", effectiveDagId);
        try {
            externalRunLogService.syncAirflowRuns(ExternalRunLogService.ENTRY_DBT, effectiveDagId, payload, activeDept);
        } catch (RuntimeException ex) {
            // best-effort
        }
        auditService.auditAction("ETL_DBT_RUNS_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/dbt/runs/{dagRunId}/logs")
    public ApiResponse<Map<String, Object>> getDbtRunLog(
        @PathVariable String dagRunId,
        @RequestParam(required = false) String dagId,
        @RequestParam(defaultValue = "dbt_run") String taskId,
        @RequestParam(defaultValue = "1") int tryNumber
    ) {
        if (!airflowProperties.isEnabled()) {
            return ApiResponses.error("Airflow integration is not enabled");
        }
        // Resolve dagId: use parameter if provided, otherwise fall back to configured default
        if (!StringUtils.hasText(dagId)) {
            dagId = airflowProperties.getDagId();
        }
        int safeTryNumber = Math.max(1, tryNumber);
        String log = airflowClient.getTaskInstanceLog(dagId, dagRunId, taskId, safeTryNumber);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dagId", dagId);
        result.put("dagRunId", dagRunId);
        result.put("taskId", taskId);
        result.put("tryNumber", safeTryNumber);
        result.put("log", log != null ? log : "");
        auditService.auditAction("ETL_DBT_LOGS_READ", AuditStage.SUCCESS, dagRunId, null);
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
        auditService.auditAction("ETL_AIRFLOW_JOBS_READ", AuditStage.SUCCESS, "list", null);
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
        auditService.auditAction("ETL_AIRFLOW_RUNS_READ", AuditStage.SUCCESS, dagId, null);
        return ApiResponses.ok(payload);
    }

    @PostMapping("/airflow/jobs/{dagId}/trigger")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> triggerAirflowJob(
        @PathVariable String dagId,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> payload = normalizeTriggerPayload(body);
        Map<String, Object> result = triggerAirflowDagOrThrow(dagId, payload);
        try {
            externalRunLogService.recordAirflowRun(ExternalRunLogService.ENTRY_AIRFLOW, dagId, result, payload, activeDept);
        } catch (RuntimeException ex) {
            // best-effort sync
        }
        auditService.auditAction("ETL_AIRFLOW_TRIGGER_EXECUTE", AuditStage.SUCCESS, dagId, null);
        return ApiResponses.ok(result);
    }

    @GetMapping("/airflow/jobs/{dagId}/runs/{dagRunId}/tasks")
    public ApiResponse<com.fasterxml.jackson.databind.JsonNode> listAirflowTaskInstances(
        @PathVariable String dagId,
        @PathVariable String dagRunId
    ) {
        if (!airflowProperties.isEnabled()) {
            return ApiResponses.error("Airflow integration is not enabled");
        }
        com.fasterxml.jackson.databind.JsonNode result = airflowClient.listTaskInstances(dagId, dagRunId);
        auditService.auditAction("ETL_AIRFLOW_TASK_INSTANCES_READ", AuditStage.SUCCESS, dagId + "/" + dagRunId, null);
        return ApiResponses.ok(result);
    }

    @GetMapping("/airflow/jobs/{dagId}/runs/{dagRunId}/task-logs")
    public ApiResponse<Map<String, Object>> getAirflowTaskLog(
        @PathVariable String dagId,
        @PathVariable String dagRunId,
        @RequestParam String taskId,
        @RequestParam(defaultValue = "1") int tryNumber
    ) {
        if (!airflowProperties.isEnabled()) {
            return ApiResponses.error("Airflow integration is not enabled");
        }
        if (!StringUtils.hasText(taskId)) {
            return ApiResponses.error("taskId is required");
        }
        int safeTryNumber = Math.max(1, tryNumber);
        String log = airflowClient.getTaskInstanceLog(dagId, dagRunId, taskId, safeTryNumber);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dagId", dagId);
        result.put("dagRunId", dagRunId);
        result.put("taskId", taskId);
        result.put("tryNumber", safeTryNumber);
        result.put("log", log != null ? log : "");
        auditService.auditAction("ETL_AIRFLOW_TASK_LOGS_READ", AuditStage.SUCCESS, dagId + "/" + dagRunId + "/" + taskId, null);
        return ApiResponses.ok(result);
    }

    public record DbtArtifactSyncRequest(String projectDir, Boolean syncManifest) {}

    private void syncDbtBuildRuns(String activeDept) {
        if (!airflowProperties.isEnabled()) {
            return;
        }
        String dagId = airflowProperties.getDagId();
        if (!StringUtils.hasText(dagId)) {
            return;
        }
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, 10).orElse(Map.of());
        if (payload.isEmpty()) {
            return;
        }
        try {
            externalRunLogService.syncAirflowRuns(
                ExternalRunLogService.ENTRY_DBT,
                dagId,
                payload,
                StringUtils.hasText(activeDept) ? activeDept : Constants.SYSTEM
            );
        } catch (RuntimeException ex) {
            // best-effort sync
        }
    }

    private Map<String, Object> triggerAirflowDagOrThrow(String dagId, Map<String, Object> payload) {
        Map<String, Object> dag = findDag(dagId);
        if (dag == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "DAG [" + dagId + "] 尚未在 Airflow 中注册，请稍后重试（通常需要 30 秒）");
        }
        ensureDagActive(dagId, dag);
        try {
            return airflowClient
                .triggerDag(dagId, payload)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 触发失败: " + dagId));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            LOG.warn("Airflow DAG trigger failed for {}: {}", dagId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    private Map<String, Object> waitForDagRegistration(String dagId) {
        int waitSeconds = Math.max(0, airflowProperties.getDagReadyWaitSeconds());
        if (waitSeconds <= 0 || !StringUtils.hasText(dagId) || !airflowProperties.isEnabled()) {
            return findDag(dagId);
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(waitSeconds));
        int pollSeconds = Math.max(0, airflowProperties.getDagReadyPollSeconds());
        while (true) {
            Map<String, Object> dag = findDag(dagId);
            if (dag != null) {
                return dag;
            }
            if (!Instant.now().isBefore(deadline)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "DAG [" + dagId + "] 尚未在 Airflow 中注册，请稍后重试（通常需要 30 秒）");
            }
            if (pollSeconds > 0) {
                try {
                    Thread.sleep(Duration.ofSeconds(pollSeconds).toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "等待 DAG [" + dagId + "] 注册时被中断，请稍后重试");
                }
            }
        }
    }

    private Map<String, Object> findDag(String dagId) {
        if (!StringUtils.hasText(dagId)) {
            return null;
        }
        try {
            Map<String, Object> dag = airflowClient.getDag(dagId).orElse(null);
            if (dag != null) {
                return dag;
            }
        } catch (AirflowClient.AirflowApiException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 查询失败: " + ex.getMessage(), ex);
        }
        Map<String, Object> dagList = airflowClient.listDags(200).orElse(null);
        if (dagList == null) {
            return null;
        }
        Object dags = dagList.get("dags");
        if (!(dags instanceof List<?> dagEntries)) {
            return null;
        }
        for (Object entry : dagEntries) {
            if (entry instanceof Map<?, ?> dag && dagId.equals(stringVal(dag.get("dag_id")))) {
                Map<String, Object> matchedDag = new LinkedHashMap<>();
                dag.forEach((key, value) -> {
                    if (key != null) {
                        matchedDag.put(String.valueOf(key), value);
                    }
                });
                return matchedDag;
            }
        }
        return null;
    }

    private void ensureDagActive(String dagId, Map<String, Object> dag) {
        if (dag == null || !boolVal(dag.get("is_paused"))) {
            return;
        }
        airflowClient
            .setDagPaused(dagId, false)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 解锁失败: " + dagId));
    }

    private boolean boolVal(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(stringVal(value));
    }

    private String resolveRunDagId(String dagId) {
        if (StringUtils.hasText(dagId)) {
            return dagId.trim();
        }
        return airflowProperties.getDagId();
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
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
