package com.yuzhi.dts.platform.service.modeling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rollback switch for canonical ModelSpec writes. */
@Component
public record ModelSpecFeatureFlags(boolean canonicalWriteEnabled) {

    public ModelSpecFeatureFlags(
        @Value("${dts.modeling.canonical-write-enabled:true}") boolean canonicalWriteEnabled
    ) {
        this.canonicalWriteEnabled = canonicalWriteEnabled;
    }

    static ModelSpecFeatureFlags enabled() {
        return new ModelSpecFeatureFlags(true);
    }
}
