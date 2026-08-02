package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import java.util.UUID;

public interface PhysicalPreviewAccessPort {
    AccessContext authorize(String tenantId, String actorId, ModelSpecView model, PhysicalPreviewRequest request);
}
