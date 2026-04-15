package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlSubqueryService;
import com.yuzhi.dts.platform.service.sql.dto.SubqueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2/temp-views")
public class SqlIdeSubqueryController {

    private final SqlSubqueryService service;
    private final AuditService auditService;

    public SqlIdeSubqueryController(SqlSubqueryService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    public ApiResponse<TempViewDto> create(@RequestParam("executionId") UUID executionId) {
        TempViewDto v = service.createTempView(executionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", v.viewName());
        payload.put("rowCount", v.rowCount());
        payload.put("actionCode", SqlIdeAuditActions.SQL_TEMP_VIEW_CREATE);
        auditService.record("CREATE", "sql.ide.temp_view", "sql.execution",
            executionId.toString(), "SUCCESS", payload);
        return ApiResponses.ok(v);
    }

    @PostMapping("/{name}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String name, @RequestBody SubqueryRequest req) {
        // Fix 5/6: SHA-256-based sqlHash for audit forensics; wrap to audit FAILED properly
        String sqlHash = sha256Prefix(req.sql());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", name);
        payload.put("sqlHash", sqlHash);
        payload.put("actionCode", SqlIdeAuditActions.SQL_SUBQUERY_EXECUTE);
        try {
            Map<String, Object> result = service.executeOnView(name, req.sql());
            auditService.record("EXECUTE", "sql.ide.subquery", "sql.temp_view",
                name, "SUCCESS", payload);
            return ApiResponses.ok(result);
        } catch (RuntimeException e) {
            payload.put("error", e.getMessage());
            auditService.record("EXECUTE", "sql.ide.subquery", "sql.temp_view",
                name, "FAILED", payload);
            throw e;
        }
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        // Fix 5: audit DELETE operation
        service.dropView(name);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", name);
        payload.put("actionCode", SqlIdeAuditActions.SQL_TEMP_VIEW_DROP);
        auditService.record("DELETE", "sql.ide.temp_view", "sql.temp_view",
            name, "SUCCESS", payload);
        return ApiResponses.ok(null);
    }

    /** Fix 6: SHA-256 first 8 bytes as hex — useful for audit forensics. */
    private String sha256Prefix(String sql) {
        if (sql == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sql.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(sql.hashCode());
        }
    }
}
