package com.yuzhi.dts.platform.service.modeling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Independent rollback switches for the canonical ModelSpec read and write paths. */
@Component
public record ModelSpecFeatureFlags(boolean canonicalReadEnabled, boolean canonicalWriteEnabled) {

    public ModelSpecFeatureFlags(
        @Value("${dts.modeling.canonical-read-enabled:true}") boolean canonicalReadEnabled,
        @Value("${dts.modeling.canonical-write-enabled:true}") boolean canonicalWriteEnabled
    ) {
        this.canonicalReadEnabled = canonicalReadEnabled;
        this.canonicalWriteEnabled = canonicalWriteEnabled;
    }

    static ModelSpecFeatureFlags enabled() {
        return new ModelSpecFeatureFlags(true, true);
    }
}
