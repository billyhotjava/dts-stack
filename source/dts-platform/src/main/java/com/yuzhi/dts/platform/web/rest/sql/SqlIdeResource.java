package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.SqlIdeTabService;
import com.yuzhi.dts.platform.service.sql.dto.CreateTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.PatchTabRequest;
import com.yuzhi.dts.platform.service.sql.dto.SqlIdeTabDto;
import com.yuzhi.dts.platform.service.sql.dto.UpsertTabRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the SQL IDE v2 feature (Sprint-11 F1/T02).
 *
 * <p>Endpoints are always exposed — no backend flag-gating is applied here.
 * Frontend flag {@code WEBAPP_ENABLE_SQL_IDE_V2} (surfaced as
 * {@code window.__RUNTIME_CONFIG__.enableSqlIdeV2}) controls whether users
 * can navigate to SqlIdePage. This asymmetry is intentional and documented in
 * {@link com.yuzhi.dts.platform.config.SqlIdeFeatureProperties}.
 */
@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdeResource {

    private final SqlIdeTabService tabService;
    private final AuditService auditService;

    public SqlIdeResource(SqlIdeTabService tabService, AuditService auditService) {
        this.tabService = tabService;
        this.auditService = auditService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponses.ok(Map.of("status", "ok", "version", "v2-skeleton"));
    }

    @GetMapping("/tabs")
    public ApiResponse<List<SqlIdeTabDto>> listTabs() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit("READ", "sql.ide.tabs.list", user);
        return ApiResponses.ok(tabService.listByUser(user));
    }

    @PostMapping("/tabs")
    public ApiResponse<SqlIdeTabDto> createTab(@RequestBody CreateTabRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        SqlIdeTabDto created = tabService.create(user, req);
        auditService.audit("CREATE", "sql.ide.tab", created.id().toString());
        return ApiResponses.ok(created);
    }

    @PatchMapping("/tabs/{id}")
    public ApiResponse<SqlIdeTabDto> patchTab(@PathVariable UUID id, @RequestBody PatchTabRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        SqlIdeTabDto updated = tabService.patch(user, id, req);
        String hash = req.sqlText() == null ? "" : Integer.toHexString(req.sqlText().hashCode());
        auditService.audit("UPDATE", "sql.ide.tab", id + ":" + hash);
        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/tabs/{id}")
    public ApiResponse<Void> deleteTab(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        tabService.delete(user, id);
        auditService.audit("DELETE", "sql.ide.tab", id.toString());
        return ApiResponses.ok(null);
    }

    @PostMapping("/tabs/batch")
    public ApiResponse<List<SqlIdeTabDto>> batchUpsert(@RequestBody List<UpsertTabRequest> reqs) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        List<SqlIdeTabDto> out = tabService.batchUpsert(user, reqs);
        auditService.audit("UPDATE", "sql.ide.tabs.batch", String.valueOf(out.size()));
        return ApiResponses.ok(out);
    }
}
