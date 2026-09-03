package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ModelMaterializationRuntimeSpecServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-27T14:00:00Z");
    private static final UUID DISPATCH_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEASE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PIPELINE_RUN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void issuesOneLeaseAndReturnsNoPathOrCredential() {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token =
            fixture.tokens.issue(DISPATCH_ID, NOW);
        when(
            fixture.dispatches.lockRuntimeSpec(token.digest())
        ).thenReturn(Optional.of(runtime(token, null)));
        LeaseView lease = lease();
        when(fixture.leases.issue(org.mockito.ArgumentMatchers.any()))
            .thenReturn(lease);
        when(
            fixture.dispatches.attachRuntimeLease(
                DISPATCH_ID,
                LEASE_ID,
                NOW
            )
        ).thenReturn(true);

        var view = fixture.service.consume(token.token());

        assertThat(view.pipelineRunGroupId()).isEqualTo(DISPATCH_ID);
        assertThat(view.runPurpose()).isEqualTo("RELEASE_BUILD");
        assertThat(view.selector())
            .isEqualTo("+dim_customer +fct_invoice");
        assertThat(view.profileLeaseId()).isEqualTo(LEASE_ID);
        assertThat(view.runtimeProfileId()).isNull();
        assertThat(view.imageRef()).isNull();
        assertThat(view.toString())
            .doesNotContain("/run/")
            .doesNotContain("/dev/shm")
            .doesNotContain("password")
            .doesNotContain("jdbc:");
        verify(fixture.leases).issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                "dts_rc_test",
                "DEV",
                "postgres-primary"
            )
        );
        verify(fixture.sourceAvailability).pinDispatchCurrent(
            DISPATCH_ID,
            NOW
        );
        AuditCall audit = fixture.audit.singleCall();
        assertThat(audit.actor()).isEqualTo("airflow");
        assertThat(audit.eventIdentity())
            .isEqualTo(runtimeSpecEventIdentity());
        assertThat(audit.occurredAt()).isEqualTo(NOW);
        assertThat(audit.actionCode())
            .isEqualTo("MODEL_MATERIALIZATION_RUNTIME_SPEC_CONSUMED");
        assertThat(audit.stage()).isEqualTo(AuditStage.SUCCESS);
        assertThat(audit.resourceId()).isEqualTo(DISPATCH_ID.toString());
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
                "status",
                "leaseId",
                "expiresAt"
            )
            .containsEntry("status", "CONSUMED")
            .containsEntry("leaseId", LEASE_ID)
            .doesNotContainKeys(
                "checksum",
                "digest",
                "token",
                "credential",
                "selector"
            );
        assertThat(payload.toString())
            .doesNotContain(token.token())
            .doesNotContain(token.digest())
            .doesNotContain("dim_customer fct_invoice")
            .doesNotContain(lease.credentialVersionRef());
    }

    @Test
    void responseLossReplayReturnsSameLeaseWithoutIssuingAnother() {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token =
            fixture.tokens.issue(DISPATCH_ID, NOW);
        when(
            fixture.dispatches.lockRuntimeSpec(token.digest())
        ).thenReturn(Optional.of(runtime(token, LEASE_ID)));
        when(fixture.leases.viewActive(LEASE_ID)).thenReturn(lease());

        var replay = fixture.service.consume(token.token());
        var repeatedReplay = fixture.service.consume(token.token());

        assertThat(replay.profileLeaseId()).isEqualTo(LEASE_ID);
        assertThat(repeatedReplay.profileLeaseId()).isEqualTo(LEASE_ID);
        verify(fixture.leases, times(2)).viewActive(LEASE_ID);
        verify(fixture.sourceAvailability, times(2)).pinDispatchCurrent(
            DISPATCH_ID,
            NOW
        );
        verify(fixture.leases, never()).issue(
            org.mockito.ArgumentMatchers.any()
        );
        assertThat(
            fixture.audit.calls().stream()
                .map(AuditCall::eventIdentity)
                .toList()
        )
            .containsExactly(
                runtimeSpecEventIdentity(),
                runtimeSpecEventIdentity()
            );
        assertThat(
            fixture.audit.calls().stream()
                .map(AuditCall::occurredAt)
                .toList()
        ).containsOnly(NOW.minusSeconds(1));
    }

    @Test
    void auditFailurePropagatesFromTransactionalConsumeAfterAttach()
        throws Exception {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token =
            fixture.tokens.issue(DISPATCH_ID, NOW);
        when(fixture.dispatches.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, null)));
        when(fixture.leases.issue(any())).thenReturn(lease());
        when(
            fixture.dispatches.attachRuntimeLease(
                DISPATCH_ID,
                LEASE_ID,
                NOW
            )
        ).thenReturn(true);
        fixture.audit.failNext(
            new IllegalStateException("audit unavailable")
        );

        assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");
        verify(fixture.dispatches).attachRuntimeLease(
            DISPATCH_ID,
            LEASE_ID,
            NOW
        );
        verify(fixture.leases).release(LEASE_ID);
        assertThat(
            ModelMaterializationRuntimeSpecService.class
                .getMethod("consume", String.class)
                .getAnnotation(Transactional.class)
        ).isNotNull();
    }

    @Test
    void auditFailureDoesNotReleaseExistingLease() {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token =
            fixture.tokens.issue(DISPATCH_ID, NOW);
        when(fixture.dispatches.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, LEASE_ID)));
        when(fixture.leases.viewActive(LEASE_ID)).thenReturn(lease());
        fixture.audit.failNext(
            new IllegalStateException("audit unavailable")
        );

        assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");
        verify(fixture.leases).viewActive(LEASE_ID);
        verify(fixture.leases, never()).release(LEASE_ID);
    }

    @Test
    void invalidOrExpiredTokenFailsBeforeLeaseIssue() {
        Fixture fixture = fixture();
        when(
            fixture.dispatches.lockRuntimeSpec(
                fixture.tokens.tokenDigest("forged")
            )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.consume("forged"))
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo("MODEL_RUNTIME_SPEC_TOKEN_INVALID");

        ModelRuntimeSpecTokenCodec.IssuedToken token =
            fixture.tokens.issue(DISPATCH_ID, NOW);
        RuntimeSpecRecord expired = new RuntimeSpecRecord(
            runtime(token, null).dispatchId(),
            runtime(token, null).tenantId(),
            runtime(token, null).candidateId(),
            runtime(token, null).candidateVersion(),
            runtime(token, null).attempt(),
            runtime(token, null).executionTargetKey(),
            runtime(token, null).airflowDagId(),
            runtime(token, null).airflowRunId(),
            runtime(token, null).scopedBundleChecksum(),
            runtime(token, null).runtimeTokenDigest(),
            NOW,
            null,
            null,
            runtime(token, null).environment(),
            runtime(token, null).targetName(),
            runtime(token, null).selector(),
            runtime(token, null).pipelineRunId()
        );
        when(
            fixture.dispatches.lockRuntimeSpec(token.digest())
        ).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo("MODEL_RUNTIME_SPEC_TOKEN_EXPIRED");
        verify(fixture.leases, never()).issue(
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void availabilityFenceFailsBeforeRuntimeLeaseIssue() {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token = fixture.tokens.issue(DISPATCH_ID, NOW);
        when(fixture.dispatches.lockRuntimeSpec(token.digest())).thenReturn(Optional.of(runtime(token, null)));
        doThrow(
            new ModelReleaseCandidateException(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
                "source fenced",
                ModelReleaseCandidateException.Kind.UNPROCESSABLE
            )
        ).when(fixture.sourceAvailability).requireDispatchCurrent(DISPATCH_ID);

        assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error -> ((ModelMaterializationRuntimeException) error).code())
            .isEqualTo(ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE);
        verify(fixture.availabilityAudit).recordRuntimeDenied(
            org.mockito.ArgumentMatchers.any(RuntimeSpecRecord.class),
            org.mockito.ArgumentMatchers.eq(ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE),
            org.mockito.ArgumentMatchers.eq(NOW)
        );
        verify(fixture.leases, never()).issue(any());
    }

    @Test
    void generationPinFailureCompensatesNewLeaseAndPersistsDenialAudit() {
        Fixture fixture = fixture();
        ModelRuntimeSpecTokenCodec.IssuedToken token = fixture.tokens.issue(
            DISPATCH_ID,
            NOW
        );
        when(fixture.dispatches.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, null)));
        when(fixture.leases.issue(any())).thenReturn(lease());
        when(
            fixture.dispatches.attachRuntimeLease(
                DISPATCH_ID,
                LEASE_ID,
                NOW
            )
        ).thenReturn(true);
        doThrow(
            new ModelReleaseCandidateException(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE,
                "source generation cannot be pinned",
                ModelReleaseCandidateException.Kind.UNPROCESSABLE
            )
        )
            .when(fixture.sourceAvailability)
            .pinDispatchCurrent(DISPATCH_ID, NOW);

        assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE
            );

        verify(fixture.leases).release(LEASE_ID);
        verify(fixture.availabilityAudit).recordRuntimeDenied(
            any(RuntimeSpecRecord.class),
            eq(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE
            ),
            eq(NOW)
        );
    }

    private static Fixture fixture() {
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setRuntimeSpecSigningKey("k".repeat(64));
        properties.setRuntimeSpecTokenTtl(Duration.ofMinutes(15));
        ModelRuntimeSpecTokenCodec tokens =
            new ModelRuntimeSpecTokenCodec(properties);
        var dispatches = mock(
            ModelMaterializationDispatchRepository.class
        );
        var leases = mock(DbtRuntimeProfileLeaseService.class);
        var sourceAvailability = mock(ModelMaterializationSourceAvailabilityGuard.class);
        var availabilityAudit = mock(ModelMaterializationAvailabilityAuditService.class);
        var audit = new RecordingAuditService();
        var service = new ModelMaterializationRuntimeSpecService(
            dispatches,
            tokens,
            sourceAvailability,
            availabilityAudit,
            leases,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(
            service,
            dispatches,
            tokens,
            sourceAvailability,
            availabilityAudit,
            leases,
            audit
        );
    }

    private static String runtimeSpecEventIdentity() {
        return (
            "model-materialization-runtime-spec:" +
            DISPATCH_ID +
            ":attempt:1:consumed"
        );
    }

    private static RuntimeSpecRecord runtime(
        ModelRuntimeSpecTokenCodec.IssuedToken token,
        UUID leaseId
    ) {
        return new RuntimeSpecRecord(
            DISPATCH_ID,
            "tenant-a",
            UUID.fromString(
                "40000000-0000-0000-0000-000000000004"
            ),
            3,
            1,
            "postgres-primary",
            "dts_release_build_postgres_primary",
            "dts_rc_test",
            "a".repeat(64),
            token.digest(),
            token.expiresAt(),
            leaseId,
            leaseId == null ? null : NOW.minusSeconds(1),
            "DEV",
            "dev",
            "dim_customer fct_invoice",
            PIPELINE_RUN_ID
        );
    }

    private static LeaseView lease() {
        return new LeaseView(
            LEASE_ID,
            "dev",
            NOW.plus(Duration.ofMinutes(10)),
            "sha256:" + "b".repeat(64)
        );
    }

    private record Fixture(
        ModelMaterializationRuntimeSpecService service,
        ModelMaterializationDispatchRepository dispatches,
        ModelRuntimeSpecTokenCodec tokens,
        ModelMaterializationSourceAvailabilityGuard sourceAvailability,
        ModelMaterializationAvailabilityAuditService availabilityAudit,
        DbtRuntimeProfileLeaseService leases,
        RecordingAuditService audit
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
            return UUID.nameUUIDFromBytes(eventIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
}
