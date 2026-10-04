package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.AirflowProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Canonical dbt execution adapter over the repository's single Airflow client. */
@Component
public class AirflowDbtExecutionGateway implements DbtExecutionGateway {

    /** Client errors that may succeed when repeated: timeout, conflict, too early, rate limit. */
    private static final Set<Integer> RETRYABLE_CLIENT_STATUSES = Set.of(408, 409, 425, 429);

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
            dag = waitForDagRegistration(request.dagId());
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
            Optional<SubmissionResult> reconciled = reconcileReleaseBuild(request);
            if (reconciled.isPresent()) return reconciled.orElseThrow();
            // Airflow authoritatively has no run for this id. A deterministic rejection will be
            // answered the same way on every retry, so it must fail the build instead of looping.
            return deterministicRejectionCode(uncertain)
                .map(SubmissionResult::blocked)
                .orElseGet(() ->
                    SubmissionResult.retryableUnknown(
                        request.dagRunId(),
                        "MODEL_AIRFLOW_TRIGGER_UNKNOWN"
                    )
                );
        }
    }

    private static Optional<String> deterministicRejectionCode(
        RuntimeException failure
    ) {
        if (!(failure instanceof AirflowClient.AirflowApiException api)) {
            return Optional.empty();
        }
        OptionalInt status = api.httpStatus();
        if (status.isEmpty()) return Optional.empty();
        int code = status.getAsInt();
        if (code < 400 || code >= 500 || RETRYABLE_CLIENT_STATUSES.contains(code)) {
            return Optional.empty();
        }
        if (code == 404) return Optional.of("MODEL_AIRFLOW_TRIGGER_DAG_NOT_FOUND");
        if (code == 401 || code == 403) return Optional.of("MODEL_AIRFLOW_TRIGGER_FORBIDDEN");
        return Optional.of("MODEL_AIRFLOW_TRIGGER_REJECTED");
    }

    /**
     * A managed DAG file can be durable before Airflow has parsed and registered it. Keep the
     * release-build submission within the configured readiness window so that short parser lag
     * does not become a terminal candidate failure.
     */
    private Map<String, Object> waitForDagRegistration(String dagId) {
        int waitSeconds = Math.max(0, properties.getDagReadyWaitSeconds());
        if (waitSeconds <= 0) {
            return airflow.getDag(dagId).orElse(null);
        }
        Instant deadline = Instant.now().plus(Duration.ofSeconds(waitSeconds));
        long pollMillis = Duration.ofSeconds(
            Math.max(1, properties.getDagReadyPollSeconds())
        ).toMillis();
        while (true) {
            Map<String, Object> dag = airflow.getDag(dagId).orElse(null);
            if (dag != null) {
                return dag;
            }
            long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
            if (remainingMillis <= 0) {
                return null;
            }
            try {
                Thread.sleep(Math.min(remainingMillis, pollMillis));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "Interrupted while waiting for Airflow DAG registration",
                    interrupted
                );
            }
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
        if ("success".equals(state)) {
            return SubmissionResult.terminalSucceeded(actualRunId);
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
