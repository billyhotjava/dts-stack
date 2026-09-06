package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository;
import com.yuzhi.dts.platform.repository.modeling.PlanExecutionBindingRepository.RepairResult;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;
import com.yuzhi.dts.platform.service.modeling.PlanExecutionException.Kind;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Operator-only commands for the server-owned plan execution binding. */
@Service
public class PlanExecutionBindingCommandService {

    private final PlanExecutionBindingRepository bindings;
    private final ModelSpecPlanWriteAccessPort planAccess;
    private final ReleaseDutyResolver duties;
    private final Clock clock;

    @Autowired
    public PlanExecutionBindingCommandService(
        PlanExecutionBindingRepository bindings,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties
    ) {
        this(
            bindings,
            planAccess,
            duties,
            Clock.systemUTC()
        );
    }

    PlanExecutionBindingCommandService(
        PlanExecutionBindingRepository bindings,
        ModelSpecPlanWriteAccessPort planAccess,
        ReleaseDutyResolver duties,
        Clock clock
    ) {
        this.bindings = Objects.requireNonNull(
            bindings,
            "bindings is required"
        );
        this.planAccess = Objects.requireNonNull(
            planAccess,
            "planAccess is required"
        );
        this.duties = Objects.requireNonNull(duties, "duties is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public RepairView repair(
        String tenantId,
        String actorId,
        UUID planId,
        UUID bindingId,
        int expectedVersion
    ) {
        String tenant = required(tenantId, "tenantId");
        String actor = required(actorId, "actorId");
        Set<DeliveryActorRole> current = duties.currentDuties();
        if (
            planId == null ||
            bindingId == null ||
            expectedVersion < 1 ||
            current == null ||
            !current.contains(DeliveryActorRole.RELEASE_OPERATOR) ||
            !planAccess.canMaintain(tenant, planId, actor)
        ) {
            throw new PlanExecutionException(
                "MODEL_PLAN_EXECUTION_REPAIR_FORBIDDEN",
                "Release operator duty and plan access are required",
                Kind.FORBIDDEN
            );
        }
        RepairResult repaired = bindings
            .requestRedeployment(
                tenant,
                planId,
                bindingId,
                expectedVersion,
                actor,
                clock.instant()
            )
            .orElseThrow(() ->
                new PlanExecutionException(
                    "MODEL_PLAN_EXECUTION_BINDING_CONFLICT",
                    "Execution binding changed or cannot be repaired",
                    Kind.CONFLICT
                )
            );
        return new RepairView(
            bindingId,
            repaired.bindingVersion(),
            "DEPLOYING"
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

    public record RepairView(
        UUID bindingId,
        int bindingVersion,
        String deploymentStatus
    ) {}
}
