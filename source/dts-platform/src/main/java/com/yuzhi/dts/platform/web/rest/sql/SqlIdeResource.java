package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlCatalogLazyService;
import com.yuzhi.dts.platform.service.sql.SqlIdeHistoryService;
import com.yuzhi.dts.platform.service.sql.SqlIdeTabService;
import com.yuzhi.dts.platform.service.sql.dto.CatalogColumnDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogDatasourceDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSchemaDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogSearchHitDto;
import com.yuzhi.dts.platform.service.sql.dto.CatalogTableDto;
import com.yuzhi.dts.platform.service.sql.dto.CreateTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.PatchTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.QueryHistoryItemDto;
import com.yuzhi.dts.platform.service.sql.dto.SqlIdeTabDto;
import com.yuzhi.dts.platform.service.sql.dto.UpsertTabRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the SQL IDE v2 feature (Sprint-11 F1/T02).
 *
 * <p>Endpoints are always exposed — no backend flag-gating is applied here.
 * Frontend flag {@code WEBAPP_ENABLE_SQL_IDE_V2} (surfaced as
 * {@code window.__RUNTIME_CONFIG__.enableSqlIdeV2}) controls whether users
 * can navigate to SqlIdePage. This asymmetry is intentional and documented in
 * {@link com.yuzhi.dts.platform.config.SqlIdeFeatureProperties}.
 *
 * <p>Audit notes (Sprint-11 follow-up): every endpoint records via
 * {@link AuditService#auditAction} using the canonical {@code SQL_IDE_*}
 * action codes registered in {@code config/audit-action-catalog.json}
 * (entry {@code explore.sqlIde}). Legacy {@code auditService.audit(...)} calls
 * remain only inside SqlIdeAuditController where backwards-compatible event
 * shape matters; new endpoints SHOULD use the catalog code path.
 */
@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdeResource {

    private final SqlIdeTabService tabService;
    private final AuditService auditService;
    private final SqlCatalogLazyService catalogService;
    private final SqlIdeHistoryService historyService;

    public SqlIdeResource(
        SqlIdeTabService tabService,
        AuditService auditService,
        SqlCatalogLazyService catalogService,
        SqlIdeHistoryService historyService
    ) {
        this.tabService = tabService;
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.historyService = historyService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponses.ok(Map.of("status", "ok", "version", "v2-skeleton"));
    }

    @GetMapping("/tabs")
    public ApiResponse<List<SqlIdeTabDto>> listTabs() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<SqlIdeTabDto> tabs = tabService.listByUser(user);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tabCount", tabs.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_TAB_LIST, AuditStage.SUCCESS, user, payload);
        return ApiResponses.ok(tabs);
    }

    @PostMapping("/tabs")
    public ApiResponse<SqlIdeTabDto> createTab(@RequestBody CreateTabRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        try {
            SqlIdeTabDto created = tabService.create(user, req);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tabId", created.id().toString());
            payload.put("title", created.title());
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_CREATE, AuditStage.SUCCESS, created.id().toString(), payload);
            return ApiResponses.ok(created);
        } catch (RuntimeException ex) {
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_CREATE, AuditStage.FAIL, null, Map.of("reason", String.valueOf(ex.getMessage())));
            throw ex;
        }
    }

    @PatchMapping("/tabs/{id}")
    public ApiResponse<SqlIdeTabDto> patchTab(@PathVariable UUID id, @RequestBody PatchTabRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        try {
            SqlIdeTabDto updated = tabService.patch(user, id, req);
            String hash = req.sqlText() == null ? "" : Integer.toHexString(req.sqlText().hashCode());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tabId", id.toString());
            payload.put("sqlHash", hash);
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_UPDATE, AuditStage.SUCCESS, id.toString(), payload);
            return ApiResponses.ok(updated);
        } catch (RuntimeException ex) {
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_UPDATE, AuditStage.FAIL, id.toString(), Map.of("reason", String.valueOf(ex.getMessage())));
            throw ex;
        }
    }

    @DeleteMapping("/tabs/{id}")
    public ApiResponse<Void> deleteTab(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        try {
            tabService.delete(user, id);
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_DELETE, AuditStage.SUCCESS, id.toString(), Map.of("tabId", id.toString()));
            return ApiResponses.ok(null);
        } catch (RuntimeException ex) {
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_DELETE, AuditStage.FAIL, id.toString(), Map.of("reason", String.valueOf(ex.getMessage())));
            throw ex;
        }
    }

    @PostMapping("/tabs/batch")
    public ApiResponse<List<SqlIdeTabDto>> batchUpsert(@RequestBody List<UpsertTabRequest> reqs) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        try {
            List<SqlIdeTabDto> out = tabService.batchUpsert(user, reqs);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestedCount", reqs == null ? 0 : reqs.size());
            payload.put("savedCount", out.size());
            auditService.auditAction(SqlIdeAuditActions.CODE_TAB_BATCH_SAVE, AuditStage.SUCCESS, String.valueOf(out.size()), payload);
            return ApiResponses.ok(out);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                SqlIdeAuditActions.CODE_TAB_BATCH_SAVE,
                AuditStage.FAIL,
                String.valueOf(reqs == null ? 0 : reqs.size()),
                Map.of("reason", String.valueOf(ex.getMessage()))
            );
            throw ex;
        }
    }

    @GetMapping("/history")
    public ApiResponse<List<QueryHistoryItemDto>> history(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "connection", required = false) String connection,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        return ApiResponses.ok(historyService.listForUser(user, status, connection, q, limit));
    }

    @GetMapping("/catalog/datasources")
    public ApiResponse<List<CatalogDatasourceDto>> catalogDatasources() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<CatalogDatasourceDto> data = catalogService.listDatasources();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "datasources");
        payload.put("count", data.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_CATALOG_BROWSE, AuditStage.SUCCESS, user, payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/catalog/{dsId}/schemas")
    public ApiResponse<List<CatalogSchemaDto>> catalogSchemas(@PathVariable String dsId) {
        List<CatalogSchemaDto> data = catalogService.listSchemas(dsId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "schemas");
        payload.put("datasourceId", dsId);
        payload.put("count", data.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_CATALOG_BROWSE, AuditStage.SUCCESS, dsId, payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/catalog/{dsId}/schemas/{schema}/tables")
    public ApiResponse<List<CatalogTableDto>> catalogTables(
        @PathVariable String dsId, @PathVariable String schema
    ) {
        List<CatalogTableDto> data = catalogService.listTables(dsId, schema);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "tables");
        payload.put("datasourceId", dsId);
        payload.put("schema", schema);
        payload.put("count", data.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_CATALOG_BROWSE, AuditStage.SUCCESS, dsId + "/" + schema, payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/catalog/{dsId}/tables/{schemaTable}/columns")
    public ApiResponse<List<CatalogColumnDto>> catalogColumns(
        @PathVariable String dsId, @PathVariable String schemaTable
    ) {
        List<CatalogColumnDto> data = catalogService.listColumns(dsId, schemaTable);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", "columns");
        payload.put("datasourceId", dsId);
        payload.put("schemaTable", schemaTable);
        payload.put("count", data.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_CATALOG_BROWSE, AuditStage.SUCCESS, dsId + "/" + schemaTable, payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/catalog/{dsId}/search")
    public ApiResponse<List<CatalogSearchHitDto>> catalogSearch(
        @PathVariable String dsId,
        @RequestParam("q") String q,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        List<CatalogSearchHitDto> data = catalogService.search(dsId, q, limit);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasourceId", dsId);
        payload.put("query", q);
        payload.put("limit", limit);
        payload.put("hits", data.size());
        auditService.auditAction(SqlIdeAuditActions.CODE_CATALOG_SEARCH, AuditStage.SUCCESS, dsId + ":" + q, payload);
        return ApiResponses.ok(data);
    }
}
