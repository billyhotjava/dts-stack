package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;

/** Authorization port for maintaining a warehouse plan from the modeling boundary. */
public interface ModelSpecPlanWriteAccessPort extends ModelSpecWriteAccessPort {
    boolean canMaintain(String tenantId, UUID planId, String actorId);
}
