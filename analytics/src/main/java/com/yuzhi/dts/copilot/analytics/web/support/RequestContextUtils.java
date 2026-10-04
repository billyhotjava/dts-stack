package com.yuzhi.dts.copilot.analytics.web.support;

import org.slf4j.MDC;

public final class RequestContextUtils {

    private RequestContextUtils() {}

    /**
     * Return the current request context, if established by filters.
     */
    public static RequestContext current() {
        return RequestContextHolder.current();
    }

    /**
     * Resolve the request ID, preferring the request context then falling back to MDC.
     */
    public static String resolveRequestId() {
        RequestContext context = RequestContextHolder.current();
        if (context != null && context.requestId() != null && !context.requestId().isBlank()) {
            return context.requestId();
        }
        return MDC.get("requestId");
    }
}
