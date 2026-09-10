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
import org.springframework.util.StringUtils;

@Component
public class WarehousePlanAuthorizationGuard {

    private final AdminDirectoryGateway directoryGateway;

    public WarehousePlanAuthorizationGuard(AdminDirectoryGateway directoryGateway) {
        this.directoryGateway = directoryGateway;
    }

    public boolean canReadPlan(WarehousePlanHeader current, WarehousePlanActor actor) {
        return current != null && actor != null && StringUtils.hasText(actor.ownerId()) &&
            SecurityUtils.isAuthenticated() &&
            (SecurityUtils.getCurrentUserId().filter(actor.ownerId()::equals).isPresent() ||
             SecurityUtils.getCurrentUserLogin().filter(actor.ownerId()::equals).isPresent());
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
        if (!StringUtils.hasText(requestedOwnerId)) {
            return;
        }
        if (
            Objects.equals(current.ownerId(), requestedOwnerId.trim()) &&
            sameOptionalDepartment(current.ownerDepartmentId(), requestedOwnerDepartmentId)
        ) {
            return;
        }
        UserSummary target = directoryGateway
            .findUserByPrincipalKey(requestedOwnerId.trim())
            .orElseThrow(() ->
                new WarehousePlanException(
                    "WAREHOUSE_PLAN_OWNER_FORBIDDEN",
                    "The requested warehouse plan owner is not present in the directory",
                    null
                )
            );
        if (!sameDepartment(target.deptCode(), requestedOwnerDepartmentId)) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN",
                "The requested owner department does not match the directory",
                null
            );
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return;
        }
        if (!sameDepartment(target.deptCode(), actor.ownerDepartmentId())) {
            throw new WarehousePlanException(
                "WAREHOUSE_PLAN_OWNER_DEPARTMENT_FORBIDDEN",
                "Department maintainers can transfer plans only inside their authenticated department",
                null
            );
        }
    }

    private static boolean sameDepartment(String left, String right) {
        String canonicalLeft = DepartmentUtils.normalize(left);
        String canonicalRight = DepartmentUtils.normalize(right);
        return StringUtils.hasText(canonicalLeft) && canonicalLeft.equals(canonicalRight);
    }

    private static boolean sameOptionalDepartment(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        return sameDepartment(left, right);
    }
}
