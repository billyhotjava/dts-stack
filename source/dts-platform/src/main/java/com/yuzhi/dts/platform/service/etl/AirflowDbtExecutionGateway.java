package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.AirflowProperties;
import java.util.LinkedHashMap;
import java.util.Locale;
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

        Optional<SubmissionResult> existing = reconcileReleaseBuild(
            request
        );
        if (existing.isPresent()) return existing.orElseThrow();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dag_run_id", request.dagRunId());
        payload.put("conf", requestConf(request));
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
            return reconcileReleaseBuild(request)
                .orElseGet(() ->
                    SubmissionResult.retryableUnknown(
                        request.dagRunId(),
                        "MODEL_AIRFLOW_TRIGGER_UNKNOWN"
                    )
                );
        }
    }

    @Override
    public Optional<SubmissionResult> reconcileReleaseBuild(
        ReleaseBuildRequest request
    ) {
        if (!properties.isEnabled()) {
            return Optional.of(
                SubmissionResult.blocked("MODEL_AIRFLOW_DISABLED")
            );
        }
        try {
            return airflow
                .getDagRun(
                request.dagId(),
                request.dagRunId()
                )
                .map(existing -> recovered(request, existing));
        } catch (RuntimeException unavailable) {
            return Optional.of(
                SubmissionResult.retryableUnknown(
                    request.dagRunId(),
                    "MODEL_AIRFLOW_RECONCILIATION_UNAVAILABLE"
                )
            );
        }
    }

    private SubmissionResult recovered(
        ReleaseBuildRequest request,
        Map<String, Object> existing
    ) {
        String actualRunId = runId(existing);
        if (
            !request.dagRunId().equals(actualRunId) ||
            !requestConf(request).equals(existing.get("conf"))
        ) {
            return SubmissionResult.blocked(
                "MODEL_AIRFLOW_RUN_IDENTITY_CONFLICT"
            );
        }
        String state = String.valueOf(existing.get("state"))
            .trim()
            .toLowerCase(Locale.ROOT);
        if ("failed".equals(state)) {
            return SubmissionResult.terminalFailed(
                actualRunId,
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED"
            );
        }
        return SubmissionResult.submitted(actualRunId, true);
    }

    private static Map<String, Object> requestConf(
        ReleaseBuildRequest request
    ) {
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
        return Map.copyOf(conf);
    }

    private static String runId(Map<String, Object> value) {
        if (value == null) return null;
        Object runId = value.get("dag_run_id");
        if (runId == null) runId = value.get("run_id");
        return runId == null ? null : String.valueOf(runId).trim();
    }
}
