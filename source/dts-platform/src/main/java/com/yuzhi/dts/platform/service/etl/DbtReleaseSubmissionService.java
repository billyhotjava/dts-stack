package com.yuzhi.dts.platform.service.etl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.service.ops.ExternalRunLogService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DbtReleaseSubmissionService {

    private final DbtQualityGateService qualityGateService;
    private final DbtReleaseGateService releaseGateService;
    private final DbtDagService dbtDagService;
    private final AirflowClient airflowClient;
    private final AirflowProperties airflowProperties;
    private final ExternalRunLogService externalRunLogService;
    private final ObjectMapper objectMapper;

    public DbtReleaseSubmissionService(
        DbtQualityGateService qualityGateService,
        DbtReleaseGateService releaseGateService,
        DbtDagService dbtDagService,
        AirflowClient airflowClient,
        AirflowProperties airflowProperties,
        ExternalRunLogService externalRunLogService,
        ObjectMapper objectMapper
    ) {
        this.qualityGateService = qualityGateService;
        this.releaseGateService = releaseGateService;
        this.dbtDagService = dbtDagService;
        this.airflowClient = airflowClient;
        this.airflowProperties = airflowProperties;
        this.externalRunLogService = externalRunLogService;
        this.objectMapper = objectMapper;
    }

    public DbtReleaseSubmitResult submit(DbtReleaseSubmitRequest request, String activeDept) {
        String selector = resolveSelector(request == null ? null : request.models());
        String dagSelector = resolveDagSelector(selector);
        String dagId = resolveDagId(dagSelector);

        if (!airflowProperties.isEnabled()) {
            return blocked(selector, dagId, List.of("Airflow 集成未启用"), null, null);
        }
        if (!StringUtils.hasText(dagId)) {
            return blocked(selector, dagId, List.of("未能解析可用的 Airflow DAG"), null, null);
        }

        Map<String, Object> dag = findDag(dagId);
        if (dag == null) {
            return blocked(
                selector,
                dagId,
                List.of("DAG [" + dagId + "] 尚未在 Airflow 中注册，请稍后重试（通常需要 30 秒）"),
                null,
                null
            );
        }

        DbtQualityGateService.DbtQualityGateResult qualityGate = qualityGateService.evaluate(selector);
        if (qualityGate != null && qualityGate.blocking()) {
            return blocked(selector, dagId, safeList(qualityGate.blockers()), qualityGate, null);
        }

        DbtReleaseGateService.DbtReleaseGateResult releaseGate = releaseGateService.evaluate(
            selector,
            request == null ? null : request.gitRef(),
            request == null ? null : request.commitSha(),
            request == null ? null : request.strictMode()
        );
        if (releaseGate != null && releaseGate.blocking()) {
            return blocked(selector, dagId, safeList(releaseGate.blockers()), qualityGate, releaseGate);
        }

        List<String> warnings = mergeWarnings(qualityGate, releaseGate);
        boolean confirmWarnings = request != null && Boolean.TRUE.equals(request.confirmWarnings());
        if (!warnings.isEmpty() && !confirmWarnings) {
            return warning(selector, dagId, warnings, qualityGate, releaseGate);
        }

        ensureDagActive(dagId, dag);

        Map<String, Object> conf = buildTriggerConf(selector, request, releaseGate);
        Map<String, Object> payload = Map.of("conf", conf, "logical_date", Instant.now().toString());
        Map<String, Object> triggerResult = airflowClient
            .triggerDag(dagId, payload)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Airflow DAG 触发失败: " + dagId));

        try {
            externalRunLogService.recordAirflowRun(
                ExternalRunLogService.ENTRY_DBT,
                dagId,
                triggerResult,
                conf,
                StringUtils.hasText(activeDept) ? activeDept : Constants.SYSTEM
            );
        } catch (RuntimeException ignored) {
            // best-effort sync
        }

        String dagRunId = normalizeText(stringVal(triggerResult.get("dag_run_id")));
        if (!StringUtils.hasText(dagRunId)) {
            dagRunId = normalizeText(stringVal(triggerResult.get("run_id")));
        }
        return new DbtReleaseSubmitResult(
            selector,
            "SUBMITTED",
            false,
            !warnings.isEmpty(),
            List.of(),
            warnings,
            dagId,
            dagRunId,
            qualityGate,
            releaseGate,
            releaseGate == null ? null : releaseGate.buildEvidence()
        );
    }

    private DbtReleaseSubmitResult blocked(
        String selector,
        String dagId,
        List<String> blockers,
        DbtQualityGateService.DbtQualityGateResult qualityGate,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate
    ) {
        return new DbtReleaseSubmitResult(
            selector,
            "BLOCKED",
            true,
            false,
            safeList(blockers),
            mergeWarnings(qualityGate, releaseGate),
            dagId,
            null,
            qualityGate,
            releaseGate,
            releaseGate == null ? null : releaseGate.buildEvidence()
        );
    }

    private DbtReleaseSubmitResult warning(
        String selector,
        String dagId,
        List<String> warnings,
        DbtQualityGateService.DbtQualityGateResult qualityGate,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate
    ) {
        return new DbtReleaseSubmitResult(
            selector,
            "WARNING",
            false,
            true,
            List.of(),
            safeList(warnings),
            dagId,
            null,
            qualityGate,
            releaseGate,
            releaseGate == null ? null : releaseGate.buildEvidence()
        );
    }

    private List<String> mergeWarnings(
        DbtQualityGateService.DbtQualityGateResult qualityGate,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate
    ) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(safeList(qualityGate == null ? null : qualityGate.warnings()));
        merged.addAll(safeList(releaseGate == null ? null : releaseGate.warnings()));
        return new ArrayList<>(merged);
    }

    private List<String> safeList(List<String> input) {
        return input == null ? List.of() : input.stream().filter(StringUtils::hasText).toList();
    }

    private Map<String, Object> buildTriggerConf(
        String selector,
        DbtReleaseSubmitRequest request,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate
    ) {
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("operation", "build");
        if (StringUtils.hasText(selector)) {
            conf.put("models", expandBuildSelector(selector));
            conf.put("dagSelector", selector);
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
        String invocationId = releaseGate != null && releaseGate.buildEvidence() != null
            ? normalizeText(releaseGate.buildEvidence().invocationId()) : null;
        if (StringUtils.hasText(invocationId)) {
            conf.put("buildInvocationId", invocationId);
        }
        return conf;
    }

    private String resolveDagId(String dagSelector) {
        String dagId = dbtDagService.ensureDagForSelector(dagSelector);
        if (!StringUtils.hasText(dagId)) {
            dagId = airflowProperties.getDagId();
        }
        return normalizeText(dagId);
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

    private String resolveSelector(String selector) {
        return StringUtils.hasText(selector) ? selector.trim() : "all";
    }

    private String resolveDagSelector(String selector) {
        if (StringUtils.hasText(selector) && selector.trim().toLowerCase().contains("tag:")) {
            return selector.trim();
        }
        if (StringUtils.hasText(selector)) {
            return "tag:dbt " + selector.trim();
        }
        return "tag:dbt";
    }

    private String expandBuildSelector(String selector) {
        String normalized = normalizeText(selector);
        if (!StringUtils.hasText(normalized) || "all".equalsIgnoreCase(normalized)) {
            return StringUtils.hasText(normalized) ? normalized : "all";
        }
        if (normalized.startsWith("+")) {
            return normalized;
        }
        if (
            normalized.startsWith("tag:") ||
            normalized.startsWith("model:") ||
            normalized.startsWith("source:")
        ) {
            return "+" + normalized;
        }
        return normalized;
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }

    private String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJsonString(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "运行变量必须是合法 JSON 对象", ex);
        }
    }

    public record DbtReleaseSubmitRequest(
        String models,
        String target,
        Map<String, Object> vars,
        String gitRef,
        String commitSha,
        Boolean strictMode,
        Boolean confirmWarnings
    ) {}

    public record DbtReleaseSubmitResult(
        String selector,
        String status,
        boolean blocking,
        boolean warning,
        List<String> blockers,
        List<String> warnings,
        String dagId,
        String dagRunId,
        DbtQualityGateService.DbtQualityGateResult qualityGate,
        DbtReleaseGateService.DbtReleaseGateResult releaseGate,
        DbtReleaseGateService.BuildEvidence buildEvidence
    ) {}
}
