package com.yuzhi.dts.admin.service.audit;

public final class AuditRequestContext {
    private static final ThreadLocal<Boolean> DOMAIN_AUDITED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> HTTP_FALLBACK_REQUESTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AuditRequestContext() {}

    public static void clear() {
        DOMAIN_AUDITED.set(Boolean.FALSE);
        HTTP_FALLBACK_REQUESTED.set(Boolean.FALSE);
    }

    public static void markDomainAudit() {
        DOMAIN_AUDITED.set(Boolean.TRUE);
    }

    public static boolean wasDomainAudited() {
        Boolean v = DOMAIN_AUDITED.get();
        return Boolean.TRUE.equals(v);
    }

    public static void requestHttpFallback() {
        HTTP_FALLBACK_REQUESTED.set(Boolean.TRUE);
    }

    public static boolean consumeHttpFallbackRequest() {
        boolean requested = Boolean.TRUE.equals(HTTP_FALLBACK_REQUESTED.get());
        HTTP_FALLBACK_REQUESTED.set(Boolean.FALSE);
        return requested;
    }
}
