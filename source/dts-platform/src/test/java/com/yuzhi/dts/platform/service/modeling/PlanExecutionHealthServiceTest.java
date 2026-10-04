package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionHealthRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionHealthRepository.BindingHealthRecord;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanExecutionHealthServiceTest {

    private static final UUID PLAN_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void reportsAirflowUnavailableWithoutInventingActualSchedule() {
        Fixture fixture = fixture(binding("MANUAL_ONLY", null, null));
        when(fixture.airflow.getDag("dts_plan_20000000000000000000000000000002"))
            .thenThrow(new IllegalStateException("offline"));

        var binding = fixture.service
            .workspace("tenant-a", "operator-a", PLAN_ID)
            .bindings()
            .getFirst();

        assertThat(binding.state()).isEqualTo("UNKNOWN");
        assertThat(binding.airflowState()).isEqualTo("UNKNOWN");
        assertThat(binding.actualSchedule()).isNull();
        assertThat(binding.nextRunAt()).isNull();
        assertThat(binding.primaryBlocker().code())
            .isEqualTo("MODEL_PLAN_AIRFLOW_UNAVAILABLE");
        assertThat(binding.allowedActions()).containsExactly(
            "REPAIR_DEPLOYMENT"
        );
    }

    @Test
    void allowsRunNowOnlyWhenActiveDagIsActuallyObserved() {
        Fixture fixture = fixture(binding("MANUAL_ONLY", null, null));
        Map<String, Object> actual = new HashMap<>();
        actual.put("dag_id", "dts_plan_20000000000000000000000000000002");
        actual.put("schedule_interval", null);
        actual.put("timezone", "UTC");
        actual.put("is_paused", false);
        actual.put("next_dagrun", null);
        when(fixture.airflow.getDag("dts_plan_20000000000000000000000000000002"))
            .thenReturn(Optional.of(actual));
        when(fixture.airflow.listDagRuns("dts_plan_20000000000000000000000000000002", 1))
            .thenReturn(Optional.empty());

        var binding = fixture.service
            .workspace("tenant-a", "operator-a", PLAN_ID)
            .bindings()
            .getFirst();

        assertThat(binding.state()).isEqualTo("ONLINE");
        assertThat(binding.primaryBlocker()).isNull();
        assertThat(binding.allowedActions()).containsExactly("RUN_NOW");
    }

    @Test
    void exposesCronDriftAsOneOperatorOwnedRepair() {
        Fixture fixture = fixture(
            binding("CRON_ENABLED", "0 2 * * *", "Asia/Shanghai")
        );
        when(fixture.airflow.getDag("dts_plan_20000000000000000000000000000002"))
            .thenReturn(
                Optional.of(
                    Map.of(
                        "dag_id",
                        "dts_plan_20000000000000000000000000000002",
                        "schedule_interval",
                        Map.of(
                            "__type",
                            "CronExpression",
                            "value",
                            "0 3 * * *"
                        ),
                        "timezone",
                        "Asia/Shanghai",
                        "is_paused",
                        false
                    )
                )
            );
        when(fixture.airflow.listDagRuns("dts_plan_20000000000000000000000000000002", 1))
            .thenReturn(Optional.empty());

        var binding = fixture.service
            .workspace("tenant-a", "operator-a", PLAN_ID)
            .bindings()
            .getFirst();

        assertThat(binding.state()).isEqualTo("DEGRADED");
        assertThat(binding.primaryBlocker().code())
            .isEqualTo("MODEL_PLAN_DAG_REGISTRATION_DRIFT");
        assertThat(binding.primaryBlocker().owner())
            .isEqualTo("RELEASE_OPERATOR");
        assertThat(binding.allowedActions()).containsExactly(
            "REPAIR_DEPLOYMENT"
        );
    }

    @Test
    void legacyReleaseDagOffersOnlyRepairWithoutReadingAnotherRun() {
        Fixture fixture = fixture(binding("MANUAL_ONLY", null, null,
            "dts_release_build_postgres_primary", null));

        var binding = fixture.service.workspace("tenant-a", "operator-a", PLAN_ID)
            .bindings().getFirst();

        assertThat(binding.allowedActions()).containsExactly("REPAIR_DEPLOYMENT");
        assertThat(binding.primaryBlocker().code())
            .isEqualTo("MODEL_PLAN_BINDING_OPERATIONAL_DAG_REQUIRED");
        assertThat(binding.latestDagRun()).isNull();
        verifyNoInteractions(fixture.airflow);
    }

    @Test
    void activeRunPreventsRepairOfLegacyBinding() {
        Fixture fixture = fixture(binding("MANUAL_ONLY", null, null,
            "dts_release_build_postgres_primary", "SUBMITTED"));

        var binding = fixture.service.workspace("tenant-a", "operator-a", PLAN_ID)
            .bindings().getFirst();

        assertThat(binding.allowedActions()).isEmpty();
        verifyNoInteractions(fixture.airflow);
    }

    private static Fixture fixture(BindingHealthRecord binding) {
        PlanExecutionHealthRepository repository =
            mock(PlanExecutionHealthRepository.class);
        when(repository.findByPlan("tenant-a", PLAN_ID))
            .thenReturn(List.of(binding));
        AirflowClient airflow = mock(AirflowClient.class);
        ModelSpecPlanWriteAccessPort access =
            mock(ModelSpecPlanWriteAccessPort.class);
        when(access.canMaintain("tenant-a", PLAN_ID, "operator-a"))
            .thenReturn(true);
        ReleaseDutyResolver duties = mock(ReleaseDutyResolver.class);
        when(duties.currentDuties()).thenReturn(
            Set.of(DeliveryActorRole.RELEASE_OPERATOR)
        );
        return new Fixture(
            airflow,
            new PlanExecutionHealthService(
                repository,
                airflow,
                access,
                duties
            )
        );
    }

    private static BindingHealthRecord binding(
        String scheduleMode,
        String schedule,
        String timezone
    ) {
        return binding(scheduleMode, schedule, timezone,
            "dts_plan_20000000000000000000000000000002", null);
    }

    private static BindingHealthRecord binding(
        String scheduleMode, String schedule, String timezone,
        String dagId, String latestRunStatus
    ) {
        return new BindingHealthRecord(
            BINDING_ID,
            3,
            "PROD",
            scheduleMode,
            schedule,
            timezone,
            schedule,
            schedule == null ? "UTC" : timezone,
            "ACTIVE",
            "a".repeat(64),
            "b".repeat(64),
            "b".repeat(64),
            dagId,
            false,
            Instant.parse("2026-07-28T08:00:00Z"),
            null,
            null,
            null,
            2,
            null,
            null,
            null,
            latestRunStatus,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private record Fixture(
        AirflowClient airflow,
        PlanExecutionHealthService service
    ) {}
}
