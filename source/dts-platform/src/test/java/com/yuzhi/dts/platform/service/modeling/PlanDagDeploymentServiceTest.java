package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository.BindingRecord;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanDagDeploymentServiceTest {

    private static final UUID BINDING_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000076"
    );
    private static final String CHECKSUM = "a".repeat(64);
    private static final Instant NOW = Instant.parse(
        "2026-07-28T02:00:00Z"
    );

    @Test
    void activatesOnlyAfterAirflowReportsTheExactDeploymentTag() {
        PlanExecutionBindingRepository repository = mock(
            PlanExecutionBindingRepository.class
        );
        DbtDagService dags = mock(DbtDagService.class);
        AirflowClient airflow = mock(AirflowClient.class);
        BindingRecord binding = binding("DEPLOYING");
        when(repository.findDeployable(20)).thenReturn(List.of(binding));
        when(
            dags.ensurePlanDag(
                binding.dagId(),
                BINDING_ID,
                null,
                "UTC",
                CHECKSUM
            )
        )
            .thenReturn(
                new DbtDagService.ManagedDagDeployment(
                    binding.dagId(),
                    "sprint76-v1",
                    CHECKSUM,
                    Path.of("/tmp/plan.py")
                )
            );
        when(airflow.setDagPaused(binding.dagId(), false))
            .thenReturn(Optional.of(Map.of("is_paused", false)));
        when(airflow.getDag(binding.dagId()))
            .thenReturn(
                Optional.of(
                    Map.of(
                        "dag_id",
                        binding.dagId(),
                        "is_paused",
                        false,
                        "tags",
                        List.of(
                            Map.of(
                                "name",
                                "deployment:" + CHECKSUM
                            )
                        )
                    )
                )
            );
        PlanDagDeploymentService service = new PlanDagDeploymentService(
            repository,
            dags,
            airflow,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.reconcilePending()).isEqualTo(1);

        verify(repository).markActive(
            binding,
            CHECKSUM,
            null,
            "UTC",
            false,
            NOW
        );
    }

    @Test
    void keepsBindingUnknownWhenAirflowHasNotRegisteredTheDag() {
        PlanExecutionBindingRepository repository = mock(
            PlanExecutionBindingRepository.class
        );
        DbtDagService dags = mock(DbtDagService.class);
        AirflowClient airflow = mock(AirflowClient.class);
        BindingRecord binding = binding("DEPLOYING");
        when(repository.findDeployable(20)).thenReturn(List.of(binding));
        when(dags.ensurePlanDag(any(), any(), any(), any(), any()))
            .thenReturn(
                new DbtDagService.ManagedDagDeployment(
                    binding.dagId(),
                    "sprint76-v1",
                    CHECKSUM,
                    Path.of("/tmp/plan.py")
                )
            );
        when(airflow.setDagPaused(binding.dagId(), false))
            .thenReturn(Optional.empty());
        PlanDagDeploymentService service = new PlanDagDeploymentService(
            repository,
            dags,
            airflow,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.reconcilePending()).isEqualTo(1);

        verify(repository).markUnknown(
            binding,
            "MODEL_PLAN_DAG_NOT_REGISTERED",
            NOW
        );
    }

    private static BindingRecord binding(String status) {
        return new BindingRecord(
            BINDING_ID,
            "default",
            UUID.fromString(
                "20000000-0000-0000-0000-000000000076"
            ),
            "PROD",
            "postgres:primary",
            1,
            "MANUAL_ONLY",
            null,
            null,
            "b".repeat(64),
            CHECKSUM,
            "dts_plan_finance_prod_primary",
            status,
            null,
            2
        );
    }
}
