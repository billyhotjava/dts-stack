package com.yuzhi.dts.platform.service.governance;

import java.util.UUID;

/** Immutable implementation identity, independent of a model's display name. */
public record IndicatorImplementationRef(UUID modelSpecId, int modelRevision, String fieldName) {
    public IndicatorImplementationRef {
        if (modelSpecId == null || modelRevision < 1 || fieldName == null ||
            !fieldName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("指标实现必须包含有效模型、修订与字段");
        }
    }
}
