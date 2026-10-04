package com.yuzhi.dts.copilot.analytics.web.support;

public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {}

    public static RequestContext current() {
        return CONTEXT.get();
    }

    public static void set(RequestContext context) {
        if (context == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(context);
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
