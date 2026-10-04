package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.security.SecurityUtils;
import org.springframework.stereotype.Component;

/** Resolves the authenticated owner context used by warehouse-plan writes. */
@Component
public class WarehousePlanActorProvider {

    public WarehousePlanActor currentActor() {
        var identity = com.yuzhi.dts.platform.security.modeling.ModelingIdentity.current();
        return new WarehousePlanActor(identity.id(), identity.deptCode());
    }

    public record WarehousePlanActor(String ownerId, String ownerDepartmentId) {}
}
