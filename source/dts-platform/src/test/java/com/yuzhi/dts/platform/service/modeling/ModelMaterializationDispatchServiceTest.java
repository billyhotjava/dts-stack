package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.BuildArtifact;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.DispatchRecord;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelMaterializationDispatchServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-27T14:00:00Z");
    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String DAG_ID =
        "dts_release_build_postgres_primary";
    private static final String DAG_RUN_ID =
        "dts_rc_20000000000000000000000000000002_v3_a1";
    private static final String ARTIFACT_CHECKSUM = "a".repeat(64);
    private static final String SCOPED_CHECKSUM = "b".repeat(64);

    @Test
    void oneMultiEntryCandidateProducesOneAirflowSubmission() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        CandidateBuildScope scope = scope();
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope);
        when(fixture.scoped.prepareCandidate(any())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/must-not-leave-platform",
                "dim_customer fct_invoice",
                SCOPED_CHECKSUM,
                List.of()
            )
        );
        when(fixture.tokens.issue(GROUP_ID, NOW)).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plus(Duration.ofMinutes(15))
            )
        );
        when(fixture.gateway.submitReleaseBuild(any())).thenReturn(
            DbtExecutionGateway.SubmissionResult.submitted(
                DAG_RUN_ID,
                false
            )
        );

        Optional<ModelMaterializationDispatchService.DispatchResult> result =
            fixture.service.dispatchNext();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo("SUBMITTED");
        ArgumentCaptor<List<DbtScopedProjectService.CandidateArtifactEntry>>
            entries = ArgumentCaptor.forClass(List.class);
        verify(fixture.scoped).prepareCandidate(entries.capture());
        assertThat(entries.getValue()).hasSize(2);
        ArgumentCaptor<DbtExecutionGateway.ReleaseBuildRequest> submission =
            ArgumentCaptor.forClass(
                DbtExecutionGateway.ReleaseBuildRequest.class
            );
        verify(fixture.gateway).submitReleaseBuild(submission.capture());
        assertThat(submission.getValue().runtimeSpecToken())
            .isEqualTo("runtime-token");
        assertThat(submission.getValue().bundleChecksum())
            .isEqualTo(SCOPED_CHECKSUM);
        verify(fixture.dispatches).markPrepared(
            GROUP_ID,
            SCOPED_CHECKSUM,
            "sha256:" + "c".repeat(64),
            NOW.plus(Duration.ofMinutes(15)),
            NOW
        );
        verify(fixture.dispatches).markSubmitted(
            GROUP_ID,
            false,
            NOW
        );
    }

    @Test
    void scopedArtifactChecksumDriftBlocksBeforeAirflow() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        CandidateBuildScope drifted = new CandidateBuildScope(
            "tenant-a",
            CANDIDATE_ID,
            3,
            GROUP_ID,
            "postgres-primary",
            "f".repeat(64),
            scope().entries()
        );
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(drifted);

        Optional<ModelMaterializationDispatchService.DispatchResult> result =
            fixture.service.dispatchNext();

        assertThat(result.orElseThrow().status()).isEqualTo("BLOCKED");
        assertThat(result.orElseThrow().errorCode()).isEqualTo(
            "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT"
        );
        verify(fixture.scoped, never()).prepareCandidate(any());
        verify(fixture.gateway, never()).submitReleaseBuild(any());
        verify(fixture.dispatches).markBlocked(
            GROUP_ID,
            "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT",
            NOW
        );
    }

    @Test
    void unknownExternalOutcomeRemainsRecoverableWithSameRunId() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(fixture.scoped.prepareCandidate(any())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/must-not-leave-platform",
                "dim_customer fct_invoice",
                SCOPED_CHECKSUM,
                List.of()
            )
        );
        when(fixture.tokens.issue(GROUP_ID, NOW)).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plus(Duration.ofMinutes(15))
            )
        );
        when(fixture.gateway.submitReleaseBuild(any())).thenReturn(
            DbtExecutionGateway.SubmissionResult.retryableUnknown(
                DAG_RUN_ID,
                "MODEL_AIRFLOW_TRIGGER_UNKNOWN"
            )
        );

        var result = fixture.service.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.dagRunId()).isEqualTo(DAG_RUN_ID);
        verify(fixture.dispatches).markUnknown(
            GROUP_ID,
            "MODEL_AIRFLOW_TRIGGER_UNKNOWN",
            NOW.plus(Duration.ofSeconds(30)),
            NOW
        );
    }

    @Test
    void acceptedAirflowRunIsNeverDowngradedToBlockedWhenLocalCommitFails() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(fixture.scoped.prepareCandidate(any())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/must-not-leave-platform",
                "dim_customer fct_invoice",
                SCOPED_CHECKSUM,
                List.of()
            )
        );
        when(fixture.tokens.issue(GROUP_ID, NOW)).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plus(Duration.ofMinutes(15))
            )
        );
        when(fixture.gateway.submitReleaseBuild(any())).thenReturn(
            DbtExecutionGateway.SubmissionResult.submitted(
                DAG_RUN_ID,
                false
            )
        );
        doThrow(new IllegalStateException("database unavailable"))
            .when(fixture.dispatches)
            .markSubmitted(GROUP_ID, false, NOW);

        var result = fixture.service.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.errorCode()).isEqualTo(
            "MODEL_DISPATCH_PERSISTENCE_UNKNOWN"
        );
        verify(fixture.dispatches, never()).markBlocked(
            eq(GROUP_ID),
            any(),
            eq(NOW)
        );
        verify(fixture.dispatches).markUnknown(
            GROUP_ID,
            "MODEL_DISPATCH_PERSISTENCE_UNKNOWN",
            NOW.plus(Duration.ofSeconds(30)),
            NOW
        );
    }

    @Test
    void expiredRuntimeTokenStillRecoversPreviouslyAcceptedAirflowRun() {
        Fixture fixture = fixture();
        DispatchRecord prepared = new DispatchRecord(
            GROUP_ID,
            "tenant-a",
            CANDIDATE_ID,
            3,
            1,
            "postgres-primary",
            DAG_ID,
            DAG_RUN_ID,
            ARTIFACT_CHECKSUM,
            "CLAIMED",
            SCOPED_CHECKSUM,
            "sha256:" + "c".repeat(64),
            NOW.minusSeconds(1)
        );
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(prepared));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(
            fixture.tokens.restore(
                GROUP_ID,
                NOW.minusSeconds(1)
            )
        ).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.minusSeconds(1)
            )
        );
        when(fixture.gateway.reconcileReleaseBuild(any())).thenReturn(
            Optional.of(
                DbtExecutionGateway.SubmissionResult.submitted(
                    DAG_RUN_ID,
                    true
                )
            )
        );

        var result = fixture.service.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.recovered()).isTrue();
        verify(fixture.dispatches).markSubmitted(
            GROUP_ID,
            true,
            NOW
        );
        verify(fixture.scoped, never()).prepareCandidate(any());
        verify(fixture.gateway, never()).submitReleaseBuild(any());
        verify(fixture.dispatches, never()).markBlocked(
            eq(GROUP_ID),
            any(),
            eq(NOW)
        );
    }

    private static Fixture fixture() {
        var dispatches = mock(
            ModelMaterializationDispatchRepository.class
        );
        var builds = mock(ModelMaterializationBuildRepository.class);
        var scoped = mock(DbtScopedProjectService.class);
        var dags = mock(DbtDagService.class);
        var gateway = mock(DbtExecutionGateway.class);
        var tokens = mock(ModelRuntimeSpecTokenCodec.class);
        when(dags.ensureReleaseBuildDag(DAG_ID)).thenReturn(
            new DbtDagService.ManagedDagDeployment(
                DAG_ID,
                "sprint76-v1",
                "d".repeat(64),
                java.nio.file.Path.of("/tmp", DAG_ID + ".py")
            )
        );
        var service = new ModelMaterializationDispatchService(
            dispatches,
            builds,
            scoped,
            dags,
            gateway,
            tokens,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(
            service,
            dispatches,
            builds,
            scoped,
            gateway,
            tokens
        );
    }

    private static DispatchRecord dispatch() {
        return new DispatchRecord(
            GROUP_ID,
            "tenant-a",
            CANDIDATE_ID,
            3,
            1,
            "postgres-primary",
            DAG_ID,
            DAG_RUN_ID,
            ARTIFACT_CHECKSUM,
            "CLAIMED",
            null,
            null,
            null
        );
    }

    private static CandidateBuildScope scope() {
        return new CandidateBuildScope(
            "tenant-a",
            CANDIDATE_ID,
            3,
            GROUP_ID,
            "postgres-primary",
            ARTIFACT_CHECKSUM,
            List.of(
                entry(
                    "30000000-0000-0000-0000-000000000003",
                    "model.finance.dim_customer",
                    "models/dwd/dim_customer.sql"
                ),
                entry(
                    "40000000-0000-0000-0000-000000000004",
                    "model.finance.fct_invoice",
                    "models/dwd/fct_invoice.sql"
                )
            )
        );
    }

    private static CandidateBuildEntry entry(
        String modelId,
        String uniqueId,
        String path
    ) {
        return new CandidateBuildEntry(
            UUID.nameUUIDFromBytes(
                ("pipeline:" + modelId).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                )
            ),
            UUID.fromString(modelId),
            2,
            "1".repeat(64),
            4,
            "2".repeat(64),
            "DESIGNER_GENERATED",
            uniqueId,
            uniqueId.substring(
                uniqueId.lastIndexOf('.') + 1
            ),
            List.of(
                new BuildArtifact(
                    path,
                    "3".repeat(64),
                    "select 1 as id"
                )
            )
        );
    }

    private record Fixture(
        ModelMaterializationDispatchService service,
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationBuildRepository builds,
        DbtScopedProjectService scoped,
        DbtExecutionGateway gateway,
        ModelRuntimeSpecTokenCodec tokens
    ) {}
}
