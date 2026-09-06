package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionHealthRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionHealthRepository.BindingHealthRecord;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Server-owned projection for desired schedule, actual Airflow state and platform evidence.
 */
@Service
public class PlanExecutionHealthService {

    private final PlanExecutionHealthRepository repository;
    private final AirflowClient airflow;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver duties;

    public PlanExecutionHealthService(
        PlanExecutionHealthRepository repository,
        AirflowClient airflow,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties
    ) {
        this.repository = Objects.requireNonNull(
            repository,
            "repository is required"
        );
        this.airflow = Objects.requireNonNull(
            airflow,
            "airflow is required"
        );
        this.planAccess = Objects.requireNonNull(
            planAccess,
            "planAccess is required"
        );
        this.duties = Objects.requireNonNull(duties, "duties is required");
    }

    public WorkspaceView workspace(
        String tenantId,
        String actorId,
        UUID planId
    ) {
        Access access = authorize(tenantId, actorId, planId);
        List<BindingView> bindings = repository
            .findByPlan(access.tenantId(), access.planId())
            .stream()
            .map(binding -> project(binding, access))
            .toList();
        return new WorkspaceView(
            access.planId(),
            bindings.isEmpty() ? "NOT_DEPLOYED" : "READY",
            bindings
        );
    }

    private BindingView project(
        BindingHealthRecord binding,
        Access access
    ) {
        AirflowActual actual = airflowActual(binding);
        boolean deploymentChecksumMatches =
            binding.desiredDeploymentChecksum() != null &&
            binding
                .desiredDeploymentChecksum()
                .equals(binding.deployedChecksum());
        boolean scheduleMatches = scheduleMatches(binding, actual);
        boolean activeRun = Set.of("QUEUED", "SUBMITTED", "UNKNOWN")
            .contains(Objects.toString(binding.latestRunStatus(), ""));
        boolean executionAvailable =
            operationalDagMatches(binding) &&
            "ACTIVE".equals(binding.deploymentStatus()) &&
            deploymentChecksumMatches &&
            "OBSERVED".equals(actual.state()) &&
            !Boolean.TRUE.equals(actual.paused()) &&
            scheduleMatches;
        List<String> allowedActions = new ArrayList<>();
        if (access.operatorWithPlanAccess() && executionAvailable && !activeRun) {
            allowedActions.add("RUN_NOW");
        }
        BlockerView blocker = blocker(
            binding,
            actual,
            deploymentChecksumMatches,
            scheduleMatches
        );
        if (
            access.operatorWithPlanAccess() &&
            !activeRun &&
            !executionAvailable &&
            Set.of("ACTIVE", "STALE", "FAILED", "UNKNOWN")
                .contains(Objects.toString(binding.deploymentStatus(), "")) &&
            blocker != null
        ) {
            allowedActions.add("REPAIR_DEPLOYMENT");
        }
        return new BindingView(
            binding.id(),
            binding.version(),
            binding.environment(),
            executionAvailable && blocker == null
                ? "ONLINE"
                : state(binding, actual),
            binding.scheduleMode(),
            binding.cronExpression(),
            binding.timezone(),
            binding.effectiveSchedule(),
            binding.effectiveTimezone(),
            binding.deploymentStatus(),
            binding.desiredDeploymentChecksum(),
            binding.deployedChecksum(),
            binding.airflowDagId(),
            actual.state(),
            actual.schedule(),
            actual.timezone(),
            actual.paused(),
            actual.nextRunAt(),
            actual.latestDagRun(),
            new OperationalRunView(
                binding.latestRunGroupId(),
                binding.latestAirflowRunId(),
                binding.latestTriggerType(),
                binding.latestRunStatus(),
                binding.latestRunErrorCode(),
                binding.latestRunCreatedAt(),
                binding.latestRunStartedAt(),
                binding.latestRunFinishedAt()
            ),
            new RelationEvidenceView(
                binding.latestRelationVerified(),
                binding.latestRelationExists(),
                binding.latestRelationErrorCode(),
                binding.latestPhysicalRelation(),
                binding.latestRelationObservedAt()
            ),
            blocker,
            List.copyOf(allowedActions)
        );
    }

