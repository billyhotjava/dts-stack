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
    public static final String SQL_EXECUTE_SUBMIT   = "sql.ide.execute.submit";
    public static final String SQL_EXECUTE_CANCEL   = "sql.ide.execute.cancel";
    public static final String SQL_EXECUTE_COMPLETE = "sql.ide.execute.complete";

    // ---- Result interactions ----
    public static final String SQL_RESULT_VIEW   = "sql.ide.result.view";
    public static final String SQL_RESULT_EXPORT = "sql.ide.result.export";
    public static final String SQL_RESULT_COPY   = "sql.ide.result.copy";

    // ---- F5 future placeholders ----
    public static final String SQL_TEMP_VIEW_CREATE = "sql.ide.temp_view.create";
    public static final String SQL_SUBQUERY_EXECUTE = "sql.ide.subquery.execute";
    public static final String SQL_PLAN_VIEW        = "sql.ide.plan.view";

    // ---- IDE workspace ----
    public static final String SQL_IDE_TAB_SAVE    = "sql.ide.tab.save";
    public static final String SAVED_QUERY_LOAD    = "sql.workbench.saved-query.load";
    public static final String SQL_CATALOG_BROWSE  = "sql.ide.catalog.browse";
    public static final String SQL_HISTORY_VIEW    = "sql.ide.history";
}
