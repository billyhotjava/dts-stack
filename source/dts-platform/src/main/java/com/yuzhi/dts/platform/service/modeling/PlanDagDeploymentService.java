package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository.BindingRecord;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reconciles desired plan DAG files with the DAG actually registered by Airflow. */
@Service
public class PlanDagDeploymentService {

    private final PlanExecutionBindingRepository bindings;
    private final DbtDagService dags;
    private final AirflowClient airflow;
    private final Clock clock;

    @Autowired
    public PlanDagDeploymentService(
        PlanExecutionBindingRepository bindings,
        DbtDagService dags,
        AirflowClient airflow
    ) {
        this(bindings, dags, airflow, Clock.systemUTC());
    }

    PlanDagDeploymentService(
        PlanExecutionBindingRepository bindings,
        DbtDagService dags,
        AirflowClient airflow,
        Clock clock
    ) {
        this.bindings = Objects.requireNonNull(
            bindings,
            "bindings is required"
        );
        this.dags = Objects.requireNonNull(dags, "dags is required");
        this.airflow = Objects.requireNonNull(
            airflow,
            "airflow is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.plan-dag-reconcile-delay-ms:5000}"
    )
    public int reconcilePending() {
        int processed = 0;
        for (BindingRecord binding : bindings.findDeployable(20)) {
            reconcile(binding);
            processed++;
        }
        return processed;
    }

    private void reconcile(BindingRecord binding) {
        Instant now = clock.instant();
        if (binding.entryCount() < 1) {
            bindings.markUnknown(
                binding,
                "MODEL_PLAN_DAG_SCOPE_EMPTY",
                now
            );
            return;
        }
        String schedule = "CRON_ENABLED".equals(binding.scheduleMode())
            ? binding.cronExpression()
            : null;
        String timezone = schedule == null ? "UTC" : binding.timezone();
        try {
            DbtDagService.ManagedDagDeployment deployment =
                dags.ensurePlanDag(
                    binding.dagId(),
                    binding.id(),
                    schedule,
                    timezone,
                    binding.desiredDeploymentChecksum()
                );
            if (
                !binding
                    .desiredDeploymentChecksum()
                    .equals(deployment.deploymentChecksum())
            ) {
                bindings.markUnknown(
                    binding,
                    "MODEL_PLAN_DAG_DEPLOYMENT_CHECKSUM_DRIFT",
                    now
                );
                return;
            }
            boolean paused = bindings.requiresActivation(binding.id());
            if (airflow.setDagPaused(binding.dagId(), paused).isEmpty()) {
                bindings.markUnknown(
                    binding,
                    "MODEL_PLAN_DAG_NOT_REGISTERED",
                    now
                );
                return;
            }
            Optional<Map<String, Object>> actual =
                airflow.getDag(binding.dagId());
            if (
                actual.isEmpty() ||
                !matchesActual(
                    binding,
                    deployment.deploymentChecksum(),
                    actual.orElseThrow(), paused
                )
            ) {
                bindings.markUnknown(
                    binding,
                    "MODEL_PLAN_DAG_REGISTRATION_DRIFT",
                    now
                );
                return;
            }
            bindings.markActive(
                binding,
                deployment.deploymentChecksum(),
                schedule,
                timezone,
                paused,
                now
            );
        } catch (RuntimeException failure) {
            bindings.markUnknown(
                binding,
                "MODEL_PLAN_DAG_DEPLOYMENT_UNAVAILABLE",
                now
            );
        }
    }

    private static boolean matchesActual(
        BindingRecord binding,
        String checksum,
        Map<String, Object> actual,
        boolean paused
    ) {
        if (
            !binding.dagId().equals(String.valueOf(actual.get("dag_id"))) ||
            !Boolean.valueOf(paused).equals(actual.get("is_paused"))
        ) {
            return false;
        }
        Object tags = actual.get("tags");
        if (!(tags instanceof Collection<?> values)) return false;
        String expected = "deployment:" + checksum;
        return values
            .stream()
            .anyMatch(value -> {
                if (value instanceof Map<?, ?> tag) {
                    return expected.equals(String.valueOf(tag.get("name")));
                }
                return expected.equals(String.valueOf(value));
            });
    }
}
