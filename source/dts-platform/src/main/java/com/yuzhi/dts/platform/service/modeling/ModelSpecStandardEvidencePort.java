package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;

/** Resolves versioned field-standard references against their professional owner modules. */
public interface ModelSpecStandardEvidencePort {
    StandardEvidence evaluate(String tenantId, ModelSpecView modelSpec);

    enum StandardEvidence {
        CURRENT,
        STALE,
        UNKNOWN,
    }
}
