package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.OpenedRun;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.OperationalScope;
import com.yuzhi.dts.platform.repository.modeling.PlanOperationalRunRepository.PreparedDispatch;
import com.yuzhi.dts.platform.service.etl.AirflowClient;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtDagService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.ScopedProjectException;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService.ScopedCandidateProject;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Opens durable OPERATIONAL_RUN rows before crossing the Airflow boundary.
 *
 * <p>Manual runs are platform-first. Scheduled runs are Airflow-first and call the pairwise
 * internal endpoint to atomically open the same durable run before dbt starts.
 */
@Service
public class PlanOperationalRunService {

    private final PlanOperationalRunRepository runs;
    private final DbtScopedProjectService scopedProjects;
    private final ModelRuntimeSpecTokenCodec tokens;
    private final AirflowClient airflow;
    private final ModelMaterializationProperties properties;
    private final DbtConfigService dbtConfig;
    private final DbtDagService dags;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver duties;
    private final Clock clock;

    @Autowired
    public PlanOperationalRunService(
        PlanOperationalRunRepository runs,
        DbtScopedProjectService scopedProjects,
        ModelRuntimeSpecTokenCodec tokens,
        AirflowClient airflow,
        ModelMaterializationProperties properties,
        DbtConfigService dbtConfig,
        DbtDagService dags,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties
    ) {
        this(
            runs,
            scopedProjects,
            tokens,
            airflow,
            properties,
            dbtConfig,
            dags,
            planAccess,
            duties,
            Clock.systemUTC()
        );
    }

    PlanOperationalRunService(
        PlanOperationalRunRepository runs,
        DbtScopedProjectService scopedProjects,
        ModelRuntimeSpecTokenCodec tokens,
        AirflowClient airflow,
        ModelMaterializationProperties properties,
        DbtConfigService dbtConfig,
        DbtDagService dags,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties,
        Clock clock
    ) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.scopedProjects = Objects.requireNonNull(
            scopedProjects,
            "scopedProjects is required"
        );
        this.tokens = Objects.requireNonNull(tokens, "tokens is required");
        this.airflow = Objects.requireNonNull(
            airflow,
            "airflow is required"
        );
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
        this.dbtConfig = Objects.requireNonNull(
            dbtConfig,
            "dbtConfig is required"
        );
        this.dags = Objects.requireNonNull(dags, "dags is required");
        this.planAccess = Objects.requireNonNull(
            planAccess,
            "planAccess is required"
        );
        this.duties = Objects.requireNonNull(duties, "duties is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public OperationalRunView openManual(
        String tenantId,
        String actorId,
        UUID planId,
        UUID bindingId,
        String idempotencyKey
    ) {
        requireOperator(tenantId, actorId, planId);
        String key = required(idempotencyKey, "Idempotency-Key");
        Target target = currentTarget();
        Instant now = clock.instant();
        String dagRunId =
            "dts_plan_" +
            bindingId.toString().replace("-", "") +
            "_" +
            digest(key).substring(0, 20);
        OpenedRun opened = runs.open(
            tenantId,
            planId,
            bindingId,
            dagRunId,
            "MANUAL",
            null,
            target.targetName(),
            null,
            now
        );
        if (
            opened.replayed() &&
            Set.of("SUBMITTED", "COMPLETED", "FAILED").contains(
                opened.status()
            )
        ) {
            return view(opened, opened.status());
        }
        Prepared prepared = prepare(opened, target, false, now);
        if (!"SUBMITTED".equals(opened.status())) {
            submitManual(opened, prepared, now);
        }
        return view(opened, "SUBMITTED");
    }

