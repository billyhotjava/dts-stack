package com.yuzhi.dts.admin.service.audit;

public final class AuditRequestContext {
    private static final ThreadLocal<Boolean> DOMAIN_AUDITED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AuditRequestContext() {}

    public static void clear() {
        DOMAIN_AUDITED.set(Boolean.FALSE);
    }

    public static void markDomainAudit() {
        DOMAIN_AUDITED.set(Boolean.TRUE);
    }

    public static boolean wasDomainAudited() {
        Boolean v = DOMAIN_AUDITED.get();
        return Boolean.TRUE.equals(v);
    }
}

