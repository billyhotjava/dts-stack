package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import java.time.Instant;
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

    public record DbtRunRequest(String models, String target, Map<String, Object> vars) {}
}
