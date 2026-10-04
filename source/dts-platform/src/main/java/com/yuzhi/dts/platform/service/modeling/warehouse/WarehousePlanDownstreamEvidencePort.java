package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageEvidence;
import java.util.List;
import java.util.UUID;

/** Reads completion evidence from the professional owners of post-design stages. */
public interface WarehousePlanDownstreamEvidencePort {
    List<StageEvidence> read(String tenantId, UUID planId, List<ModelSpecView> models);

    static WarehousePlanDownstreamEvidencePort unavailable() {
        return (tenantId, planId, models) -> List.of();
    }
}
