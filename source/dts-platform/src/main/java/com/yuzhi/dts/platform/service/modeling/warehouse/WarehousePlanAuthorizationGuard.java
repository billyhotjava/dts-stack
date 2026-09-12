package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway;
import com.yuzhi.dts.platform.service.admin.gateway.directory.AdminDirectoryGateway.UserSummary;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

@Component
public class WarehousePlanAuthorizationGuard {

    private final AdminDirectoryGateway directoryGateway;

    public WarehousePlanAuthorizationGuard(AdminDirectoryGateway directoryGateway) {
        this.directoryGateway = directoryGateway;
    }

    public boolean canReadPlan(WarehousePlanHeader current, WarehousePlanActor actor) {
        return current != null && actor != null &&
            com.yuzhi.dts.platform.security.modeling.ModelingIdentity.matchesActor(actor.ownerId()) &&
            com.yuzhi.dts.platform.security.modeling.ModelingIdentity.department(current.ownerDepartmentId());
    }

    public void requirePlanRead(WarehousePlanHeader current, WarehousePlanActor actor) {
        if (canReadPlan(current, actor)) {
            return;
        }
        throw new WarehousePlanException("WAREHOUSE_PLAN_NOT_FOUND", "Warehouse plan not found", null);
    }

    public void requirePlanMaintenance(WarehousePlanHeader current, WarehousePlanActor actor) {
        if (canReadPlan(current, actor)) return;
        throw new WarehousePlanException(
            "WAREHOUSE_PLAN_AUTHENTICATED_ACTOR_REQUIRED",
            "An authenticated actor is required for warehouse plan maintenance", null);
    }

    public void validateHeaderUpdate(
        WarehousePlanHeader current,
        String requestedOwnerId,
        String requestedOwnerDepartmentId,
        WarehousePlanActor actor
    ) {
        requirePlanMaintenance(current, actor);
        if (!Objects.equals(current.ownerDepartmentId(), requestedOwnerDepartmentId) ||
            (StringUtils.hasText(requestedOwnerId) && !Objects.equals(current.ownerId(), requestedOwnerId))) {
            throw new com.yuzhi.dts.platform.service.modeling.ModelSpecException("MODELING_CONTEXT_DEPARTMENT_IMMUTABLE",
                "部门公共层归属不可变更", com.yuzhi.dts.platform.service.modeling.ModelSpecException.Kind.CONFLICT);
        }
    }
}
