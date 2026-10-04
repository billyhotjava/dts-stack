package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PolicyDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;

public interface PhysicalPreviewPolicyPort {
    PolicyDecision resolve(ModelSpecView model, RelationEvidence evidence, AccessContext access, String actorId);
}
