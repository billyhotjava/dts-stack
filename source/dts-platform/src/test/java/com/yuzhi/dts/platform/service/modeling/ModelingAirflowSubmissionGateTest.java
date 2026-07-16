package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelingAirflowSubmissionGateTest {

    @Test
    void compileFailureDoesNotSubmitAirflowDag() {
        ModelingRunRequestContract.RunRequest request = validRequest();

        ModelingAirflowSubmissionGate.Decision decision = ModelingAirflowSubmissionGate.evaluate(
            ModelingAirflowSubmissionGate.CompileStatus.FAILED,
            request,
            Set.of("batch-1")
        );

        assertThat(decision.submittable()).isFalse();
        assertThat(decision.blockers()).containsExactly("COMPILE_REQUIRED");
    }

    @Test
    void compiledPjmModelCanBeSubmittedWithStableSelector() {
        ModelingAirflowSubmissionGate.Decision decision = ModelingAirflowSubmissionGate.evaluate(
            ModelingAirflowSubmissionGate.CompileStatus.COMPILED,
            validRequest(),
            Set.of("batch-1")
        );

        assertThat(decision.submittable()).isTrue();
        assertThat(decision.dbtSelector()).isEqualTo("model.pjm.project_progress_monthly");
        assertThat(decision.targetTable()).isEqualTo("dws_project_progress_monthly");
    }

    private static ModelingRunRequestContract.RunRequest validRequest() {
        return new ModelingRunRequestContract.RunRequest(
            "pjm-project-node-dws",
            1,
            "run-1",
            new ModelingRunRequestContract.ExternalContext("batch-1", "addax-task-1", "dts_modeling_pjm", "airflow-run-1", null, "model.pjm.project_progress_monthly", "dws_project_progress_monthly")
        );
    }
}
