package com.yuzhi.dts.platform.service.audit;

/**
 * Audit action constants for SQL IDE operations (Sprint-11 F4 T20).
 *
 * <p>These string constants are passed to {@link AuditService#audit} as the {@code action}
 * parameter, giving the audit trail a consistent vocabulary across all 13 plan-required events.
 */
public final class SqlIdeAuditActions {

    private SqlIdeAuditActions() {}

    // ---- Execution lifecycle ----
    public static final String SQL_EXECUTE_SUBMIT   = "SQL_EXECUTE_SUBMIT";
    public static final String SQL_EXECUTE_CANCEL   = "SQL_EXECUTE_CANCEL";
    public static final String SQL_EXECUTE_COMPLETE = "SQL_EXECUTE_COMPLETE";

    // ---- Result interactions ----
    public static final String SQL_RESULT_VIEW   = "SQL_RESULT_VIEW";
    public static final String SQL_RESULT_EXPORT = "SQL_RESULT_EXPORT";
    public static final String SQL_RESULT_COPY   = "SQL_RESULT_COPY";

    // ---- F5 future placeholders ----
    public static final String SQL_TEMP_VIEW_CREATE = "SQL_TEMP_VIEW_CREATE";
    public static final String SQL_SUBQUERY_EXECUTE = "SQL_SUBQUERY_EXECUTE";
    public static final String SQL_PLAN_VIEW        = "SQL_PLAN_VIEW";

    // ---- IDE workspace ----
    public static final String SQL_IDE_TAB_SAVE    = "SQL_IDE_TAB_SAVE";
    public static final String SAVED_QUERY_LOAD    = "SAVED_QUERY_LOAD";
    public static final String SQL_CATALOG_BROWSE  = "SQL_CATALOG_BROWSE";
    public static final String SQL_HISTORY_VIEW    = "SQL_HISTORY_VIEW";
}
