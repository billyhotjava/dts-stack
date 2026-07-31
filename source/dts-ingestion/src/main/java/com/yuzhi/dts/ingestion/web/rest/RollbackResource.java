package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.domain.RollbackAuditLog;
import com.yuzhi.dts.ingestion.security.SecurityUtils;
import com.yuzhi.dts.ingestion.service.etl.rollback.DataRollbackService;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackAuditService;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackImpact;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackRequest;
import com.yuzhi.dts.ingestion.service.etl.rollback.RollbackResult;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ingestion/rollback")
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).INFRA_MAINTAINERS)")
public class RollbackResource {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackResource.class);

    private final DataRollbackService rollbackService;
    private final RollbackAuditService auditService;

    public RollbackResource(DataRollbackService rollbackService,
                            RollbackAuditService auditService) {
        this.rollbackService = rollbackService;
        this.auditService = auditService;
    }

    /**
     * POST /api/ingestion/rollback/analyze - Impact analysis (dryRun).
     */
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<RollbackImpact>> analyze(@RequestBody RollbackRequest request) {
        LOG.info("Rollback impact analysis: scope={}, level={}, taskId={}, dataSourceId={}",
            request.scope(), request.level(), request.taskId(), request.dataSourceId());
        RollbackImpact impact = rollbackService.analyze(request);
        return ResponseEntity.ok(ApiResponses.ok(impact));
    }

    /**
     * POST /api/ingestion/rollback/execute - Execute rollback.
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<RollbackResult>> execute(@RequestBody RollbackRequest request) {
        if (request == null || request.dryRun()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "ROLLBACK_EXECUTE_DRY_RUN_FORBIDDEN: dryRun 请求只能提交到 /analyze"
            );
        }
        String operator = resolveOperator();
        LOG.info("Rollback execute: scope={}, level={}, taskId={}, dataSourceId={}, operator={}",
            request.scope(), request.level(), request.taskId(), request.dataSourceId(), operator);
        RollbackResult result = rollbackService.execute(request, operator);
        return ResponseEntity.ok(ApiResponses.ok(result));
    }

    /**
     * GET /api/ingestion/rollback/audit-log - Query audit logs.
     */
    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<List<RollbackAuditLog>>> getAuditLog(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) UUID dataSourceId) {
        List<RollbackAuditLog> logs;
        if (taskId != null) {
            logs = auditService.findByTaskId(taskId);
        } else if (dataSourceId != null) {
            logs = auditService.findByDataSourceId(dataSourceId);
        } else {
            logs = List.of();
        }
        return ResponseEntity.ok(ApiResponses.ok(logs));
    }

    private String resolveOperator() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