    public ScheduledOpenView openScheduled(
        UUID bindingId,
        String dagRunId,
        Instant logicalDate,
        String deploymentChecksum
    ) {
        if (bindingId == null || logicalDate == null) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_REQUEST_INVALID",
                "Scheduled run identity is required",
                Kind.INVALID
            );
        }
        Target target = currentTarget();
        String tenantId = runs.tenantForBinding(bindingId);
        Instant now = clock.instant();
        OpenedRun opened = runs.open(
            tenantId,
            null,
            bindingId,
            required(dagRunId, "dagRunId"),
            "CRON",
            logicalDate,
            target.targetName(),
            required(
                deploymentChecksum,
                "deploymentChecksum"
            ),
            now
        );
        if (
            opened.replayed() &&
            Set.of("COMPLETED", "FAILED").contains(opened.status())
        ) {
            throw failure(
                "MODEL_OPERATIONAL_CRON_RUN_TERMINAL",
                "Scheduled DagRun already reached a terminal platform state",
                Kind.CONFLICT
            );
        }
        try {
            Prepared prepared = prepare(opened, target, true, now);
            return new ScheduledOpenView(
                opened.pipelineRunGroupId(),
                opened.bindingId(),
                opened.bindingVersion(),
                "OPERATIONAL_RUN",
                "CRON",
                prepared.token(),
                prepared.projectBundleChecksum()
            );
        } catch (RuntimeException failed) {
            runs.finalizeFailed(
                opened.pipelineRunGroupId(),
                "MODEL_OPERATIONAL_CRON_PREPARE_FAILED",
                now
            );
            throw failed;
        }
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.plan-run-reconcile-delay-ms:5000}"
    )
    public int reconcilePendingManualRuns() {
        int recovered = 0;
        for (int index = 0; index < 20; index++) {
            Optional<OpenedRun> claimed = runs.claimRecoverableManual(
                clock.instant()
            );
            if (claimed.isEmpty()) break;
            reconcileManual(claimed.orElseThrow());
            recovered++;
        }
        return recovered;
    }

    private void reconcileManual(OpenedRun opened) {
        Instant now = clock.instant();
        try {
            if (!"PENDING".equals(opened.status())) {
                Optional<Map<String, Object>> actual =
                    airflow.getDagRun(
                        opened.airflowDagId(),
                        opened.airflowRunId()
                    );
                if (actual.isPresent()) {
                    String state = String.valueOf(
                        actual.orElseThrow().get("state")
                    )
                        .trim()
                        .toLowerCase();
                    if (
                        Set.of(
                            "failed",
                            "upstream_failed"
                        ).contains(state)
                    ) {
                        runs.finalizeFailed(
                            opened.pipelineRunGroupId(),
                            "MODEL_OPERATIONAL_AIRFLOW_RUN_FAILED",
                            now
                        );
                        return;
                    }
                    if (
                        "success".equals(state) &&
                        "SUBMITTED".equals(opened.status())
                    ) {
                        runs.finalizeFailed(
                            opened.pipelineRunGroupId(),
                            "MODEL_OPERATIONAL_FINALIZE_CALLBACK_MISSING",
                            now
                        );
                        return;
                    }
                    runs.markSubmitted(
                        opened.pipelineRunGroupId(),
                        now
                    );
                    return;
                }
                if ("SUBMITTED".equals(opened.status())) {
                    runs.markUnknown(
                        opened.pipelineRunGroupId(),
                        "MODEL_OPERATIONAL_AIRFLOW_RUN_MISSING",
                        now
                    );
                    return;
                }
            }
            Target target = currentTarget();
            Prepared prepared = prepare(
                opened,
                target,
                false,
                now
            );
            submitManual(opened, prepared, now);
        } catch (RuntimeException unavailable) {
            if (unavailable instanceof PlanExecutionException execution) {
                if ("MODEL_OPERATIONAL_AIRFLOW_SUBMISSION_UNKNOWN".equals(execution.code())) return;
                runs.markUnknown(opened.pipelineRunGroupId(), execution.code(), now);
                return;
            }
            if (unavailable instanceof ScopedProjectException scoped) {
                runs.markUnknown(opened.pipelineRunGroupId(), scoped.code(), now);
                return;
            }
            runs.markUnknown(opened.pipelineRunGroupId(), "MODEL_OPERATIONAL_DISPATCH_RECOVERY_FAILED", now);
        }
    }

    private Prepared prepare(
        OpenedRun opened,
        Target target,
        boolean airflowAlreadyOpened,
        Instant now
    ) {
        OperationalScope scope = runs.loadScope(
            opened.pipelineRunGroupId()
        );
        if (
            !target
                .executionTargetKey()
                .equals(scope.executionTargetKey())
        ) {
            runs.markUnknown(
                opened.pipelineRunGroupId(),
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                now
            );
            throw failure(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Plan execution target is not the current server target",
                Kind.CONFLICT
            );
        }
        Optional<PreparedDispatch> existing = runs.findPrepared(
            opened.pipelineRunGroupId()
        );
        if (existing.isPresent()) {
            PreparedDispatch prepared = existing.orElseThrow();
            ModelRuntimeSpecTokenCodec.IssuedToken restored =
                tokens.restore(
                    prepared.groupId(),
                    prepared.runtimeTokenExpiresAt()
                );
            if (
                !restored.digest().equals(
                    prepared.runtimeTokenDigest()
                )
            ) {
                throw failure(
                    "MODEL_RUNTIME_SPEC_TOKEN_INVALID",
                    "Operational runtime token is unavailable",
                    Kind.CONFLICT
                );
            }
            if (!restored.expiresAt().isAfter(now)) {
                ModelRuntimeSpecTokenCodec.IssuedToken refreshed =
                    tokens.issue(opened.pipelineRunGroupId(), now);
                if (
                    !runs.refreshRuntimeToken(
                        opened.pipelineRunGroupId(),
                        refreshed.digest(),
                        refreshed.expiresAt(),
                        now
                    )
                ) {
                    throw failure(
                        "MODEL_RUNTIME_SPEC_TOKEN_REFRESH_CONFLICT",
                        "Operational runtime token could not be refreshed",
                        Kind.CONFLICT
                    );
                }
                return new Prepared(
                    prepared.projectBundleChecksum(),
                    refreshed.token()
                );
            }
            return new Prepared(
                prepared.projectBundleChecksum(),
                restored.token()
            );
        }
        ScopedCandidateProject project =
            scopedProjects.prepareCandidate(scope.entries());
        ModelRuntimeSpecTokenCodec.IssuedToken issued =
            tokens.issue(opened.pipelineRunGroupId(), now);
        if (
            !runs.attachPreparedRuntime(
                opened.pipelineRunGroupId(),
                project.bundleChecksum(),
                issued.digest(),
                issued.expiresAt(),
                airflowAlreadyOpened,
                now
            )
        ) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_PREPARE_CONFLICT",
                "Operational runtime could not be prepared",
                Kind.CONFLICT
            );
        }
        return new Prepared(project.bundleChecksum(), issued.token());
    }

    private void submitManual(
        OpenedRun opened,
        Prepared prepared,
        Instant now
    ) {
        Map<String, Object> conf = Map.of(
            "pipelineRunGroupId",
            opened.pipelineRunGroupId().toString(),
            "bindingId",
            opened.bindingId().toString(),
            "bindingVersion",
            opened.bindingVersion(),
            "runPurpose",
            "OPERATIONAL_RUN",
            "triggerType",
            "MANUAL",
            "runtimeSpecToken",
            prepared.token(),
            "bundleChecksum",
            prepared.projectBundleChecksum()
        );
        try {
            dags.ensureReleaseBuildDag(opened.airflowDagId());
            if (
                airflow
                    .getDagRun(
                        opened.airflowDagId(),
                        opened.airflowRunId()
                    )
                    .isEmpty()
            ) {
                Optional<Map<String, Object>> response =
                    airflow.triggerDag(
                        opened.airflowDagId(),
                        Map.of(
                            "dag_run_id",
                            opened.airflowRunId(),
                            "conf",
                            conf
                        )
                    );
                if (response.isEmpty()) {
                    throw new IllegalStateException(
                        "Airflow did not accept the DagRun"
                    );
                }
            }
            runs.markSubmitted(opened.pipelineRunGroupId(), now);
        } catch (RuntimeException unavailable) {
            try {
                if (
                    airflow
                        .getDagRun(
                            opened.airflowDagId(),
                            opened.airflowRunId()
                        )
                        .isPresent()
                ) {
                    runs.markSubmitted(
                        opened.pipelineRunGroupId(),
                        now
                    );
                    return;
                }
            } catch (RuntimeException ignored) {
                // Keep UNKNOWN; the deterministic DagRun id is reconciled later.
            }
            runs.markUnknown(
                opened.pipelineRunGroupId(),
                "MODEL_OPERATIONAL_AIRFLOW_SUBMISSION_UNKNOWN",
                now
            );
            throw failure(
                "MODEL_OPERATIONAL_AIRFLOW_SUBMISSION_UNKNOWN",
                "Airflow submission outcome is unknown",
                Kind.UNAVAILABLE
            );
        }
    }

    private Target currentTarget() {
        String executionTargetKey = required(
            properties.getExecutionTargetKey(),
            "execution target key"
        );
        DbtConfigService.DbtWorkspaceConfig config;
        try {
            config = dbtConfig.loadRuntimeConfig();
        } catch (RuntimeException unavailable) {
            throw failure(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Server execution target is unavailable",
                Kind.UNAVAILABLE
            );
        }
        if (config == null) {
            throw failure(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Server execution target is unavailable",
                Kind.UNAVAILABLE
            );
        }
        return new Target(
            executionTargetKey,
            required(config.targetName(), "targetName")
        );
    }

    private void requireOperator(
        String tenantId,
        String actorId,
        UUID planId
    ) {
        Set<DeliveryActorRole> current = duties.currentDuties();
        if (
            current == null ||
            !current.contains(DeliveryActorRole.RELEASE_OPERATOR) ||
            planId == null ||
            !planAccess.canMaintain(
                required(tenantId, "tenantId"),
                planId,
                required(actorId, "actorId")
            )
        ) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_FORBIDDEN",
                "Release operator duty and plan access are required",
                Kind.FORBIDDEN
            );
        }
    }

    private static OperationalRunView view(
        OpenedRun opened,
        String status
    ) {
        return new OperationalRunView(
            opened.pipelineRunGroupId(),
            opened.bindingId(),
            opened.bindingVersion(),
            opened.triggerType(),
            opened.airflowDagId(),
            opened.airflowRunId(),
            status,
            opened.replayed()
        );
    }

    private static String required(String value, String name) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > 256) {
            throw failure(
                "MODEL_OPERATIONAL_RUN_REQUEST_INVALID",
                name + " is required",
                Kind.INVALID
            );
        }
        return text;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (Exception impossible) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                impossible
            );
        }
    }

    private static PlanExecutionException failure(
        String code,
        String message,
        Kind kind
    ) {
        return new PlanExecutionException(code, message, kind);
    }

    private record Target(String executionTargetKey, String targetName) {}

    private record Prepared(
        String projectBundleChecksum,
        String token
    ) {}

    public record OperationalRunView(
        UUID pipelineRunGroupId,
        UUID bindingId,
        int bindingVersion,
        String triggerType,
        String airflowDagId,
        String airflowRunId,
        String status,
        boolean replayed
    ) {}

    public record ScheduledOpenView(
        UUID pipelineRunGroupId,
        UUID bindingId,
        int bindingVersion,
        String runPurpose,
        String triggerType,
        String runtimeSpecToken,
        String bundleChecksum
    ) {}
}
