package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.audit.SqlIdeAuditActions;
import com.yuzhi.dts.platform.service.sql.SqlExecutionExportRateLimiter;
import com.yuzhi.dts.platform.service.sql.SqlResultStreamService;
import com.yuzhi.dts.platform.service.sql.dto.QueryLogDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
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
 *
 * <p>Audit codes are catalogued under {@code explore.sqlIde}; see
 * {@link SqlIdeAuditActions} for the canonical {@code SQL_IDE_*} identifiers.
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
        ResultMetaDto meta = streamService.getMeta(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", id.toString());
        if (meta != null) {
            payload.put("rowCount", meta.totalRows());
            payload.put("truncated", meta.truncated());
            if (meta.elapsedMs() != null) {
                payload.put("elapsedMs", meta.elapsedMs());
            }
        }
        auditService.auditAction(SqlIdeAuditActions.CODE_EXECUTION_META_VIEW, AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(meta);
    }

    @GetMapping("/{id}/page")
    public ApiResponse<ResultPageDto> getPage(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "200") int size
    ) {
        ResultPageDto pageDto = streamService.getPage(id, page, size);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", id.toString());
        payload.put("page", page);
        payload.put("size", size);
        if (pageDto != null) {
            payload.put("returnedRows", pageDto.rows() == null ? 0 : pageDto.rows().size());
        }
        auditService.auditAction(SqlIdeAuditActions.CODE_EXECUTION_PAGE_VIEW, AuditStage.SUCCESS, id.toString(), payload);
        return ApiResponses.ok(pageDto);
    }

    @GetMapping("/{id}/log")
    public ApiResponse<QueryLogDto> log(@PathVariable UUID id) {
        return ApiResponses.ok(streamService.getLog(id));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<StreamingResponseBody> export(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "csv") String format
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        if (!rateLimiter.tryAcquire(user)) {
            auditService.auditAction(
                SqlIdeAuditActions.CODE_RESULT_EXPORT,
                AuditStage.FAIL,
                id.toString(),
                Map.of("reason", "rate-limited", "format", format == null ? "" : format)
            );
            return ResponseEntity.status(429).build();
        }
        String fmt = format == null ? "csv" : format.toLowerCase();
        String contentType;
        String ext;
        switch (fmt) {
            case "csv" -> { contentType = "text/csv; charset=utf-8"; ext = "csv"; }
            case "json" -> { contentType = "application/json"; ext = "json"; }
            case "xlsx", "excel" -> { contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; ext = "xlsx"; }
            default -> {
                auditService.auditAction(
                    SqlIdeAuditActions.CODE_RESULT_EXPORT,
                    AuditStage.FAIL,
                    id.toString(),
                    Map.of("reason", "unsupported-format", "format", format)
                );
                return ResponseEntity.badRequest().build();
            }
        }
        StreamingResponseBody body = out -> {
            switch (fmt) {
                case "json" -> streamService.exportJson(id, out);
                case "xlsx", "excel" -> streamService.exportExcel(id, out);
                default -> streamService.exportCsv(id, out); // fmt already validated above
            }
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", id.toString());
        payload.put("format", ext);
        auditService.auditAction(SqlIdeAuditActions.CODE_RESULT_EXPORT, AuditStage.SUCCESS, id + ":" + ext, payload);
        return ResponseEntity
            .ok()
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=execution-" + id.toString().substring(0, 8) + "." + ext)
            .body(body);
    }
}
