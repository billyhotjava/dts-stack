package com.yuzhi.dts.platform.service.ops;

import java.util.List;
import java.util.UUID;

/** Tenant-bound warehouse-plan projection used by operations reporting. */
public interface WarehousePlanOperationsReadPort {
    List<WarehousePlanProjection> listPlans();

    record WarehousePlanProjection(UUID id, String name, String ownerDepartmentId, String lifecycleStatus) {}
}
