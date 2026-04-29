package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.permission.DashboardShareService;
import com.yuzhi.dts.platform.service.permission.dto.AssetGrantDto;
import com.yuzhi.dts.platform.service.permission.dto.DashboardShareRequest;
import com.yuzhi.dts.platform.service.visualization.BiReportLinkService;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
@Transactional
public class ReportsResource {

    private static final String REPORT_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).DATA_MAINTAINER_ROLES)";

    private final BiReportLinkService reports;
    private final AuditService audit;
    private final DashboardShareService shareService;

    public ReportsResource(
        BiReportLinkService reports,
        AuditService audit,
        DashboardShareService shareService
    ) {
        this.reports = reports;
        this.audit = audit;
        this.shareService = shareService;
    }

    @GetMapping("/published")
    public ApiResponse<List<BiReportLinkDto>> published(
        @RequestParam(required = false) String deptCode,
        @RequestParam(required = false, name = "type") String reportType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID queryDatasetId,
        @RequestParam(required = false) String bizDomain,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<BiReportLinkDto> list = reports.listPublished(deptCode, reportType, keyword, activeDept, queryDatasetId, bizDomain);
        audit.audit("READ", "vis.reports.published", "size=" + list.size());
        return ApiResponses.ok(list);
    }

    @PostMapping("/visit")
    public ApiResponse<Map<String, Object>> visit(@RequestBody(required = false) Map<String, Object> body) {
        String id = text(body != null ? body.get("id") : null);
        String code = text(body != null ? body.get("code") : null);
        String title = text(body != null ? body.get("title") : null);
        String url = text(body != null ? body.get("url") : null);
        String engine = text(body != null ? body.get("engine") : null);
        String classification = text(body != null ? body.get("classification") : null);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        put(payload, "id", id);
        put(payload, "code", code);
        put(payload, "title", title);
        put(payload, "url", url);
        put(payload, "engine", engine);
        put(payload, "classification", classification);
        payload.put("ts", Instant.now().toString());
        UUID reportId = parseUuid(id);
        try {
            reports.touchVisit(reportId, code, title, url, engine, classification);
        } catch (Exception ignored) {}
        audit.recordAuxiliary("OPEN", "vis", "report", code != null ? code : (id != null ? id : "unknown"), payload);
        return ApiResponses.ok(Map.of("ok", true));
    }

    @GetMapping
    @PreAuthorize(REPORT_MAINTAINER_EXPRESSION)
    public ApiResponse<List<BiReportLinkDto>> listAll(
        @RequestParam(required = false) String deptCode,
        @RequestParam(required = false, name = "type") String reportType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "false") boolean enabledOnly,
        @RequestParam(required = false) UUID queryDatasetId,
        @RequestParam(required = false) String bizDomain,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<BiReportLinkDto> list = reports.listAll(deptCode, reportType, keyword, enabledOnly, activeDept, queryDatasetId, bizDomain);
        audit.audit("READ", "vis.reports.manage.list", "size=" + list.size());
        return ApiResponses.ok(list);
    }

    @PostMapping
    @PreAuthorize(REPORT_MAINTAINER_EXPRESSION)
    public ApiResponse<BiReportLinkDto> create(
        @Valid @RequestBody BiReportLinkRequest req,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        BiReportLinkDto dto = reports.create(req, activeDept);
        audit.audit("CREATE", "vis.reports.manage.create", dto.code());
        return ApiResponses.ok(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize(REPORT_MAINTAINER_EXPRESSION)
    public ApiResponse<BiReportLinkDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody BiReportLinkRequest req,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        BiReportLinkDto dto = reports.update(id, req, activeDept);
        audit.audit("UPDATE", "vis.reports.manage.update", dto.code());
        return ApiResponses.ok(dto);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize(REPORT_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> delete(@PathVariable UUID id) {
        reports.delete(id);
        audit.audit("DELETE", "vis.reports.manage.delete", id.toString());
        return ApiResponses.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}/purge")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<Map<String, Object>> purge(@PathVariable UUID id) {
        reports.purge(id);
        audit.audit("PURGE", "vis.reports.manage.purge", id.toString());
        return ApiResponses.ok(Map.of("ok", true));
    }

    // ------------------------------------------------------------------
    // 大屏共享 API
    // ------------------------------------------------------------------
    // 鉴权完全交给 DashboardShareService（→ DashboardAccessGuard）。
    // 这里只做 (a) 异常→HTTP 状态码映射；(b) 路径参数转发。
    // - GET    /api/reports/{id}/grants            列出当前所有 grant（仅 manager 可见）
    // - POST   /api/reports/{id}/grants            授予 VIEW / MANAGE，含越级共享
    // - DELETE /api/reports/{id}/grants/{grantId}  撤销

    @GetMapping("/{id}/grants")
    public ApiResponse<List<AssetGrantDto>> listGrants(@PathVariable UUID id) {
        try {
            List<AssetGrantDto> grants = shareService.listGrants(id);
            return ApiResponses.ok(grants);
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/{id}/grants")
    public ApiResponse<AssetGrantDto> shareGrant(
        @PathVariable UUID id,
        @RequestBody DashboardShareRequest request
    ) {
        try {
            return ApiResponses.ok(shareService.share(id, request));
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}/grants/{grantId}")
    public ApiResponse<Map<String, Object>> revokeGrant(
        @PathVariable UUID id,
        @PathVariable Long grantId
    ) {
        try {
            shareService.revoke(id, grantId);
            return ApiResponses.ok(Map.of("ok", true));
        } catch (AccessDeniedException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private String text(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void put(Map<String, Object> out, String key, String value) {
        if (StringUtils.hasText(value)) {
            out.put(key, value);
        }
    }
}
