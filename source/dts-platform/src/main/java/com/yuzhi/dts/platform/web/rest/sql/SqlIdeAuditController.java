package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for client-side audit events that cannot be captured server-side
 * (e.g. clipboard copy).  Sprint-11 F4 T20.
 *
 * <p>POST /api/sql/v2/audit/copy  — frontend reports a result-grid clipboard-copy event.
 */
@RestController
@RequestMapping("/api/sql/v2/audit")
public class SqlIdeAuditController {

    private final AuditService auditService;

    public SqlIdeAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    public record CopyAuditRequest(String executionId, int cellCount) {}

    @PostMapping("/copy")
    public ApiResponse<Void> copy(@RequestBody CopyAuditRequest req) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit(
            SqlIdeAuditActions.SQL_RESULT_COPY,
            "executionId=" + req.executionId() + " cells=" + req.cellCount(),
            user
        );
        return ApiResponses.ok(null);
    }
}
