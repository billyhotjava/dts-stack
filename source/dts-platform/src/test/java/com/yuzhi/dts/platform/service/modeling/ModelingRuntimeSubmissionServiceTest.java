package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelingRuntimeSubmissionServiceTest {

    @Test
    void compiledModelSubmitsAddaxThenAirflowWithTheSameRunContext() {
        RecordingGateway addax = new RecordingGateway("addax-execution-7");
        RecordingGateway airflow = new RecordingGateway("airflow-run-7");
        ModelingRuntimeSubmissionService service = new ModelingRuntimeSubmissionService(addax, airflow, true);

        ModelingRuntimeSubmissionService.SubmissionResult result = service.submit(
            ModelingAirflowSubmissionGate.CompileStatus.COMPILED,
            request(),
            Set.of("batch-7")
        );

        assertThat(result.state()).isEqualTo("SUBMITTED");
        assertThat(result.addaxTaskId()).isEqualTo("addax-execution-7");
        assertThat(result.airflowRunId()).isEqualTo("airflow-run-7");
        assertThat(addax.lastId).isEqualTo("addax-task-7");
        assertThat(airflow.lastId).isEqualTo("dts_modeling_pjm");
        assertThat(airflow.lastPayload).containsEntry("dbtSelector", "model.pjm.project_progress_monthly");
        assertThat(airflow.lastPayload).containsEntry("targetTable", "dws_project_progress_monthly");
        assertThat(airflow.lastPayload).containsEntry("addaxExecutionId", "addax-execution-7");
    }

    @Test
    void compileFailureBlocksBothExternalRuntimes() {
        RecordingGateway addax = new RecordingGateway("unused");
        RecordingGateway airflow = new RecordingGateway("unused");
        ModelingRuntimeSubmissionService service = new ModelingRuntimeSubmissionService(addax, airflow, true);

        ModelingRuntimeSubmissionService.SubmissionResult result = service.submit(
            ModelingAirflowSubmissionGate.CompileStatus.FAILED,
            request(),
            Set.of("batch-7")
        );

        assertThat(result.state()).isEqualTo("BLOCKED");
        assertThat(result.message()).contains("COMPILE_REQUIRED");
        assertThat(addax.lastId).isNull();
        assertThat(airflow.lastId).isNull();
    }

    @Test
    void disabledRuntimeKeepsRunQueuedWithoutCallingExternalSystems() {
        RecordingGateway addax = new RecordingGateway("unused");
        RecordingGateway airflow = new RecordingGateway("unused");
        ModelingRuntimeSubmissionService service = new ModelingRuntimeSubmissionService(addax, airflow, false);

        ModelingRuntimeSubmissionService.SubmissionResult result = service.submit(
            ModelingAirflowSubmissionGate.CompileStatus.COMPILED,
            request(),
            Set.of("batch-7")
        );

        assertThat(result.state()).isEqualTo("QUEUED");
        assertThat(result.message()).isEqualTo("RUNTIME_DISABLED");
        assertThat(addax.lastId).isNull();
        assertThat(airflow.lastId).isNull();
    }

    @Test
    void disabledRuntimeStillBlocksAnUncompiledModel() {
        RecordingGateway addax = new RecordingGateway("unused");
        RecordingGateway airflow = new RecordingGateway("unused");
        ModelingRuntimeSubmissionService service = new ModelingRuntimeSubmissionService(addax, airflow, false);

        ModelingRuntimeSubmissionService.SubmissionResult result = service.submit(
            ModelingAirflowSubmissionGate.CompileStatus.FAILED,
            request(),
            Set.of("batch-7")
        );

        assertThat(result.state()).isEqualTo("BLOCKED");
        assertThat(result.message()).contains("COMPILE_REQUIRED");
        assertThat(addax.lastId).isNull();
        assertThat(airflow.lastId).isNull();
    }

    @Test
    void disabledRuntimeStillValidatesTheAddaxBatchContext() {
        RecordingGateway addax = new RecordingGateway("unused");
        RecordingGateway airflow = new RecordingGateway("unused");
        ModelingRuntimeSubmissionService service = new ModelingRuntimeSubmissionService(addax, airflow, false);

        ModelingRuntimeSubmissionService.SubmissionResult result = service.submit(
            ModelingAirflowSubmissionGate.CompileStatus.COMPILED,
            request(),
            Set.of("another-batch")
        );

        assertThat(result.state()).isEqualTo("BLOCKED");
        assertThat(result.message()).contains("SOURCE_BATCH_NOT_FOUND");
        assertThat(addax.lastId).isNull();
        assertThat(airflow.lastId).isNull();
    }

    private static ModelingRunRequestContract.RunRequest request() {
        return new ModelingRunRequestContract.RunRequest(
            "pjm-project-node-dws",
            3,
            "run-7",
            new ModelingRunRequestContract.ExternalContext(
                "batch-7",
                "addax-task-7",
                "dts_modeling_pjm",
                null,
                null,
                "model.pjm.project_progress_monthly",
                "dws_project_progress_monthly"
            )
        );
    }

    private static final class RecordingGateway implements ModelingRuntimeSubmissionService.RuntimeGateway {

        private final String responseId;
        private String lastId;
        private Map<String, Object> lastPayload;

        private RecordingGateway(String responseId) {
            this.responseId = responseId;
        }

        @Override
        public ModelingRuntimeSubmissionService.GatewayResult submit(String id, Map<String, Object> payload) {
            lastId = id;
            lastPayload = new LinkedHashMap<>(payload);
            return ModelingRuntimeSubmissionService.GatewayResult.success(responseId);
        }
    }
}
