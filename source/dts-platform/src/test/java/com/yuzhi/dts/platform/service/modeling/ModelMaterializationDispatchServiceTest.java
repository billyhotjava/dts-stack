package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.BuildArtifact;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.DispatchRecord;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtExecutionGateway;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.PinnedSourceDefinition;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test void directoryOutageRemainsRetryableAndDoesNotSubmitOrFailTheCandidate() {
        Fixture fixture = fixture();
        when(fixture.dispatches.claimNext(eq(NOW),eq(Duration.ofMinutes(2)))).thenReturn(Optional.of(dispatch()));
        var authorization = (ModelingExecutionAuthorization) org.springframework.test.util.ReflectionTestUtils.getField(fixture.service,"executionAuthorization");
        when(authorization.candidate(anyString(),any(),anyInt(),anyString())).thenThrow(new com.yuzhi.dts.platform.security.modeling.ModelingIdentityException(503,"MODELING_IDENTITY_UNAVAILABLE","目录不可用"));
        var result = fixture.service.dispatchNext().orElseThrow();
        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.errorCode()).isEqualTo("MODELING_IDENTITY_UNAVAILABLE");
        verify(fixture.gateway,never()).submitReleaseBuild(any());
        verify(fixture.candidates,never()).transition(any(),any(),any(),any());
    }
    @Test void revokedUserIsBlockedBeforeExternalSubmission() {
        Fixture fixture = fixture();
        when(fixture.dispatches.claimNext(eq(NOW),eq(Duration.ofMinutes(2)))).thenReturn(Optional.of(dispatch()));
        var authorization = (ModelingExecutionAuthorization) org.springframework.test.util.ReflectionTestUtils.getField(fixture.service,"executionAuthorization");
        when(authorization.candidate(anyString(),any(),anyInt(),anyString())).thenThrow(new com.yuzhi.dts.platform.security.modeling.ModelingIdentityException(403,"MODEL_EXECUTION_AUTHORIZATION_REVOKED","授权失效"));
        var result = fixture.service.dispatchNext().orElseThrow();
        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCode()).isEqualTo("MODEL_EXECUTION_AUTHORIZATION_REVOKED");
        verify(fixture.gateway,never()).submitReleaseBuild(any());
    }

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
        when(fixture.gateway.submitReleaseBuild(any())).thenAnswer(
            invocation -> {
                assertThat(fixture.transactions.active()).isFalse();
                return DbtExecutionGateway.SubmissionResult.submitted(
                    DAG_RUN_ID,
                    false
                );
            }
        );
        fixture.audit.observe(() -> {
            assertThat(fixture.transactions.active()).isTrue();
        });

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
        AuditCall audit = fixture.audit.singleCall();
        assertThat(audit.actor()).isEqualTo("scheduler");
        assertThat(audit.eventIdentity())
            .isEqualTo(dispatchEventIdentity("submitted"));
        assertThat(audit.occurredAt()).isEqualTo(NOW);
        assertThat(audit.actionCode())
            .isEqualTo("MODEL_MATERIALIZATION_DISPATCH_SUBMITTED");
        assertThat(audit.stage()).isEqualTo(AuditStage.SUCCESS);
        assertThat(audit.resourceId()).isEqualTo(GROUP_ID.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
            (Map<String, Object>) audit.payload();
        assertThat(payload)
            .containsOnlyKeys(
                "tenant",
                "candidate",
                "version",
                "attempt",
                "dispatch",
                "run",
                "status"
            )
            .containsEntry("status", "SUBMITTED");
        assertThat(payload)
            .doesNotContainKeys(
                "checksum",
                "digest",
                "token",
                "credential",
                "selector"
            );
        assertThat(payload.toString())
            .doesNotContain("runtime-token")
            .doesNotContain("sha256:")
            .doesNotContain("/must-not-leave-platform")
            .doesNotContain("dim_customer fct_invoice");
    }

    @Test
    void addsPinnedLogicalDependenciesToTheImmutableCandidateOverlay() {
        Fixture fixture = fixture();
        when(fixture.dispatches.claimNext(eq(NOW), eq(Duration.ofMinutes(2))))
            .thenReturn(Optional.of(dispatch()));
        CandidateBuildScope scope = scope();
        BuildArtifact pinnedDependency = new BuildArtifact(
            "models/.dts-pinned-dependencies/dim_status.sql",
            "9".repeat(64),
            "{{ config(materialized='ephemeral') }}\nselect * from \"biadmin\".\"public\".\"dim_status\"\n"
        );
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope);
        when(fixture.builds.loadPinnedDependencyArtifacts(scope))
            .thenReturn(List.of(pinnedDependency));
        when(fixture.scoped.prepareCandidate(any())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/must-not-leave-platform",
                "+dim_customer +fct_invoice",
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
            DbtExecutionGateway.SubmissionResult.submitted(DAG_RUN_ID, false)
        );

        fixture.service.dispatchNext().orElseThrow();

        ArgumentCaptor<List<DbtScopedProjectService.CandidateArtifactEntry>> entries =
            ArgumentCaptor.forClass(List.class);
        verify(fixture.scoped).prepareCandidate(entries.capture());
        assertThat(entries.getValue())
            .flatExtracting(DbtScopedProjectService.CandidateArtifactEntry::artifacts)
            .extracting(DbtScopedProjectService.CandidateArtifact::path)
            .contains(pinnedDependency.path());
    }

    @Test
    void addsVersionBoundPhysicalSourcesToTheImmutableCandidateOverlay() {
        Fixture fixture = fixture();
        when(fixture.dispatches.claimNext(eq(NOW), eq(Duration.ofMinutes(2))))
            .thenReturn(Optional.of(dispatch()));
        CandidateBuildScope scope = scope();
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope);
        when(fixture.sourceAvailability.pinnedDispatchSources(GROUP_ID))
            .thenReturn(List.of(new PinnedSourceDefinition(
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                "source-version-1",
                "public",
                "public",
                "prjdemo_ods_project_task_clean"
            )));
        when(fixture.scoped.prepareCandidate(any())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/must-not-leave-platform",
                "+dim_customer +fct_invoice",
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
            DbtExecutionGateway.SubmissionResult.submitted(DAG_RUN_ID, false)
        );

        fixture.service.dispatchNext().orElseThrow();

        ArgumentCaptor<List<DbtScopedProjectService.CandidateArtifactEntry>> entries =
            ArgumentCaptor.forClass(List.class);
        verify(fixture.scoped).prepareCandidate(entries.capture());
        DbtScopedProjectService.CandidateArtifact sourceYaml = entries.getValue()
            .stream()
            .flatMap(entry -> entry.artifacts().stream())
            .filter(artifact -> artifact.path().equals("models/_dts_pinned_sources.yml"))
            .findFirst()
            .orElseThrow();
        assertThat(sourceYaml.content())
            .contains("name: public")
            .contains("schema: public")
            .contains("name: prjdemo_ods_project_task_clean")
            .contains("40000000-0000-0000-0000-000000000004")
            .contains("source-version-1");
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
        verify(fixture.candidates).transition(
            "tenant-a",
            "service:dts-platform",
            CANDIDATE_ID,
            new TransitionCommand(
                3,
                DeliveryStatus.BUILD_FAILED,
                "materialization-dispatch-blocked-" + GROUP_ID,
                "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT"
            )
        );
        assertThat(fixture.audit.singleCall())
            .isEqualTo(
                new AuditCall(
                    "scheduler",
                    dispatchEventIdentity("blocked"),
                    NOW,
                    "MODEL_MATERIALIZATION_DISPATCH_BLOCKED",
                    AuditStage.FAIL,
                    GROUP_ID.toString(),
                    Map.of(
                        "tenant",
                        "tenant-a",
                        "candidate",
                        CANDIDATE_ID,
                        "version",
                        3,
                        "attempt",
                        1,
                        "dispatch",
                        GROUP_ID,
                        "run",
                        DAG_RUN_ID,
                        "status",
                        "BLOCKED",
                        "errorCode",
                        "MATERIALIZATION_ARTIFACT_BUNDLE_DRIFT"
                    )
                )
            );
    }

    @Test
    void availabilityFenceBlocksBeforeArtifactPreparationOrAirflow() {
        Fixture fixture = fixture();
        when(fixture.dispatches.claimNext(eq(NOW), eq(Duration.ofMinutes(2))))
            .thenReturn(Optional.of(dispatch()));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID)).thenReturn(scope());
        doThrow(
            new ModelReleaseCandidateException(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
                "source fenced",
                ModelReleaseCandidateException.Kind.UNPROCESSABLE
            )
        ).when(fixture.sourceAvailability).requireDispatchCurrent(GROUP_ID);

        var result = fixture.service.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCode()).isEqualTo(ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE);
        verify(fixture.scoped, never()).prepareCandidate(any());
        verify(fixture.gateway, never()).submitReleaseBuild(any());
        verify(fixture.dispatches).markBlocked(
            GROUP_ID,
            ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
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
        AuditCall audit = fixture.audit.singleCall();
        assertThat(audit.eventIdentity())
            .isEqualTo(dispatchEventIdentity("unknown"));
        assertThat(audit.actionCode())
            .isEqualTo("MODEL_MATERIALIZATION_DISPATCH_UNKNOWN");
        assertThat(audit.stage()).isEqualTo(AuditStage.FAIL);
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
        doThrow(
            new ModelReleaseCandidateException(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
                "source fenced",
                ModelReleaseCandidateException.Kind.UNPROCESSABLE
            )
        ).when(fixture.sourceAvailability).requireDispatchCurrent(GROUP_ID);

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
        verify(fixture.sourceAvailability, never()).requireDispatchCurrent(GROUP_ID);
        verify(fixture.dispatches, never()).markBlocked(
            eq(GROUP_ID),
            any(),
            eq(NOW)
        );
        AuditCall audit = fixture.audit.singleCall();
        assertThat(audit.eventIdentity())
            .isEqualTo(dispatchEventIdentity("submitted"));
        assertThat(audit.actionCode())
            .isEqualTo("MODEL_MATERIALIZATION_DISPATCH_SUBMITTED");
        assertThat(audit.stage()).isEqualTo(AuditStage.SUCCESS);
    }

    @Test
    void reconcilesSubmittedAirflowFailureIntoDurableFailedTruth() {
        Fixture fixture = fixture();
        DispatchRecord submitted = submittedDispatch();
        when(
            fixture.dispatches.findSubmittedBefore(
                NOW.minus(Duration.ofMinutes(2)),
                20
            )
        ).thenReturn(List.of(submitted));
        when(
            fixture.tokens.restore(
                GROUP_ID,
                NOW.plus(Duration.ofMinutes(15))
            )
        ).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plus(Duration.ofMinutes(15))
            )
        );
        when(fixture.gateway.reconcileReleaseBuild(any())).thenReturn(
            Optional.of(
                DbtExecutionGateway.SubmissionResult.terminalFailed(
                    DAG_RUN_ID,
                    "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED"
                )
            )
        );

        fixture.service.reconcileSubmitted();

        verify(fixture.runArtifacts).finalizeRun(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.FinalizeCommand(
                "FAILED"
            )
        );
    }

    @Test
    void leavesSubmittedRunUntouchedWhileAirflowIsStillRunning() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.findSubmittedBefore(
                NOW.minus(Duration.ofMinutes(2)),
                20
            )
        ).thenReturn(List.of(submittedDispatch()));
        when(
            fixture.tokens.restore(
                GROUP_ID,
                NOW.plus(Duration.ofMinutes(15))
            )
        ).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plus(Duration.ofMinutes(15))
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

        fixture.service.reconcileSubmitted();

        verify(fixture.runArtifacts, never()).finalizeRun(any(), any());
    }

    @Test
    void auditFailureRollsBackAndPropagatesBeforeDeterministicReconcile() {
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
            NOW.plus(Duration.ofMinutes(15))
        );
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()), Optional.of(prepared));
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
        when(
            fixture.tokens.restore(
                GROUP_ID,
                NOW.plus(Duration.ofMinutes(15))
            )
        ).thenReturn(
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
        when(fixture.gateway.reconcileReleaseBuild(any())).thenReturn(
            Optional.of(
                DbtExecutionGateway.SubmissionResult.submitted(
                    DAG_RUN_ID,
                    true
                )
            )
        );
        fixture.audit.failNext(
            new IllegalStateException("audit unavailable")
        );

        assertThatThrownBy(fixture.service::dispatchNext)
            .isInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("audit unavailable");
        assertThat(fixture.transactions.rollbacks()).isEqualTo(1);
        verify(fixture.dispatches, never()).markUnknown(
            eq(GROUP_ID),
            any(),
            any(),
            any()
        );

        var reconciled = fixture.service.dispatchNext().orElseThrow();

        assertThat(reconciled.status()).isEqualTo("SUBMITTED");
        assertThat(reconciled.recovered()).isTrue();
        assertThat(fixture.transactions.commits()).isEqualTo(1);
        verify(fixture.gateway).submitReleaseBuild(any());
        verify(fixture.gateway).reconcileReleaseBuild(any());
        assertThat(
            fixture.audit.calls().stream()
                .map(AuditCall::eventIdentity)
                .toList()
        )
            .containsExactly(
                dispatchEventIdentity("submitted"),
                dispatchEventIdentity("submitted")
            );
    }

    private static Fixture fixture() {
        var dispatches = mock(
            ModelMaterializationDispatchRepository.class
        );
        var builds = mock(ModelMaterializationBuildRepository.class);
        var candidates = mock(ModelReleaseCandidateService.class);
        var scoped = mock(DbtScopedProjectService.class);
        var dags = mock(DbtDagService.class);
        var gateway = mock(DbtExecutionGateway.class);
        var runArtifacts = mock(
            ModelMaterializationRunArtifactService.class
        );
        var tokens = mock(ModelRuntimeSpecTokenCodec.class);
        var sourceAvailability = mock(ModelMaterializationSourceAvailabilityGuard.class);
        var audit = new RecordingAuditService();
        var transactions = new RecordingTransactionManager();
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
            candidates,
            scoped,
            dags,
            gateway,
            runArtifacts,
            tokens,
            sourceAvailability,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new TransactionTemplate(transactions)
        );
        org.springframework.test.util.ReflectionTestUtils.setField(service, "executionAuthorization", mock(ModelingExecutionAuthorization.class));
        return new Fixture(
            service,
            dispatches,
            builds,
            candidates,
            scoped,
            gateway,
            runArtifacts,
            tokens,
            sourceAvailability,
            audit,
            transactions
        );
    }

    private static String dispatchEventIdentity(String status) {
        return (
            "model-materialization-dispatch:" +
            GROUP_ID +
            ":attempt:1:" +
            status
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

    private static DispatchRecord submittedDispatch() {
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
            "SUBMITTED",
            SCOPED_CHECKSUM,
            "sha256:" + "c".repeat(64),
            NOW.plus(Duration.ofMinutes(15))
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
        ModelReleaseCandidateService candidates,
        DbtScopedProjectService scoped,
        DbtExecutionGateway gateway,
        ModelMaterializationRunArtifactService runArtifacts,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        RecordingAuditService audit,
        RecordingTransactionManager transactions
    ) {}

    private record AuditCall(
        String actor,
        String eventIdentity,
        Instant occurredAt,
        String actionCode,
        AuditStage stage,
        String resourceId,
        Object payload
    ) {}

    private static final class RecordingAuditService
        extends AuditService {

        private final java.util.ArrayList<AuditCall> calls =
            new java.util.ArrayList<>();
        private Runnable observer = () -> {};
        private RuntimeException nextFailure;

        private RecordingAuditService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public UUID auditActionAsStrict(
            String machineActor,
            String eventIdentity,
            Instant occurredAt,
            String actionCode,
            AuditStage stage,
            String resourceId,
            Object payload
        ) {
            observer.run();
            calls.add(
                new AuditCall(
                    machineActor,
                    eventIdentity,
                    occurredAt,
                    actionCode,
                    stage,
                    resourceId,
                    payload
                )
            );
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            return UUID.nameUUIDFromBytes(
                eventIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        private void observe(Runnable observer) {
            this.observer = observer;
        }

        private void failNext(RuntimeException failure) {
            nextFailure = failure;
        }

        private AuditCall singleCall() {
            assertThat(calls).hasSize(1);
            return calls.getFirst();
        }

        private List<AuditCall> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class RecordingTransactionManager
        implements PlatformTransactionManager {

        private final ThreadLocal<Boolean> active =
            ThreadLocal.withInitial(() -> false);
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(
            TransactionDefinition definition
        ) throws TransactionException {
            active.set(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status)
            throws TransactionException {
            commits++;
            active.remove();
        }

        @Override
        public void rollback(TransactionStatus status)
            throws TransactionException {
            rollbacks++;
            active.remove();
        }

        private boolean active() {
            return active.get();
        }

        private int commits() {
            return commits;
        }

        private int rollbacks() {
            return rollbacks;
        }
    }
}
