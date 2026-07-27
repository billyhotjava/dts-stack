package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository.RuntimeSpecRecord;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
        assertThat(view.profileLeaseId()).isEqualTo(LEASE_ID);
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

        assertThat(replay.profileLeaseId()).isEqualTo(LEASE_ID);
        verify(fixture.leases).viewActive(LEASE_ID);
        verify(fixture.leases, never()).issue(
            org.mockito.ArgumentMatchers.any()
        );
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
        var service = new ModelMaterializationRuntimeSpecService(
            dispatches,
            tokens,
            leases,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, dispatches, tokens, leases);
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
        DbtRuntimeProfileLeaseService leases
    ) {}
}
