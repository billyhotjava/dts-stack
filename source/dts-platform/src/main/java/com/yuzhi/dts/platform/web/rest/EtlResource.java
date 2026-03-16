package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.etl.DbtAssetSyncService;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtPreviewService;
import com.yuzhi.dts.platform.service.etl.DbtOutputRelationService;
import com.yuzhi.dts.platform.service.etl.DbtArtifactSyncState;
import com.yuzhi.dts.platform.service.etl.DbtRunResultService;
import com.yuzhi.dts.platform.service.etl.DbtQualityGateService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import com.yuzhi.dts.platform.service.topic.TopicBindingRuntimeService;
import java.time.Duration;
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
    private final DbtOutputRelationService dbtOutputRelationService;
    private final DbtRunResultService dbtRunResultService;
    private final DbtQualityGateService dbtQualityGateService;
    private final DbtReleaseGateService dbtReleaseGateService;
    private final TopicBindingRuntimeService topicBindingRuntimeService;
    private final DbtArtifactSyncState dbtArtifactSyncState;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;
    private final ExternalRunLogService externalRunLogService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ModelingSqlModelRepository sqlModelRepository;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EtlResource.class);

    public EtlResource(
        DbtConfigService dbtConfigService,
        DbtManifestService manifestService,
        DbtSourceService dbtSourceService,
        DbtAssetSyncService dbtAssetSyncService,
        DbtDagService dbtDagService,
        DbtPreviewService dbtPreviewService,
        DbtOutputRelationService dbtOutputRelationService,
        DbtRunResultService dbtRunResultService,
        DbtQualityGateService dbtQualityGateService,
        DbtReleaseGateService dbtReleaseGateService,
        TopicBindingRuntimeService topicBindingRuntimeService,
        DbtArtifactSyncState dbtArtifactSyncState,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties,
        ExternalRunLogService externalRunLogService,
        AuditService auditService,
        ObjectMapper objectMapper,
        ModelingSqlModelRepository sqlModelRepository
    ) {
        this.dbtConfigService = dbtConfigService;
        this.manifestService = manifestService;
        this.dbtSourceService = dbtSourceService;
        this.dbtAssetSyncService = dbtAssetSyncService;
        this.dbtDagService = dbtDagService;
        this.dbtPreviewService = dbtPreviewService;
        this.dbtOutputRelationService = dbtOutputRelationService;
        this.dbtRunResultService = dbtRunResultService;
        this.dbtQualityGateService = dbtQualityGateService;
        this.dbtReleaseGateService = dbtReleaseGateService;
        this.topicBindingRuntimeService = topicBindingRuntimeService;
        this.dbtArtifactSyncState = dbtArtifactSyncState;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
        this.externalRunLogService = externalRunLogService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.sqlModelRepository = sqlModelRepository;
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

    @GetMapping("/dbt/output")
    public ApiResponse<DbtOutputRelationService.DbtOutputRelationSummary> analyzeDbtOutputRelation(
        @RequestParam java.util.UUID modelId
    ) {
        try {
            ApiResponse<DbtOutputRelationService.DbtOutputRelationSummary> response = ApiResponses.ok(
                dbtOutputRelationService.analyze(modelId)
            );
            auditService.audit("READ", "etl.dbt.output", String.valueOf(modelId));
            return response;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/dbt/output/truncate")
    public ApiResponse<DbtOutputRelationService.DbtOutputRelationActionResult> truncateDbtOutputRelation(
        @RequestBody DbtOutputRelationRequest request
    ) {
        try {
            ApiResponse<DbtOutputRelationService.DbtOutputRelationActionResult> response = ApiResponses.ok(
                dbtOutputRelationService.truncate(request.modelId())
            );
            auditService.audit("EXECUTE", "etl.dbt.output.truncate", String.valueOf(request.modelId()));
            return response;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/dbt/output/rebuild")
    public ApiResponse<Map<String, Object>> rebuildDbtOutputRelation(
        @RequestBody DbtOutputRelationRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        try {
            DbtOutputRelationService.DbtOutputRelationActionResult prepare = dbtOutputRelationService.prepareRebuild(request.modelId());
            String selector = prepare.selector();
            ApiResponse<Map<String, Object>> response = triggerDbtOperation(
                "build",
                new DbtRunRequest(selector, selector, request.target(), "build", request.vars(), null, null, null),
                activeDept,
                true
            );
            Map<String, Object> payload = new LinkedHashMap<>(response.getData());
            payload.put("relation", prepare.qualifiedName());
            payload.put("selector", selector);
            payload.put("dropExecuted", prepare.executed());
            payload.put("dropMessage", prepare.message());
            auditService.audit("EXECUTE", "etl.dbt.output.rebuild", String.valueOf(request.modelId()));
            return ApiResponses.ok(payload);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
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
    public ApiResponse<DbtArtifactSyncState.DbtArtifactSyncStatus> getDbtSyncStatus(
        @RequestParam(required = false) String models,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        syncDbtBuildRuns(models, activeDept);
        DbtRunResultService.DbtRunSummary latestRun = dbtRunResultService.loadLatestBuildSummary(20);
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

        // After successful publish/build trigger: auto-sync models + update status to PUBLISHED
        if ("run".equals(operation) || "build".equals(operation)) {
            try {
                dbtAssetSyncService.syncFromManifest();
                dbtRunResultService.syncFromRunResults();
                updateModelStatus("PUBLISHED", selector);
                LOG.info("[dbt-lifecycle] Auto-synced and updated models to PUBLISHED after dbt {}", operation);
            } catch (RuntimeException ex) {
                LOG.warn("[dbt-lifecycle] Auto-sync after dbt {} failed: {}", operation, ex.getMessage());
            }
        }
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
        String selector = resolveSelector(request, false);
        ApiResponse<Map<String, Object>> response = triggerDbtOperation("test", request, activeDept, false);
        auditService.audit("EXECUTE", "etl.dbt.test", selector);

        // After successful test trigger: update status to TESTED
        try {
            updateModelStatus("TESTED", selector);
            LOG.info("[dbt-lifecycle] Updated models to TESTED after dbt test");
        } catch (RuntimeException ex) {
            LOG.warn("[dbt-lifecycle] Failed to update model status after test: {}", ex.getMessage());
        }
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
        @RequestBody(required = false) DbtReleaseGateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String selector = request == null ? null : request.models();
        syncDbtBuildRuns(selector, activeDept);
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
        Map<String, Object> result = triggerAirflowDagOrThrow(dagId, payload);
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
    public record DbtOutputRelationRequest(java.util.UUID modelId, String target, Map<String, Object> vars) {}
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
        dbtSourceService.refreshOdsSources();
        TopicBindingRuntimeService.RuntimeCompilationResult topicRuntime = topicBindingRuntimeService.compileRuntimeArtifacts();
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
        Map<String, Object> mergedVars = new LinkedHashMap<>();
        if (request != null && request.vars() != null && !request.vars().isEmpty()) {
            mergedVars.putAll(request.vars());
        }
        if (topicRuntime != null && topicRuntime.vars() != null && !topicRuntime.vars().isEmpty()) {
            mergedVars.putAll(topicRuntime.vars());
        }
        if (!mergedVars.isEmpty()) {
            conf.put("vars", toJsonString(mergedVars));
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
        Map<String, Object> result = new LinkedHashMap<>(triggerAirflowDagOrThrow(dagId, payload));
        result.putIfAbsent("dagId", dagId);
        try {
            externalRunLogService.recordAirflowRun(ExternalRunLogService.ENTRY_DBT, dagId, result, conf, activeDept);
        } catch (RuntimeException ex) {
            // best-effort sync
        }
        return ApiResponses.ok(result);
    }

    private void syncDbtBuildRuns(String selector, String activeDept) {
        if (!airflowProperties.isEnabled()) {
            return;
        }
        String dagSelector = resolveDagSelector(null, selector);
        String dagId = dbtDagService.ensureDagForSelector(dagSelector);
        if (!StringUtils.hasText(dagId)) {
            dagId = airflowProperties.getDagId();
        }
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
        Map<String, Object> dag = waitForDagRegistration(dagId);
        ensureDagActive(dagId, dag);
        return airflowClient
            .triggerDag(dagId, payload)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 触发失败: " + dagId));
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
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 尚未注册完成: " + dagId);
            }
            if (pollSeconds > 0) {
                try {
                    Thread.sleep(Duration.ofSeconds(pollSeconds).toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "等待 Airflow DAG 注册时被中断: " + dagId);
                }
            }
        }
    }

    private Map<String, Object> findDag(String dagId) {
        Map<String, Object> payload = airflowClient.listDags(200).orElse(Map.of());
        for (Map<String, Object> dag : asListOfMaps(payload.get("dags"))) {
            if (dagId.equals(stringVal(dag.get("dag_id")))) {
                return dag;
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

    /**
     * Update model lifecycle status for models matching the given selector.
     * If selector is "all", updates all models. Otherwise matches by tag or layer.
     */
    private void updateModelStatus(String newStatus, String selector) {
        List<ModelingSqlModel> models;
        String normalizedSelector = StringUtils.hasText(selector) ? selector.trim() : selector;
        while (StringUtils.hasText(normalizedSelector) && normalizedSelector.startsWith("+")) {
            normalizedSelector = normalizedSelector.substring(1).trim();
        }
        if ("all".equals(normalizedSelector) || !StringUtils.hasText(normalizedSelector)) {
            models = sqlModelRepository.findAll();
        } else {
            // For tag-based selectors like "tag:ods_crm", match models with that tag
            String tagValue = normalizedSelector.replace("tag:", "").trim();
            models = sqlModelRepository.findByTagsContainingIgnoreCase(tagValue);
            if (models.isEmpty()) {
                // Fallback: match by layer from selector
                models = sqlModelRepository.findByLayerIgnoreCase(tagValue);
            }
        }
        int updated = 0;
        for (ModelingSqlModel model : models) {
            String current = model.getStatus();
            boolean shouldUpgrade = switch (newStatus) {
                case "TESTED" -> "COMMITTED".equals(current) || "DRAFT".equals(current) || current == null;
                case "PUBLISHED" -> "TESTED".equals(current) || "COMMITTED".equals(current);
                default -> false;
            };
            if (shouldUpgrade) {
                model.setStatus(newStatus);
                updated++;
            }
        }
        if (updated > 0) {
            sqlModelRepository.saveAll(models);
            LOG.info("[dbt-lifecycle] Updated {} model(s) to {}", updated, newStatus);
        }
    }
}
