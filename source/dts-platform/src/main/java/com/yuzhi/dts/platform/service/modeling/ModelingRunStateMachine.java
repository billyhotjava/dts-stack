package com.yuzhi.dts.platform.service.modeling;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** State transition rules shared by Addax, Airflow, dbt callbacks and the UI. */
public final class ModelingRunStateMachine {

    private static final Map<RunState, Set<RunState>> ALLOWED = Map.of(
        RunState.QUEUED, EnumSet.of(RunState.RUNNING, RunState.CANCELLED, RunState.TIMED_OUT),
        RunState.RUNNING, EnumSet.of(RunState.SUCCEEDED, RunState.FAILED, RunState.CANCELLED, RunState.TIMED_OUT),
        RunState.FAILED, EnumSet.of(RunState.QUEUED),
        RunState.CANCELLED, EnumSet.of(RunState.QUEUED),
        RunState.TIMED_OUT, EnumSet.of(RunState.QUEUED),
        RunState.SUCCEEDED, EnumSet.noneOf(RunState.class)
    );

    private ModelingRunStateMachine() {}

    public enum RunState {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        TIMED_OUT,
    }

    public static RunState transition(RunState current, RunState next) {
        if (current == null || next == null || !ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("RUN_STATE_INVALID: " + current + " -> " + next);
        }
        return next;
    }

    public static RunState applyCallback(RunState current, RunState callbackState) {
        if (current == RunState.SUCCEEDED || current == callbackState) return current;
        return transition(current, callbackState);
    }
}
