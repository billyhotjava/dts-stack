package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort;
import com.yuzhi.dts.platform.service.ops.WarehousePlanOperationsReadPort.WarehousePlanProjection;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WarehousePlanOperationsReadAdapter implements WarehousePlanOperationsReadPort {

    private final WarehousePlanApplicationService plans;
    private final WarehousePlanActorProvider actorProvider;
    private final WarehousePlanAuthorizationGuard authorizationGuard;
    private final String serverTenantId;

    public WarehousePlanOperationsReadAdapter(
        WarehousePlanApplicationService plans,
        WarehousePlanActorProvider actorProvider,
        WarehousePlanAuthorizationGuard authorizationGuard,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.plans = plans;
        this.actorProvider = actorProvider;
        this.authorizationGuard = authorizationGuard;
        this.serverTenantId = serverTenantId;
    }

    @Override
    public List<WarehousePlanProjection> listPlans() {
        WarehousePlanActor actor = actorProvider.currentActor();
        return plans
            .list(serverTenantId, null)
            .stream()
            .filter(plan -> authorizationGuard.canReadPlan(plan, actor))
            .map(
                plan ->
                    new WarehousePlanProjection(
                        plan.id(),
                        plan.name(),
                        plan.ownerDepartmentId(),
                        plan.lifecycleStatus() == null ? null : plan.lifecycleStatus().name()
                    )
            )
            .toList();
    }
}
