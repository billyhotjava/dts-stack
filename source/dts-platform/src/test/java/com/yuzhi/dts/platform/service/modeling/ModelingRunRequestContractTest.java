package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelingRunRequestContractTest {

    @Test
    void acceptsPjmRunWithAddaxAirflowDbtAndPostgresContext() {
        ModelingRunRequestContract.RunRequest request = new ModelingRunRequestContract.RunRequest(
            "pjm-project-node-dws",
            1,
            "run-pjm-001",
            new ModelingRunRequestContract.ExternalContext(
                "addax-batch-20260714",
                "addax-task-73",
                "dts_modeling_pjm",
                "airflow-run-20260714",
                "dbt-run-881",
                "model.pjm.project_progress_monthly",
                "dws_project_progress_monthly"
            )
        );

        assertThat(ModelingRunRequestContract.validate(request, Set.of("addax-batch-20260714"))).isEmpty();
    }

    @Test
    void missingAddaxBatchReturnsReadableBlocker() {
        ModelingRunRequestContract.RunRequest request = new ModelingRunRequestContract.RunRequest(
            "pjm-project-node-dws",
            1,
            "run-pjm-001",
            new ModelingRunRequestContract.ExternalContext("missing-batch", null, "dts_modeling_pjm", null, null, "model.pjm.project_progress_monthly", "dws_project_progress_monthly")
        );

        assertThat(ModelingRunRequestContract.validate(request, Set.of("addax-batch-20260714")))
            .extracting(ModelingRunRequestContract.Issue::code)
            .containsExactly(ModelingRunRequestContract.ErrorCode.SOURCE_BATCH_NOT_FOUND);
    }

    @Test
    void idempotencyAndTargetAreMandatoryForSubmission() {
        ModelingRunRequestContract.RunRequest request = new ModelingRunRequestContract.RunRequest(
            "",
            0,
            "",
            new ModelingRunRequestContract.ExternalContext(null, null, null, null, null, null, null)
        );

        assertThat(ModelingRunRequestContract.validate(request, Set.of()))
            .extracting(ModelingRunRequestContract.Issue::code)
            .containsExactlyInAnyOrder(
                ModelingRunRequestContract.ErrorCode.MODEL_SPEC_REQUIRED,
                ModelingRunRequestContract.ErrorCode.REVISION_INVALID,
                ModelingRunRequestContract.ErrorCode.IDEMPOTENCY_REQUIRED,
                ModelingRunRequestContract.ErrorCode.DBT_SELECTOR_REQUIRED,
                ModelingRunRequestContract.ErrorCode.TARGET_TABLE_REQUIRED
            );
    }
}
