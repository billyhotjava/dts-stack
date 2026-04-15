package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlPlanService;
import com.yuzhi.dts.platform.service.sql.dto.ExplainRequest;
import com.yuzhi.dts.platform.service.sql.dto.PlanResultDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for SQL EXPLAIN / query plan — Sprint-11 F5 T23.
 *
 * <p>POST /api/sql/v2/explain accepts an {@link ExplainRequest} and returns a unified
 * {@link PlanResultDto} tree regardless of the underlying engine.
 */
@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdePlanController {

    private final SqlPlanService planService;
    private final AuditService auditService;

    public SqlIdePlanController(SqlPlanService planService, AuditService auditService) {
        this.planService = planService;
        this.auditService = auditService;
    }

    @PostMapping("/explain")
    public ApiResponse<PlanResultDto> explain(@RequestBody ExplainRequest req) {
        PlanResultDto result = planService.explain(req);
        String operator = result.root() == null ? null : result.root().operator();
        boolean failed = "ExplainError".equals(operator) || "ParseError".equals(operator);
        String outcome = failed ? "FAILED" : "SUCCESS";
        String sqlHash = Integer.toHexString(req.sql() == null ? 0 : req.sql().hashCode());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", req.engine());
        payload.put("sqlHash", sqlHash);
        payload.put("actionCode", SqlIdeAuditActions.SQL_PLAN_VIEW);
        if (failed && result.rawText() != null) {
            payload.put("errorSnippet", result.rawText().substring(0, Math.min(200, result.rawText().length())));
        }
        auditService.record(
            "READ", "sql.ide.plan", "sql.explain",
            "sqlHash:" + sqlHash, outcome, payload
        );
        return ApiResponses.ok(result);
    }
}
