package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.OpenedRun;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.OperationalScope;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PlanOperationalRunServiceTest {

    private static final Instant NOW =
        Instant.parse("2026-07-28T09:00:00Z");
    private static final UUID PLAN_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID GROUP_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void persistsRunBeforeTriggeringAirflow() {
        Fixture fixture = fixture();
        OpenedRun opened = opened("PENDING", false);
        when(
            fixture.runs.open(
                eq("tenant-a"),
                eq(PLAN_ID),
                eq(BINDING_ID),
                any(),
                eq("MANUAL"),
                eq(null),
                eq("prod"),
                eq(NOW)
            )
        ).thenReturn(opened);
        when(fixture.runs.loadScope(GROUP_ID)).thenReturn(scope());
        when(fixture.runs.findPrepared(GROUP_ID)).thenReturn(Optional.empty());
        when(fixture.scoped.prepareCandidate(List.of())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/server-owned/project",
                "dim_finance",
                "b".repeat(64),
                List.of()
            )
        );
        when(fixture.tokens.issue(GROUP_ID, NOW)).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plusSeconds(900)
            )
        );
        when(
            fixture.runs.attachPreparedRuntime(
                GROUP_ID,
                "b".repeat(64),
                "sha256:" + "c".repeat(64),
                NOW.plusSeconds(900),
                false,
                NOW
            )
        ).thenReturn(true);
        when(fixture.airflow.getDagRun("dts_plan_binding", "run-1"))
            .thenReturn(Optional.empty());
        when(fixture.airflow.triggerDag(eq("dts_plan_binding"), any()))
            .thenReturn(Optional.of(Map.of("dag_run_id", "run-1")));

        var result = fixture.service.openManual(
            "tenant-a",
            "operator-a",
            PLAN_ID,
            BINDING_ID,
            "request-1"
        );

        assertThat(result.status()).isEqualTo("SUBMITTED");
        InOrder order = inOrder(fixture.runs, fixture.airflow);
        order.verify(fixture.runs)
            .open(
                eq("tenant-a"),
                eq(PLAN_ID),
                eq(BINDING_ID),
                any(),
                eq("MANUAL"),
                eq(null),
                eq("prod"),
                eq(NOW)
            );
        order.verify(fixture.airflow)
            .getDagRun("dts_plan_binding", "run-1");
        order.verify(fixture.airflow)
            .triggerDag(eq("dts_plan_binding"), any());
        order.verify(fixture.runs).markSubmitted(GROUP_ID, NOW);
    }

    @Test
    void terminalIdempotencyReplayDoesNotPrepareOrTriggerAgain() {
        Fixture fixture = fixture();
        when(fixture.runs.open(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(opened("COMPLETED", true));

        var result = fixture.service.openManual(
            "tenant-a",
            "operator-a",
            PLAN_ID,
            BINDING_ID,
            "request-1"
        );

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.replayed()).isTrue();
        verify(fixture.runs, never()).loadScope(any());
        verify(fixture.scoped, never()).prepareCandidate(any());
        verify(fixture.airflow, never()).getDagRun(any(), any());
        verify(fixture.airflow, never()).triggerDag(any(), any());
    }

    @Test
    void scheduledOpenPreparesDurableRunWithoutTriggeringAirflow() {
        Fixture fixture = fixture();
        Instant logicalDate =
            Instant.parse("2026-07-28T08:00:00Z");
        when(fixture.runs.tenantForBinding(BINDING_ID))
            .thenReturn("tenant-a");
        when(
            fixture.runs.open(
                "tenant-a",
                null,
                BINDING_ID,
                "scheduled__2026-07-28T08:00:00+00:00",
                "CRON",
                logicalDate,
                "prod",
                NOW
            )
        ).thenReturn(opened("PENDING", false));
        when(fixture.runs.loadScope(GROUP_ID)).thenReturn(scope());
        when(fixture.runs.findPrepared(GROUP_ID)).thenReturn(Optional.empty());
        when(fixture.scoped.prepareCandidate(List.of())).thenReturn(
            new DbtScopedProjectService.ScopedCandidateProject(
                "/server-owned/project",
                "dim_finance",
                "b".repeat(64),
                List.of()
            )
        );
        when(fixture.tokens.issue(GROUP_ID, NOW)).thenReturn(
            new ModelRuntimeSpecTokenCodec.IssuedToken(
                "runtime-token",
                "sha256:" + "c".repeat(64),
                NOW.plusSeconds(900)
            )
        );
        when(
            fixture.runs.attachPreparedRuntime(
                GROUP_ID,
                "b".repeat(64),
                "sha256:" + "c".repeat(64),
                NOW.plusSeconds(900),
                true,
                NOW
            )
        ).thenReturn(true);

        var result = fixture.service.openScheduled(
            BINDING_ID,
            "scheduled__2026-07-28T08:00:00+00:00",
            logicalDate
        );

        assertThat(result.triggerType()).isEqualTo("CRON");
        assertThat(result.runtimeSpecToken()).isEqualTo("runtime-token");
        verify(fixture.airflow, never()).triggerDag(any(), any());
    }

    @Test
    void recoveryReconcilesExactExistingDagRunBeforeAnyRetrigger() {
        Fixture fixture = fixture();
        OpenedRun unknown = opened("UNKNOWN", true);
        when(fixture.runs.claimRecoverableManual(NOW))
            .thenReturn(Optional.of(unknown), Optional.empty());
        when(fixture.airflow.getDagRun("dts_plan_binding", "run-1"))
            .thenReturn(Optional.of(Map.of("dag_run_id", "run-1")));

        assertThat(fixture.service.reconcilePendingManualRuns())
            .isEqualTo(1);

        verify(fixture.runs).markSubmitted(GROUP_ID, NOW);
        verify(fixture.airflow, never()).triggerDag(any(), any());
        verify(fixture.scoped, never()).prepareCandidate(any());
    }

    private static Fixture fixture() {
        PlanOperationalRunRepository runs =
            mock(PlanOperationalRunRepository.class);
        DbtScopedProjectService scoped =
            mock(DbtScopedProjectService.class);
        ModelRuntimeSpecTokenCodec tokens =
            mock(ModelRuntimeSpecTokenCodec.class);
        AirflowClient airflow = mock(AirflowClient.class);
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setExecutionTargetKey("postgres-primary");
        DbtConfigService dbtConfig = mock(DbtConfigService.class);
        when(dbtConfig.loadRuntimeConfig()).thenReturn(
            new DbtConfigService.DbtWorkspaceConfig(
                true,
                "/project",
                "/profiles",
                "dts",
                "prod",
                UUID.randomUUID(),
                "warehouse",
                "finance",
                Map.of()
            )
        );
        ModelSpecPlanWriteAccessPort access =
            mock(ModelSpecPlanWriteAccessPort.class);
        when(access.canMaintain("tenant-a", PLAN_ID, "operator-a"))
            .thenReturn(true);
        ReleaseDutyResolver duties = mock(ReleaseDutyResolver.class);
        when(duties.currentDuties()).thenReturn(
            Set.of(DeliveryActorRole.RELEASE_OPERATOR)
        );
        return new Fixture(
            runs,
            scoped,
            tokens,
            airflow,
            new PlanOperationalRunService(
                runs,
                scoped,
                tokens,
                airflow,
                properties,
                dbtConfig,
                access,
                duties,
                Clock.fixed(NOW, ZoneOffset.UTC)
            )
        );
    }

    private static OpenedRun opened(String status, boolean replayed) {
        return new OpenedRun(
            GROUP_ID,
            "tenant-a",
            PLAN_ID,
            BINDING_ID,
            4,
            "MANUAL",
            "dts_plan_binding",
            "run-1",
            "a".repeat(64),
            null,
            status,
            replayed
        );
    }

    private static OperationalScope scope() {
        return new OperationalScope(
            GROUP_ID,
            "tenant-a",
            BINDING_ID,
            4,
            "postgres-primary",
            "prod",
            "dts_plan_binding",
            "run-1",
            "a".repeat(64),
            null,
            List.of()
        );
    }

    private record Fixture(
        PlanOperationalRunRepository runs,
        DbtScopedProjectService scoped,
        ModelRuntimeSpecTokenCodec tokens,
        AirflowClient airflow,
        PlanOperationalRunService service
    ) {}
}
