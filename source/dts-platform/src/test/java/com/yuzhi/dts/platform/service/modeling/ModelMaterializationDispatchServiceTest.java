package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void uncertifiedRuntimeBlocksBeforeAnyAirflowBoundary() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(fixture.runtimeCertification.requireCertified())
            .thenThrow(
                new ModelReleaseCandidateException(
                    "DBT_RUNTIME_NOT_CERTIFIED",
                    "Certified dbt runtime is unavailable",
                    ModelReleaseCandidateException.Kind.PRECONDITION_REQUIRED
                )
            );

        var result = fixture.service.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.errorCode())
            .isEqualTo("DBT_RUNTIME_NOT_CERTIFIED");
        verify(fixture.gateway, never()).reconcileReleaseBuild(any());
        verify(fixture.gateway, never()).submitReleaseBuild(any());
        verify(fixture.scoped, never()).prepareCandidate(any());
        assertThat(fixture.audit.singleCall().payload().toString())
            .contains("DBT_RUNTIME_NOT_CERTIFIED")
            .doesNotContain("sha256:")
            .doesNotContain("registry");
    }

    @Test
    void uncertifiedRuntimeFailsClosedWhenStrictAuditForwarderIsUnavailable() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.claimNext(
                eq(NOW),
                eq(Duration.ofMinutes(2))
            )
        ).thenReturn(Optional.of(dispatch()));
        when(fixture.builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(fixture.runtimeCertification.requireCertified())
            .thenThrow(
                new ModelReleaseCandidateException(
                    "DBT_RUNTIME_NOT_CERTIFIED",
                    "Certified dbt runtime is unavailable",
                    ModelReleaseCandidateException.Kind.PRECONDITION_REQUIRED
                )
            );
        fixture.audit.failNext(
            new IllegalStateException("strict audit forwarder unavailable")
        );

        assertThatThrownBy(fixture.service::dispatchNext)
            .isInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("strict audit forwarder unavailable");
        verify(fixture.gateway, never()).reconcileReleaseBuild(any());
        verify(fixture.gateway, never()).submitReleaseBuild(any());
        verify(fixture.scoped, never()).prepareCandidate(any());
        assertThat(fixture.transactions.rollbacks()).isOne();
        assertThat(fixture.audit.singleCall().actionCode())
            .isEqualTo("MODEL_MATERIALIZATION_DISPATCH_BLOCKED");
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
        var scoped = mock(DbtScopedProjectService.class);
        var dags = mock(DbtDagService.class);
        var gateway = mock(DbtExecutionGateway.class);
        var tokens = mock(ModelRuntimeSpecTokenCodec.class);
        var runtimeCertification = mock(
            DbtRuntimeCertificationService.class
        );
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
        when(runtimeCertification.requireCertified()).thenReturn(
            DbtRuntimeCertificationServiceTest.runtime()
        );
        var service = new ModelMaterializationDispatchService(
            dispatches,
            builds,
            scoped,
            dags,
            gateway,
            tokens,
            runtimeCertification,
            sourceAvailability,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new TransactionTemplate(transactions)
        );
        return new Fixture(
            service,
            dispatches,
            builds,
            scoped,
            gateway,
            tokens,
            runtimeCertification,
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
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeCertificationService runtimeCertification,
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
