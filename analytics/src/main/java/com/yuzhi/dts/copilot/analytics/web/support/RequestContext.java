package com.yuzhi.dts.copilot.analytics.web.support;

import java.util.Objects;

public record RequestContext(
        String requestId,
        String clientIp,
        String userAgent,
        String scheme,
        String host,
        String path,
        String locale,
        String timezone) {
    public RequestContext {
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
