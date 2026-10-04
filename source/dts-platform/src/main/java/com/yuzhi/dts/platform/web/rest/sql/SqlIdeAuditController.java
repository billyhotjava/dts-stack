package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public record CopyAuditRequest(
        @NotBlank String executionId,
        @Min(0) int cellCount
    ) {}

    @PostMapping("/copy")
    public ApiResponse<Void> copy(@Valid @RequestBody CopyAuditRequest req) {
        try {
            java.util.UUID.fromString(req.executionId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid executionId");
        }
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("cells", req.cellCount());
        payload.put("executionId", req.executionId());
        auditService.auditAction(SqlIdeAuditActions.CODE_RESULT_COPY, AuditStage.SUCCESS, req.executionId(), payload);
        return ApiResponses.ok(null);
    }
}
