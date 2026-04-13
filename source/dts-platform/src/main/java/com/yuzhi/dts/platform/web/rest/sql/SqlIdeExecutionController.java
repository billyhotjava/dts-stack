package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.SqlResultStreamService;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for v2 chunked result access (Sprint-11 F4 T16).
 *
 * <p>GET /api/sql/v2/executions/{id}/meta  — column metadata + row/chunk counts
 * <p>GET /api/sql/v2/executions/{id}/page  — paginated rows from chunked storage
 */
@RestController
@RequestMapping("/api/sql/v2/executions")
public class SqlIdeExecutionController {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final SqlResultStreamService streamService;
    private final AuditService auditService;

    public SqlIdeExecutionController(SqlResultStreamService streamService, AuditService auditService) {
        this.streamService = streamService;
        this.auditService = auditService;
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<ResultMetaDto> getMeta(@PathVariable UUID id) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.audit("READ", "sql.ide.execution.meta", id.toString());
        ResultMetaDto meta = streamService.getMeta(id);
        return ApiResponses.ok(meta);
    }

    @GetMapping("/{id}/page")
    public ApiResponse<ResultPageDto> getPage(
        @PathVariable UUID id,
        @RequestParam(required = false, defaultValue = "0") long from,
        @RequestParam(required = false) Integer size
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        int pageSize = size != null ? size : DEFAULT_PAGE_SIZE;
        auditService.record(
            "READ",
            "sql.ide.execution.page",
            "sql.ide.execution.page",
            id.toString(),
            "SUCCESS",
            Map.of("from", from, "size", pageSize, "user", user)
        );
        ResultPageDto page = streamService.streamRange(id, from, pageSize);
        return ApiResponses.ok(page);
    }
}
