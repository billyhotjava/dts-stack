package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;

public interface ModelLifecycleCompilerPort {
    List<ArtifactWrite> compile(String tenantId, ModelSpecView model);
}
