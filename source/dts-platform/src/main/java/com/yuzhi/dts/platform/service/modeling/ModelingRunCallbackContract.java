package com.yuzhi.dts.platform.service.modeling;

/** Validates the small, idempotent callback envelope shared by Addax, Airflow and dbt. */
public final class ModelingRunCallbackContract {

    private ModelingRunCallbackContract() {}

    public record Callback(
        ModelingRunStateMachine.RunState state,
        String addaxTaskId,
        String airflowRunId,
        String dbtRunId,
        String message
    ) {
        public Callback(String state, String addaxTaskId, String airflowRunId, String dbtRunId, String message) {
            this(parseState(state), addaxTaskId, airflowRunId, dbtRunId, message);
        }
    }

    public static Callback validate(Callback callback) {
        if (callback == null || callback.state() == null) throw new IllegalArgumentException("MODEL_CALLBACK_STATE_REQUIRED");
        return callback;
    }

    private static ModelingRunStateMachine.RunState parseState(String state) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException("MODEL_CALLBACK_STATE_REQUIRED");
        try {
            return ModelingRunStateMachine.RunState.valueOf(state.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("MODEL_CALLBACK_STATE_INVALID: " + state, exception);
        }
    }
}