    private AirflowActual airflowActual(BindingHealthRecord binding) {
        if (!operationalDagMatches(binding)) return AirflowActual.notRegistered();
        try {
            var dag = airflow.getDag(binding.airflowDagId());
            if (dag.isEmpty()) {
                return AirflowActual.notRegistered();
            }
            Map<String, Object> value = dag.orElseThrow();
            DagRunActual latest = airflow
                .listDagRuns(binding.airflowDagId(), 1)
                .map(PlanExecutionHealthService::latestDagRun)
                .orElse(null);
            return new AirflowActual(
                "OBSERVED",
                schedule(value.get("schedule_interval")),
                text(value.get("timezone")),
                bool(value.get("is_paused")),
                instant(value.get("next_dagrun")),
                latest
            );
        } catch (RuntimeException unavailable) {
            return AirflowActual.unknown();
        }
    }

    private static boolean operationalDagMatches(BindingHealthRecord binding) {
        return ("dts_plan_" + binding.id().toString().replace("-", ""))
            .equals(binding.airflowDagId());
    }

    private static DagRunActual latestDagRun(
        Map<String, Object> response
    ) {
        Object raw = response.get("dag_runs");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            return null;
        }
        Object first = values.getFirst();
        if (!(first instanceof Map<?, ?> run)) return null;
        return new DagRunActual(
            text(run.get("dag_run_id")),
            text(run.get("state")),
            instant(run.get("logical_date")),
            instant(run.get("start_date")),
            instant(run.get("end_date"))
        );
    }

    private static String schedule(Object raw) {
        if (raw instanceof Map<?, ?> value) {
            return text(value.get("value"));
        }
        return text(raw);
    }

    private static boolean scheduleMatches(
        BindingHealthRecord binding,
        AirflowActual actual
    ) {
        if (!"OBSERVED".equals(actual.state())) return false;
        String desired = "CRON_ENABLED".equals(binding.scheduleMode())
            ? binding.cronExpression()
            : null;
        boolean actualMatches =
            Objects.equals(normalize(desired), normalize(actual.schedule()));
        return (
            actualMatches &&
            Objects.equals(
                normalize(desired),
                normalize(binding.effectiveSchedule())
            ) &&
            (
                desired == null ||
                Objects.equals(
                    normalize(binding.timezone()),
                    normalize(binding.effectiveTimezone())
                )
            )
        );
    }

    private static BlockerView blocker(
        BindingHealthRecord binding,
        AirflowActual actual,
        boolean checksumMatches,
        boolean scheduleMatches
    ) {
        if (!operationalDagMatches(binding)) {
            return new BlockerView(
                "MODEL_PLAN_BINDING_OPERATIONAL_DAG_REQUIRED",
                "运行配置需要修复",
                "RELEASE_OPERATOR"
            );
        }
        if (!"ACTIVE".equals(binding.deploymentStatus())) {
            return new BlockerView(
                binding.deploymentErrorCode() == null
                    ? "MODEL_PLAN_DAG_DEPLOYMENT_INCOMPLETE"
                    : binding.deploymentErrorCode(),
                "计划 DAG 尚未完成部署或已发生漂移",
                "RELEASE_OPERATOR"
            );
        }
        if (!"OBSERVED".equals(actual.state())) {
            return new BlockerView(
                "MODEL_PLAN_AIRFLOW_UNAVAILABLE",
                "当前无法读取 Airflow 实际状态",
                "PLATFORM_OPERATOR"
            );
        }
        if (!checksumMatches || !scheduleMatches) {
            return new BlockerView(
                "MODEL_PLAN_DAG_REGISTRATION_DRIFT",
                "Airflow 实际 DAG 与当前上线版本不一致",
                "RELEASE_OPERATOR"
            );
        }
        if (Boolean.TRUE.equals(actual.paused())) {
            return new BlockerView(
                "MODEL_PLAN_DAG_PAUSED",
                "Airflow 中的计划 DAG 已暂停",
                "RELEASE_OPERATOR"
            );
        }
        if (
            binding.latestRunStatus() != null &&
            Set.of("FAILED", "UNKNOWN", "BLOCKED").contains(
                binding.latestRunStatus()
            )
        ) {
            return new BlockerView(
                binding.latestRunErrorCode() == null
                    ? "MODEL_OPERATIONAL_RUN_FAILED"
                    : binding.latestRunErrorCode(),
                "最近一次生产计算未成功",
                "RELEASE_OPERATOR"
            );
        }
        if (Boolean.FALSE.equals(binding.latestRelationVerified())) {
            return new BlockerView(
                binding.latestRelationErrorCode() == null
                    ? "MODEL_OPERATIONAL_RELATION_UNVERIFIED"
                    : binding.latestRelationErrorCode(),
                "最近一次生产计算未通过真实关系核验",
                "MODEL_MAINTAINER"
            );
        }
        return null;
    }

    private static String state(
        BindingHealthRecord binding,
        AirflowActual actual
    ) {
        if ("DEPLOYING".equals(binding.deploymentStatus())) {
            return "DEPLOYING";
        }
        if ("DISABLED".equals(binding.deploymentStatus())) {
            return "DISABLED";
        }
        if (!"OBSERVED".equals(actual.state())) return "UNKNOWN";
        return "DEGRADED";
    }

    private Access authorize(
        String tenantId,
        String actorId,
        UUID planId
    ) {
        String tenant = required(tenantId, "tenantId");
        String actor = required(actorId, "actorId");
        Set<DeliveryActorRole> current = duties.currentDuties();
        if (planId == null || current == null || current.isEmpty()) {
            throw forbidden();
        }
        boolean planWritable = planAccess.canMaintain(
            tenant,
            planId,
            actor
        );
        if (
            current.equals(Set.of(DeliveryActorRole.MODEL_MAINTAINER)) &&
            !planWritable
        ) {
            throw forbidden();
        }
        return new Access(
            tenant,
            actor,
            planId,
            current.contains(DeliveryActorRole.RELEASE_OPERATOR) &&
            planWritable
        );
    }

    private static PlanExecutionException forbidden() {
        return new PlanExecutionException(
            "MODEL_PLAN_EXECUTION_FORBIDDEN",
            "Warehouse plan is not available for this release duty",
            Kind.FORBIDDEN
        );
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new PlanExecutionException(
                "MODEL_PLAN_EXECUTION_REQUEST_INVALID",
                name + " is required",
                Kind.INVALID
            );
        }
        return text;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text)
            ? null
            : text;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean result) return result;
        String text = text(value);
        return text == null ? null : Boolean.valueOf(text);
    }

    private static Instant instant(Object value) {
        String text = text(value);
        if (text == null) return null;
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (RuntimeException invalid) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private record Access(
        String tenantId,
        String actorId,
        UUID planId,
        boolean operatorWithPlanAccess
    ) {}

    private record AirflowActual(
        String state,
        String schedule,
        String timezone,
        Boolean paused,
        Instant nextRunAt,
        DagRunActual latestDagRun
    ) {
        static AirflowActual unknown() {
            return new AirflowActual(
                "UNKNOWN",
                null,
                null,
                null,
                null,
                null
            );
        }

        static AirflowActual notRegistered() {
            return new AirflowActual(
                "NOT_REGISTERED",
                null,
                null,
                null,
                null,
                null
            );
        }
    }

    public record WorkspaceView(
        UUID planId,
        String state,
        List<BindingView> bindings
    ) {}

    public record BindingView(
        UUID id,
        int version,
        String environment,
        String state,
        String scheduleMode,
        String desiredSchedule,
        String desiredTimezone,
        String effectiveSchedule,
        String effectiveTimezone,
        String deploymentStatus,
        String desiredDeploymentChecksum,
        String deployedChecksum,
        String airflowDagId,
        String airflowState,
        String actualSchedule,
        String actualTimezone,
        Boolean airflowPaused,
        Instant nextRunAt,
        DagRunActual latestDagRun,
        OperationalRunView latestOperationalRun,
        RelationEvidenceView latestRelation,
        BlockerView primaryBlocker,
        List<String> allowedActions
    ) {}

    public record DagRunActual(
        String dagRunId,
        String state,
        Instant logicalDate,
        Instant startedAt,
        Instant finishedAt
    ) {}

    public record OperationalRunView(
        UUID pipelineRunGroupId,
        String airflowRunId,
        String triggerType,
        String status,
        String errorCode,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
    ) {}

    public record RelationEvidenceView(
        Boolean verified,
        Boolean exists,
        String errorCode,
        String physicalRelation,
        Instant observedAt
    ) {}

    public record BlockerView(
        String code,
        String message,
        String owner
    ) {}
}
