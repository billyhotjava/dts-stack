package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.util.UUID;

/** Validates a submitted source token against the locked, live warehouse-plan source. */
public interface ModelSpecSourceValidationPort {
    boolean isCurrentBinding(String tenantId, UUID planId, String actorId, SourceRef sourceRef);

    /** Re-resolves immutable gate evidence with the persisted plan owner context, independent of the viewer. */
    boolean isCurrentBindingForGate(String tenantId, UUID planId, SourceRef sourceRef);
}
