package com.yuzhi.dts.platform.service.audit;

/**
 * Audit action constants for SQL IDE operations (Sprint-11 F4 T20).
 *
 * <p>Two layers of constants are defined here:
 * <ul>
 *   <li><b>Legacy string codes</b> — loosely-typed strings still consumed by callers using
 *       {@link AuditService#audit(String, String, String)} and historic IT fixtures. Kept
 *       intact for backward compatibility.</li>
 *   <li><b>Action catalog codes</b> ({@code CODE_*}) — uppercase {@code SCREAMING_SNAKE_CASE}
 *       identifiers that match entries in {@code config/audit-action-catalog.json} under the
 *       {@code explore.sqlIde} entry. These are the codes that the central audit center renders
 *       and filters on, and must be passed to {@link AuditService#auditAction}.</li>
 * </ul>
 */
public final class SqlIdeAuditActions {

    private SqlIdeAuditActions() {}

    // ---- Execution lifecycle (legacy strings) ----
    public static final String SQL_EXECUTE_SUBMIT   = "sql.ide.execute.submit";
    public static final String SQL_EXECUTE_CANCEL   = "sql.ide.execute.cancel";
    public static final String SQL_EXECUTE_COMPLETE = "sql.ide.execute.complete";

    // ---- Result interactions (legacy strings) ----
    public static final String SQL_RESULT_VIEW   = "sql.ide.result.view";
    public static final String SQL_RESULT_EXPORT = "sql.ide.result.export";
    public static final String SQL_RESULT_COPY   = "sql.ide.result.copy";

    // ---- F5 future placeholders (legacy strings) ----
    public static final String SQL_TEMP_VIEW_CREATE = "sql.ide.temp_view.create";
    public static final String SQL_TEMP_VIEW_DROP   = "sql.ide.temp_view.drop";
    public static final String SQL_SUBQUERY_EXECUTE = "sql.ide.subquery.execute";
    public static final String SQL_PLAN_VIEW        = "sql.ide.plan.view";

    // ---- IDE workspace (legacy strings) ----
    public static final String SQL_IDE_TAB_SAVE    = "sql.ide.tab.save";
    public static final String SAVED_QUERY_LOAD    = "sql.workbench.saved-query.load";
    public static final String SQL_CATALOG_BROWSE  = "sql.ide.catalog.browse";
    public static final String SQL_HISTORY_VIEW    = "sql.ide.history";

    // ===== Action catalog codes (must match audit-action-catalog.json :: explore.sqlIde) =====
    public static final String CODE_TAB_LIST              = "SQL_IDE_TAB_LIST";
    public static final String CODE_TAB_CREATE            = "SQL_IDE_TAB_CREATE";
    public static final String CODE_TAB_UPDATE            = "SQL_IDE_TAB_UPDATE";
    public static final String CODE_TAB_DELETE            = "SQL_IDE_TAB_DELETE";
    public static final String CODE_TAB_BATCH_SAVE        = "SQL_IDE_TAB_BATCH_SAVE";
    public static final String CODE_CATALOG_BROWSE        = "SQL_IDE_CATALOG_BROWSE";
    public static final String CODE_CATALOG_SEARCH        = "SQL_IDE_CATALOG_SEARCH";
    public static final String CODE_EXECUTION_META_VIEW   = "SQL_IDE_EXECUTION_META_VIEW";
    public static final String CODE_EXECUTION_PAGE_VIEW   = "SQL_IDE_EXECUTION_PAGE_VIEW";
    public static final String CODE_RESULT_EXPORT         = "SQL_IDE_RESULT_EXPORT";
    public static final String CODE_RESULT_COPY           = "SQL_IDE_RESULT_COPY";
    public static final String CODE_PLAN_VIEW             = "SQL_IDE_PLAN_VIEW";
    public static final String CODE_TEMP_VIEW_CREATE      = "SQL_IDE_TEMP_VIEW_CREATE";
    public static final String CODE_TEMP_VIEW_DROP        = "SQL_IDE_TEMP_VIEW_DROP";
    public static final String CODE_SUBQUERY_EXECUTE      = "SQL_IDE_SUBQUERY_EXECUTE";
}
