package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.AirflowProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Canonical dbt execution adapter over the repository's single Airflow client. */
@Component
public class AirflowDbtExecutionGateway implements DbtExecutionGateway {

    private final AirflowClient airflow;
    private final AirflowProperties properties;

    public AirflowDbtExecutionGateway(
        AirflowClient airflow,
        AirflowProperties properties
    ) {
        this.airflow = airflow;
        this.properties = properties;
    }

    @Override
    public SubmissionResult submitReleaseBuild(
        ReleaseBuildRequest request
    ) {
        if (!properties.isEnabled()) {
            return SubmissionResult.blocked(
                "MODEL_AIRFLOW_DISABLED"
            );
        }
        Map<String, Object> dag;
        try {
            dag = airflow.getDag(request.dagId()).orElse(null);
        } catch (RuntimeException unavailable) {
            return SubmissionResult.retryableUnknown(
                request.dagRunId(),
                "MODEL_AIRFLOW_UNAVAILABLE"
            );
        }
        if (dag == null) {
            return SubmissionResult.blocked(
                "MODEL_AIRFLOW_DAG_NOT_REGISTERED"
            );
        }
        if (Boolean.parseBoolean(String.valueOf(dag.get("is_paused")))) {
            return SubmissionResult.blocked(
                "MODEL_AIRFLOW_DAG_PAUSED"
            );
        }

        Optional<Map<String, Object>> existing = lookup(request);
        if (existing.isPresent()) {
            return recovered(request, existing.orElseThrow());
        }

        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put(
            "pipelineRunGroupId",
            request.pipelineRunGroupId().toString()
        );
        conf.put("candidateId", request.candidateId().toString());
        conf.put("candidateVersion", request.candidateVersion());
        conf.put("attempt", request.attempt());
        conf.put("runPurpose", "RELEASE_BUILD");
        conf.put("runtimeSpecToken", request.runtimeSpecToken());
        conf.put("bundleChecksum", request.bundleChecksum());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dag_run_id", request.dagRunId());
        payload.put("conf", Map.copyOf(conf));
        try {
            Map<String, Object> response = airflow
                .triggerDag(request.dagId(), Map.copyOf(payload))
                .orElse(null);
            if (response == null) {
                return SubmissionResult.blocked(
                    "MODEL_AIRFLOW_DISABLED"
                );
            }
            String actualRunId = runId(response);
            if (!request.dagRunId().equals(actualRunId)) {
                return SubmissionResult.retryableUnknown(
                    request.dagRunId(),
                    "MODEL_AIRFLOW_RUN_ID_MISMATCH"
                );
            }
            return SubmissionResult.submitted(actualRunId, false);
        } catch (RuntimeException uncertain) {
            Optional<Map<String, Object>> recovered = lookup(request);
            if (recovered.isPresent()) {
                return recovered(request, recovered.orElseThrow());
            }
            return SubmissionResult.retryableUnknown(
                request.dagRunId(),
                "MODEL_AIRFLOW_TRIGGER_UNKNOWN"
            );
        }
    }

    private Optional<Map<String, Object>> lookup(
        ReleaseBuildRequest request
    ) {
        try {
            return airflow.getDagRun(
                request.dagId(),
                request.dagRunId()
            );
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private SubmissionResult recovered(
        ReleaseBuildRequest request,
        Map<String, Object> existing
    ) {
        String actualRunId = runId(existing);
        if (!request.dagRunId().equals(actualRunId)) {
            return SubmissionResult.retryableUnknown(
                request.dagRunId(),
                "MODEL_AIRFLOW_RUN_ID_MISMATCH"
            );
        }
        return SubmissionResult.submitted(actualRunId, true);
    }

    private static String runId(Map<String, Object> value) {
        if (value == null) return null;
        Object runId = value.get("dag_run_id");
        if (runId == null) runId = value.get("run_id");
        return runId == null ? null : String.valueOf(runId).trim();
    }
}
