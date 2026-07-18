package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.security.SecurityUtils;
import org.springframework.stereotype.Component;

/** Resolves the authenticated owner context used by warehouse-plan writes. */
@Component
public class WarehousePlanActorProvider {

    public WarehousePlanActor currentActor() {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        String ownerId = SecurityUtils.getCurrentUserId().orElse(login);
        return new WarehousePlanActor(ownerId, SecurityUtils.getCurrentUserDept().orElse(null));
    }

    public record WarehousePlanActor(String ownerId, String ownerDepartmentId) {}
}
