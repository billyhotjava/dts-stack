package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlSubqueryService;
import com.yuzhi.dts.platform.service.sql.dto.SubqueryRequest;
import com.yuzhi.dts.platform.service.sql.dto.TempViewDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
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
        Map<String, Object> result = service.executeOnView(name, req.sql());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("viewName", name);
        payload.put("sqlHash", Integer.toHexString(req.sql() == null ? 0 : req.sql().hashCode()));
        payload.put("actionCode", SqlIdeAuditActions.SQL_SUBQUERY_EXECUTE);
        auditService.record("EXECUTE", "sql.ide.subquery", "sql.temp_view",
            name, "SUCCESS", payload);
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Void> delete(@PathVariable String name) {
        service.dropView(name);
        return ApiResponses.ok(null);
    }
}
