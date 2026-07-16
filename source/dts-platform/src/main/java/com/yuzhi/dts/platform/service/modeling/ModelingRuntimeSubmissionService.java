package com.yuzhi.dts.platform.service.modeling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Coordinates the external runtime hand-off after a model has compiled.
 *
 * <p>Addax and Airflow are deliberately represented by small ports so the ordering,
 * payload and failure semantics stay testable without starting either service. The
 * production adapters are responsible only for translating their native responses.</p>
 */
@Service
public class ModelingRuntimeSubmissionService {

    public interface RuntimeGateway {

        GatewayResult submit(String id, Map<String, Object> payload);
    }

    public record GatewayResult(boolean accepted, String executionId, String message) {

        public static GatewayResult success(String executionId) {
            return new GatewayResult(true, executionId, "OK");
        }

        public static GatewayResult rejected(String message) {
            return new GatewayResult(false, null, message);
        }
    }

    public record SubmissionResult(String state, String addaxTaskId, String airflowRunId, String message) {}

    private final RuntimeGateway addaxGateway;
    private final RuntimeGateway airflowGateway;
    private final boolean enabled;

    public ModelingRuntimeSubmissionService(RuntimeGateway addaxGateway, RuntimeGateway airflowGateway, boolean enabled) {
        this.addaxGateway = addaxGateway;
        this.airflowGateway = airflowGateway;
        this.enabled = enabled;
    }

    @Autowired
    public ModelingRuntimeSubmissionService(
        ModelingAddaxGateway addaxGateway,
        ModelingAirflowGateway airflowGateway,
        ModelingRuntimeProperties properties
    ) {
        this(addaxGateway, airflowGateway, properties.isEnabled());
    }

    public SubmissionResult submit(
        ModelingAirflowSubmissionGate.CompileStatus compileStatus,
        ModelingRunRequestContract.RunRequest request,
        Set<String> knownSourceBatches
    ) {
        ModelingAirflowSubmissionGate.Decision decision = ModelingAirflowSubmissionGate.evaluate(compileStatus, request, knownSourceBatches);
        if (!decision.submittable()) return new SubmissionResult("BLOCKED", null, null, String.join(",", decision.blockers()));
        // The disabled flag only suppresses external side effects. It must not
        // bypass compile, request, or source-batch validation.
        if (!enabled) return new SubmissionResult("QUEUED", existingAddaxTaskId(request), existingAirflowRunId(request), "RUNTIME_DISABLED");

        ModelingRunRequestContract.ExternalContext context = request.externalContext();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelSpecId", request.modelSpecId());
        payload.put("revision", request.revision());
        payload.put("idempotencyKey", request.idempotencyKey());
        putIfPresent(payload, "sourceBatchId", context.sourceBatchId());
        putIfPresent(payload, "addaxTaskId", context.addaxTaskId());
        putIfPresent(payload, "airflowRunId", context.airflowRunId());
        putIfPresent(payload, "dbtRunId", context.dbtRunId());
        payload.put("dbtSelector", decision.dbtSelector());
        payload.put("targetTable", decision.targetTable());

        String addaxExecutionId = context.addaxTaskId();
        if (hasText(context.addaxTaskId())) {
            GatewayResult addax = addaxGateway.submit(context.addaxTaskId(), payload);
            if (!addax.accepted()) return new SubmissionResult("FAILED", null, null, "ADDAX_SUBMIT_FAILED:" + addax.message());
            addaxExecutionId = defaultIfBlank(addax.executionId(), context.addaxTaskId());
            payload.put("addaxExecutionId", addaxExecutionId);
        }

        String airflowRunId = context.airflowRunId();
        if (hasText(context.airflowDagId())) {
            GatewayResult airflow = airflowGateway.submit(context.airflowDagId(), payload);
            if (!airflow.accepted()) return new SubmissionResult("FAILED", addaxExecutionId, null, "AIRFLOW_SUBMIT_FAILED:" + airflow.message());
            airflowRunId = defaultIfBlank(airflow.executionId(), context.airflowRunId());
        }
        return new SubmissionResult("SUBMITTED", addaxExecutionId, airflowRunId, "OK");
    }

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (hasText(value)) payload.put(key, value);
    }

    private static String existingAddaxTaskId(ModelingRunRequestContract.RunRequest request) {
        return request == null || request.externalContext() == null ? null : request.externalContext().addaxTaskId();
    }

    private static String existingAirflowRunId(ModelingRunRequestContract.RunRequest request) {
        return request == null || request.externalContext() == null ? null : request.externalContext().airflowRunId();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
