package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/etl")
public class EtlResource {

    private final DbtConfigService dbtConfigService;
    private final DbtManifestService manifestService;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;

    public EtlResource(
        DbtConfigService dbtConfigService,
        DbtManifestService manifestService,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties
    ) {
        this.dbtConfigService = dbtConfigService;
        this.manifestService = manifestService;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
    }

    @GetMapping("/dbt/config")
    public ApiResponse<DbtConfigService.DbtConfigView> getDbtConfig() {
        return ApiResponses.ok(dbtConfigService.loadConfig());
    }

    @PutMapping("/dbt/config")
    public ApiResponse<DbtConfigService.DbtConfigView> updateDbtConfig(
        @RequestBody DbtConfigService.DbtWorkspaceConfigRequest request
    ) {
        return ApiResponses.ok(dbtConfigService.saveConfig(request));
    }

    @GetMapping("/dbt/models")
    public ApiResponse<DbtManifestService.DbtModelResult> listDbtModels() {
        return ApiResponses.ok(manifestService.listModels());
    }

    @GetMapping("/dbt/runs")
    public ApiResponse<Map<String, Object>> listDbtRuns(@RequestParam(defaultValue = "20") int limit) {
        String dagId = airflowProperties.getDagId();
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, Math.max(1, Math.min(limit, 50))).orElse(Map.of());
        return ApiResponses.ok(payload);
    }

    @PostMapping("/dbt/run")
    public ApiResponse<Map<String, Object>> triggerDbtRun(@RequestBody DbtRunRequest request) {
        String dagId = airflowProperties.getDagId();
        if (request == null || (request.models() == null || request.models().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供模型选择器");
        }
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("models", request.models());
        if (request.target() != null && !request.target().isBlank()) {
            conf.put("target", request.target());
        }
        if (request.vars() != null && !request.vars().isEmpty()) {
            conf.put("vars", request.vars());
        }
        Map<String, Object> payload = Map.of("conf", conf, "logical_date", Instant.now().toString());
        return ApiResponses.ok(airflowClient.triggerDag(dagId, payload).orElse(Map.of("status", "queued")));
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
        return ApiResponses.ok(results);
    }

    @GetMapping("/airflow/jobs/{dagId}/runs")
    public ApiResponse<Map<String, Object>> listAirflowJobRuns(
        @PathVariable String dagId,
        @RequestParam(defaultValue = "20") int limit
    ) {
        Map<String, Object> payload = airflowClient.listDagRuns(dagId, Math.max(1, Math.min(limit, 200))).orElse(Map.of());
        return ApiResponses.ok(payload);
    }

    @PostMapping("/airflow/jobs/{dagId}/trigger")
    public ApiResponse<Map<String, Object>> triggerAirflowJob(
        @PathVariable String dagId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> payload = normalizeTriggerPayload(body);
        return ApiResponses.ok(airflowClient.triggerDag(dagId, payload).orElse(Map.of("status", "queued")));
    }

    public record DbtRunRequest(String models, String target, Map<String, Object> vars) {}

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
