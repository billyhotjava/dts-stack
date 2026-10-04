package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.common.audit.AuditStage;
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
        try {
            TempViewDto v = service.createTempView(executionId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("executionId", executionId.toString());
            payload.put("viewName", v.viewName());
            payload.put("rowCount", v.rowCount());
            auditService.auditAction(SqlIdeAuditActions.CODE_TEMP_VIEW_CREATE, AuditStage.SUCCESS, executionId.toString(), payload);
            return ApiResponses.ok(v);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                SqlIdeAuditActions.CODE_TEMP_VIEW_CREATE,
                AuditStage.FAIL,
                executionId.toString(),
                Map.of("reason", String.valueOf(ex.getMessage()))
            );
            throw ex;
        }
    }

    @PostMapping("/{name}/query")
    public ApiResponse<Map<String, Object>> query(@PathVariable String name, @RequestBody SubqueryRequest req) {
        // Fix 5/6: SHA-256-based sqlHash for audit forensics; wrap to audit FAILED properly
        String sqlHash = sha256Prefix(req.sql());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", name);
        payload.put("sqlHash", sqlHash);
        try {
            Map<String, Object> result = service.executeOnView(name, req.sql());
            auditService.auditAction(SqlIdeAuditActions.CODE_SUBQUERY_EXECUTE, AuditStage.SUCCESS, name, payload);
            return ApiResponses.ok(result);
        } catch (RuntimeException ex) {
            payload.put("reason", String.valueOf(ex.getMessage()));
            auditService.auditAction(SqlIdeAuditActions.CODE_SUBQUERY_EXECUTE, AuditStage.FAIL, name, payload);
            throw ex;
        }
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        try {
            service.dropView(name);
            auditService.auditAction(
                SqlIdeAuditActions.CODE_TEMP_VIEW_DROP,
                AuditStage.SUCCESS,
                name,
                Map.of("viewName", name)
            );
            return ApiResponses.ok(null);
        } catch (RuntimeException ex) {
            auditService.auditAction(
                SqlIdeAuditActions.CODE_TEMP_VIEW_DROP,
                AuditStage.FAIL,
                name,
                Map.of("viewName", name, "reason", String.valueOf(ex.getMessage()))
            );
            throw ex;
        }
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
