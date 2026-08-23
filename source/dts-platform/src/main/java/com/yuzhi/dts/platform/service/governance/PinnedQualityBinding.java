package com.yuzhi.dts.platform.service.governance;

import java.util.UUID;

/** Exact governance rule snapshot selected by a model release candidate. */
public record PinnedQualityBinding(UUID ruleId, UUID ruleVersionId, UUID bindingId) {}
