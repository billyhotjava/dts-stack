package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.sql.SqlExecutionExportRateLimiter;
import com.yuzhi.dts.platform.service.sql.SqlResultStreamService;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * REST endpoints for v2 chunked result access (Sprint-11 F4 T16).
 *
 * <p>GET /api/sql/v2/executions/{id}/meta  — column metadata + row/chunk counts
 * <p>GET /api/sql/v2/executions/{id}/page  — paginated rows from chunked storage
 */
@RestController
@RequestMapping("/api/sql/v2/executions")
public class SqlIdeExecutionController {

    private final SqlResultStreamService streamService;
    private final AuditService auditService;
    private final SqlExecutionExportRateLimiter rateLimiter;

    public SqlIdeExecutionController(
        SqlResultStreamService streamService,
        AuditService auditService,
        SqlExecutionExportRateLimiter rateLimiter
    ) {
        this.streamService = streamService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{id}/meta")
    public ApiResponse<ResultMetaDto> getMeta(@PathVariable UUID id) {
        auditService.audit("READ", "sql.ide.execution.meta", id.toString());
        ResultMetaDto meta = streamService.getMeta(id);
        return ApiResponses.ok(meta);
    }

    @GetMapping("/{id}/page")
    public ApiResponse<ResultPageDto> getPage(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        auditService.audit("READ", "sql.ide.execution.page", id + "?page=" + page + "&size=" + size);
        return ApiResponses.ok(streamService.getPage(id, page, size));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<StreamingResponseBody> export(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "csv") String format
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        if (!rateLimiter.tryAcquire(user)) {
            return ResponseEntity.status(429).build();
        }
        String fmt = format.toLowerCase();
        String contentType;
        String ext;
        switch (fmt) {
            case "json" -> { contentType = "application/json"; ext = "json"; }
            case "xlsx", "excel" -> { contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; ext = "xlsx"; }
            default -> { contentType = "text/csv; charset=utf-8"; ext = "csv"; }
        }
        StreamingResponseBody body = out -> {
            switch (fmt) {
                case "json" -> streamService.exportJson(id, out);
                case "xlsx", "excel" -> streamService.exportExcel(id, out);
                default -> streamService.exportCsv(id, out);
            }
        };
        auditService.audit("EXPORT", "sql.ide.execution.export", id + ":" + ext);
        return ResponseEntity
            .ok()
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=execution-" + id.toString().substring(0, 8) + "." + ext)
            .body(body);
    }
}
