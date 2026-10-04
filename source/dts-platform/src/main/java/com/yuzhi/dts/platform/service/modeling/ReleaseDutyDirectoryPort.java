package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryActorRole;

/** Authoritative current-duty lookup used by asynchronous release commands. */
public interface ReleaseDutyDirectoryPort {
    boolean hasDuty(String actorId, DeliveryActorRole duty);
}
