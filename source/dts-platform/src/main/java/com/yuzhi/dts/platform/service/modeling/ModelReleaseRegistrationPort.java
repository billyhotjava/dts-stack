package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.UUID;

public interface ModelReleaseRegistrationPort {
    RegistrationResult register(
        RegistrationStep step,
        String tenantId,
        String actorId,
        UUID releaseEventId,
        ModelSpecView model,
        List<ArtifactView> artifacts,
        String previousExternalRef
    );

    record RegistrationResult(String externalRef) {}
}
