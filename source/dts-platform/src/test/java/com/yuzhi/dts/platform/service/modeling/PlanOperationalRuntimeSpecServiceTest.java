package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.RuntimeRecord;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService.LeaseView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanOperationalRuntimeSpecServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-28T09:00:00Z");
    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEASE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PIPELINE_RUN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void consumesOperationalTokenIntoOneCredentialFreeLeaseView() {
        Fixture fixture = fixture();
        var token = fixture.tokens.issue(GROUP_ID, NOW);
        when(fixture.runs.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, null)));
        when(fixture.leases.issue(any())).thenReturn(lease());
        when(
            fixture.runs.attachRuntimeLease(
                GROUP_ID,
                LEASE_ID,
                NOW
            )
        ).thenReturn(true);

        var view = fixture.service.consume(token.token());

        assertThat(view.runPurpose()).isEqualTo("OPERATIONAL_RUN");
        assertThat(view.pipelineRunGroupId()).isEqualTo(GROUP_ID);
        assertThat(view.profileLeaseId()).isEqualTo(LEASE_ID);
        assertThat(view.toString())
            .doesNotContain("/run/")
            .doesNotContain("password")
            .doesNotContain("jdbc:");
        verify(fixture.leases).issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                "run-1",
                "PROD",
                "postgres-primary"
            )
        );
    }

    @Test
    void responseReplayUsesTheSameLease() {
        Fixture fixture = fixture();
        var token = fixture.tokens.issue(GROUP_ID, NOW);
        when(fixture.runs.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, LEASE_ID)));
        when(fixture.leases.viewActive(LEASE_ID)).thenReturn(lease());

        var view = fixture.service.consume(token.token());

        assertThat(view.profileLeaseId()).isEqualTo(LEASE_ID);
        verify(fixture.leases).viewActive(LEASE_ID);
        verify(fixture.leases, never()).issue(any());
    }

    @Test
    void targetFailureReturnsItsStableCodeInsteadOfAnUnhandledError() {
        Fixture fixture = fixture();
        var token = fixture.tokens.issue(GROUP_ID, NOW);
        when(fixture.runs.lockRuntimeSpec(token.digest()))
            .thenReturn(Optional.of(runtime(token, null)));
        when(fixture.leases.issue(any())).thenThrow(
            new com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileException(
                "DBT_TARGET_DATASOURCE_NOT_FOUND",
                "目标数仓数据源不存在",
                null
            )
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.consume(token.token()))
            .isInstanceOf(PlanExecutionException.class)
            .satisfies(error -> {
                assertThat(((PlanExecutionException) error).code()).isEqualTo("DBT_TARGET_DATASOURCE_NOT_FOUND");
                assertThat(((PlanExecutionException) error).kind()).isEqualTo(PlanExecutionException.Kind.UNAVAILABLE);
            });
        verify(fixture.runs, never()).attachRuntimeLease(any(), any(), any());
    }

    private static Fixture fixture() {
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setRuntimeSpecSigningKey("k".repeat(64));
        properties.setRuntimeSpecTokenTtl(Duration.ofMinutes(15));
        ModelRuntimeSpecTokenCodec tokens =
            new ModelRuntimeSpecTokenCodec(properties);
        PlanOperationalRunRepository runs =
            mock(PlanOperationalRunRepository.class);
        DbtRuntimeProfileLeaseService leases =
            mock(DbtRuntimeProfileLeaseService.class);
        return new Fixture(
            new PlanOperationalRuntimeSpecService(
                runs,
                tokens,
                leases,
                Clock.fixed(NOW, ZoneOffset.UTC)
            ),
            runs,
            tokens,
            leases
        );
    }

    private static RuntimeRecord runtime(
        ModelRuntimeSpecTokenCodec.IssuedToken token,
        UUID leaseId
    ) {
        return new RuntimeRecord(
            GROUP_ID,
            "tenant-a",
            UUID.fromString(
                "40000000-0000-0000-0000-000000000004"
            ),
            3,
            "postgres-primary",
            "prod",
            "dts_plan_finance",
            "run-1",
            "a".repeat(64),
            token.digest(),
            token.expiresAt(),
            leaseId,
            leaseId == null ? null : NOW.minusSeconds(1),
            "PROD",
            "dim_finance",
            PIPELINE_RUN_ID
        );
    }

    private static LeaseView lease() {
        return new LeaseView(
            LEASE_ID,
            "prod",
            NOW.plus(Duration.ofMinutes(10)),
            "sha256:" + "b".repeat(64)
        );
    }

    private record Fixture(
        PlanOperationalRuntimeSpecService service,
        PlanOperationalRunRepository runs,
        ModelRuntimeSpecTokenCodec tokens,
        DbtRuntimeProfileLeaseService leases
    ) {}
}
