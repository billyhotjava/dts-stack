package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelingRunStateMachineTest {

    @Test
    void acceptsOnlyForwardOrExplicitRetryTransitions() {
        assertThat(ModelingRunStateMachine.transition(ModelingRunStateMachine.RunState.QUEUED, ModelingRunStateMachine.RunState.RUNNING))
            .isEqualTo(ModelingRunStateMachine.RunState.RUNNING);
        assertThat(ModelingRunStateMachine.transition(ModelingRunStateMachine.RunState.FAILED, ModelingRunStateMachine.RunState.QUEUED))
            .isEqualTo(ModelingRunStateMachine.RunState.QUEUED);
        assertThatThrownBy(() -> ModelingRunStateMachine.transition(ModelingRunStateMachine.RunState.SUCCEEDED, ModelingRunStateMachine.RunState.RUNNING))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RUN_STATE_INVALID");
    }

    @Test
    void successfulCallbackIsIdempotentAndCannotBeOverwrittenByLateFailure() {
        assertThat(ModelingRunStateMachine.applyCallback(ModelingRunStateMachine.RunState.RUNNING, ModelingRunStateMachine.RunState.RUNNING))
            .isEqualTo(ModelingRunStateMachine.RunState.RUNNING);
        assertThat(ModelingRunStateMachine.applyCallback(ModelingRunStateMachine.RunState.SUCCEEDED, ModelingRunStateMachine.RunState.SUCCEEDED))
            .isEqualTo(ModelingRunStateMachine.RunState.SUCCEEDED);
        assertThat(ModelingRunStateMachine.applyCallback(ModelingRunStateMachine.RunState.SUCCEEDED, ModelingRunStateMachine.RunState.FAILED))
            .isEqualTo(ModelingRunStateMachine.RunState.SUCCEEDED);
    }
}
