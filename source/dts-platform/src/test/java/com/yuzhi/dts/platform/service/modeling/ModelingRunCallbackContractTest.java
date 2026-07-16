package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelingRunCallbackContractTest {

    @Test
    void normalizesExternalCallbackStateAndKeepsRunIdentifiers() {
        ModelingRunCallbackContract.Callback callback = ModelingRunCallbackContract.validate(
            new ModelingRunCallbackContract.Callback(" succeeded ", "addax-1", "airflow-1", "dbt-1", "done")
        );

        assertThat(callback.state()).isEqualTo(ModelingRunStateMachine.RunState.SUCCEEDED);
        assertThat(callback.addaxTaskId()).isEqualTo("addax-1");
        assertThat(callback.airflowRunId()).isEqualTo("airflow-1");
        assertThat(callback.dbtRunId()).isEqualTo("dbt-1");
    }

    @Test
    void rejectsUnknownOrMissingCallbackState() {
        assertThatThrownBy(() -> ModelingRunCallbackContract.validate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MODEL_CALLBACK_STATE_REQUIRED");
        assertThatThrownBy(() -> ModelingRunCallbackContract.validate(
            new ModelingRunCallbackContract.Callback("paused", null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MODEL_CALLBACK_STATE_INVALID");
    }
}
