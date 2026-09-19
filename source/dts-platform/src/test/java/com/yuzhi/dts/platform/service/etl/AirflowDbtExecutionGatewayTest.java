package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.AirflowProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AirflowDbtExecutionGatewayTest {

    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String DAG_ID =
        "dts_release_build_postgres_primary";
    private static final String DAG_RUN_ID =
        "dts_rc_20000000000000000000000000000002_v3_a1";

    @Test
    void recoversExistingDeterministicRunWithoutTriggeringAgain() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(
            Optional.of(
                Map.of(
                    "dag_run_id",
                    DAG_RUN_ID,
                    "state",
                    "queued",
                    "conf",
                    expectedConf()
                )
            )
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(DbtExecutionGateway.SubmissionStatus.SUBMITTED);
        assertThat(result.recovered()).isTrue();
        assertThat(result.dagRunId()).isEqualTo(DAG_RUN_ID);
        verify(airflow, never()).triggerDag(any(), any());
    }

    @Test
    void reportsExistingFailedRunAsTerminalFailure() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(
            Optional.of(
                Map.of(
                    "dag_run_id",
                    DAG_RUN_ID,
                    "state",
                    "failed",
                    "conf",
                    expectedConf()
                )
            )
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result = gateway
            .reconcileReleaseBuild(request())
            .orElseThrow();

        assertThat(result.status()).isEqualTo(
            DbtExecutionGateway.SubmissionStatus.TERMINAL_FAILED
        );
        assertThat(result.dagRunId()).isEqualTo(DAG_RUN_ID);
        assertThat(result.errorCode()).isEqualTo(
            "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED"
        );
    }

    @Test
    void timeoutAfterAcceptanceRecoversByExactRunId() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID))
            .thenReturn(Optional.empty())
            .thenReturn(
                Optional.of(
                    Map.of(
                        "dag_run_id",
                        DAG_RUN_ID,
                        "state",
                        "queued",
                        "conf",
                        expectedConf()
                    )
                )
            );
        when(airflow.triggerDag(eq(DAG_ID), any()))
            .thenThrow(new RuntimeException("read timed out"));
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(DbtExecutionGateway.SubmissionStatus.SUBMITTED);
        assertThat(result.recovered()).isTrue();
        verify(airflow).triggerDag(eq(DAG_ID), any());
    }

    @Test
    void deterministicRunIdWithDifferentConfFailsClosed() {
        AirflowClient airflow = mock(AirflowClient.class);
        Map<String, Object> mismatchedConf = new LinkedHashMap<>(
            expectedConf()
        );
        mismatchedConf.put(
            "candidateId",
            "90000000-0000-0000-0000-000000000009"
        );
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(
            Optional.of(
                Map.of(
                    "dag_run_id",
                    DAG_RUN_ID,
                    "state",
                    "queued",
                    "conf",
                    mismatchedConf
                )
            )
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(DbtExecutionGateway.SubmissionStatus.BLOCKED);
        assertThat(result.errorCode()).isEqualTo(
            "MODEL_AIRFLOW_RUN_IDENTITY_CONFLICT"
        );
        verify(airflow, never()).triggerDag(any(), any());
    }

    @Test
    void deterministicTriggerRejectionWithoutRunIsBlockedInsteadOfRetriedForever() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(Optional.empty());
        when(airflow.triggerDag(eq(DAG_ID), any())).thenThrow(
            new AirflowClient.AirflowApiException("DAG not found", 404, null)
        );

        DbtExecutionGateway.SubmissionResult result = gateway(airflow)
            .submitReleaseBuild(request());

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.BLOCKED);
        assertThat(result.errorCode()).isEqualTo("MODEL_AIRFLOW_TRIGGER_DAG_NOT_FOUND");
    }

    @Test
    void forbiddenAndMalformedTriggerRejectionsAreBlockedWithStableCodes() {
        for (var expectation : Map.of(
            403, "MODEL_AIRFLOW_TRIGGER_FORBIDDEN",
            401, "MODEL_AIRFLOW_TRIGGER_FORBIDDEN",
            400, "MODEL_AIRFLOW_TRIGGER_REJECTED"
        ).entrySet()) {
            AirflowClient airflow = mock(AirflowClient.class);
            when(airflow.getDag(DAG_ID)).thenReturn(
                Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
            );
            when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(Optional.empty());
            when(airflow.triggerDag(eq(DAG_ID), any())).thenThrow(
                new AirflowClient.AirflowApiException("rejected", expectation.getKey(), null)
            );

            DbtExecutionGateway.SubmissionResult result = gateway(airflow)
                .submitReleaseBuild(request());

            assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.BLOCKED);
            assertThat(result.errorCode()).isEqualTo(expectation.getValue());
        }
    }

    @Test
    void transientTriggerStatusesRemainRetryableUnknown() {
        for (int status : new int[] { 408, 409, 429, 500, 503 }) {
            AirflowClient airflow = mock(AirflowClient.class);
            when(airflow.getDag(DAG_ID)).thenReturn(
                Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
            );
            when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(Optional.empty());
            when(airflow.triggerDag(eq(DAG_ID), any())).thenThrow(
                new AirflowClient.AirflowApiException("transient", status, null)
            );

            DbtExecutionGateway.SubmissionResult result = gateway(airflow)
                .submitReleaseBuild(request());

            assertThat(result.status())
                .as("HTTP %s", status)
                .isEqualTo(DbtExecutionGateway.SubmissionStatus.RETRYABLE_UNKNOWN);
            assertThat(result.errorCode()).isEqualTo("MODEL_AIRFLOW_TRIGGER_UNKNOWN");
        }
    }

    @Test
    void triggerRejectionStillRecoversRunThatAirflowAlreadyAccepted() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(Map.of(
                "dag_run_id", DAG_RUN_ID, "state", "queued", "conf", expectedConf()
            )));
        when(airflow.triggerDag(eq(DAG_ID), any())).thenThrow(
            new AirflowClient.AirflowApiException("conflict", 400, null)
        );

        DbtExecutionGateway.SubmissionResult result = gateway(airflow)
            .submitReleaseBuild(request());

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.SUBMITTED);
        assertThat(result.recovered()).isTrue();
    }

    @Test
    void reconcileReportsSucceededRunAsTerminalSuccess() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(
            Optional.of(Map.of(
                "dag_run_id", DAG_RUN_ID, "state", "success", "conf", expectedConf()
            ))
        );

        DbtExecutionGateway.SubmissionResult result = gateway(airflow)
            .reconcileReleaseBuild(request())
            .orElseThrow();

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.TERMINAL_SUCCEEDED);
        assertThat(result.dagRunId()).isEqualTo(DAG_RUN_ID);
    }

    @Test
    void unavailableReconciliationNeverBlindTriggers() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenThrow(
            new RuntimeException("Airflow read unavailable")
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(
                DbtExecutionGateway.SubmissionStatus.RETRYABLE_UNKNOWN
            );
        assertThat(result.errorCode()).isEqualTo(
            "MODEL_AIRFLOW_RECONCILIATION_UNAVAILABLE"
        );
        verify(airflow, never()).triggerDag(any(), any());
    }

    @Test
    void submitsOnlyTheFrozenBusinessReferences() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(
            Optional.empty()
        );
        when(airflow.triggerDag(eq(DAG_ID), any())).thenReturn(
            Optional.of(Map.of("dag_run_id", DAG_RUN_ID))
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(DbtExecutionGateway.SubmissionStatus.SUBMITTED);
        assertThat(result.recovered()).isFalse();
        ArgumentCaptor<Map<String, Object>> payload =
            ArgumentCaptor.forClass(Map.class);
        verify(airflow).triggerDag(eq(DAG_ID), payload.capture());
        assertThat(payload.getValue()).containsOnlyKeys(
            "dag_run_id",
            "conf"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> conf =
            (Map<String, Object>) payload.getValue().get("conf");
        assertThat(conf)
            .containsOnlyKeys(
                "pipelineRunGroupId",
                "candidateId",
                "candidateVersion",
                "attempt",
                "runPurpose",
                "runtimeSpecToken",
                "bundleChecksum"
            )
            .doesNotContainKeys(
                "projectDir",
                "selector",
                "target",
                "profile",
                "image",
                "command",
                "callable"
            );
    }

    @Test
    void pausedExecutorIsBlockedInsteadOfSilentlyUnpaused() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", true))
        );
        AirflowDbtExecutionGateway gateway = gateway(airflow);

        DbtExecutionGateway.SubmissionResult result =
            gateway.submitReleaseBuild(request());

        assertThat(result.status())
            .isEqualTo(DbtExecutionGateway.SubmissionStatus.BLOCKED);
        assertThat(result.errorCode()).isEqualTo(
            "MODEL_AIRFLOW_DAG_PAUSED"
        );
        verify(airflow, never()).triggerDag(any(), any());
    }

    @Test
    void waitsForAirflowDagRegistrationBeforeTriggering() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(
            Optional.empty(),
            Optional.of(Map.of("dag_id", DAG_ID, "is_paused", false))
        );
        when(airflow.getDagRun(DAG_ID, DAG_RUN_ID)).thenReturn(Optional.empty());
        when(airflow.triggerDag(eq(DAG_ID), any())).thenReturn(
            Optional.of(Map.of("dag_run_id", DAG_RUN_ID))
        );
        AirflowProperties properties = new AirflowProperties();
        properties.setEnabled(true);
        properties.setDagReadyWaitSeconds(1);
        properties.setDagReadyPollSeconds(0);

        DbtExecutionGateway.SubmissionResult result = new AirflowDbtExecutionGateway(airflow, properties)
            .submitReleaseBuild(request());

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.SUBMITTED);
        verify(airflow).triggerDag(eq(DAG_ID), any());
    }

    @Test
    void registrationWaitDeadlineRemainsBlocked() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenReturn(Optional.empty());
        AirflowProperties properties = new AirflowProperties();
        properties.setEnabled(true);
        properties.setDagReadyWaitSeconds(1);
        properties.setDagReadyPollSeconds(10);

        DbtExecutionGateway.SubmissionResult result = new AirflowDbtExecutionGateway(airflow, properties)
            .submitReleaseBuild(request());

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.BLOCKED);
        assertThat(result.errorCode()).isEqualTo("MODEL_AIRFLOW_DAG_NOT_REGISTERED");
        verify(airflow, never()).triggerDag(any(), any());
    }

    @Test
    void registrationLookupFailureRemainsRetryableUnknown() {
        AirflowClient airflow = mock(AirflowClient.class);
        when(airflow.getDag(DAG_ID)).thenThrow(new RuntimeException("Airflow unavailable"));

        DbtExecutionGateway.SubmissionResult result = gateway(airflow)
            .submitReleaseBuild(request());

        assertThat(result.status()).isEqualTo(DbtExecutionGateway.SubmissionStatus.RETRYABLE_UNKNOWN);
        assertThat(result.errorCode()).isEqualTo("MODEL_AIRFLOW_UNAVAILABLE");
        verify(airflow, never()).triggerDag(any(), any());
    }

    private static AirflowDbtExecutionGateway gateway(
        AirflowClient airflow
    ) {
        AirflowProperties properties = new AirflowProperties();
        properties.setEnabled(true);
        return new AirflowDbtExecutionGateway(airflow, properties);
    }

    private static DbtExecutionGateway.ReleaseBuildRequest request() {
        return new DbtExecutionGateway.ReleaseBuildRequest(
            GROUP_ID,
            CANDIDATE_ID,
            3,
            1,
            DAG_ID,
            DAG_RUN_ID,
            "runtime-token",
            "a".repeat(64)
        );
    }

    private static Map<String, Object> expectedConf() {
        return Map.of(
            "pipelineRunGroupId",
            GROUP_ID.toString(),
            "candidateId",
            CANDIDATE_ID.toString(),
            "candidateVersion",
            3,
            "attempt",
            1,
            "runPurpose",
            "RELEASE_BUILD",
            "runtimeSpecToken",
            "runtime-token",
            "bundleChecksum",
            "a".repeat(64)
        );
    }
}
