package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.visualization.BiReportLinkService;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backward-compatible dashboard endpoints.
 *
 * New integration should use {@code /api/reports/*}. This controller keeps legacy paths used by older
 * front-end pages and avoids any file-based configuration.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class DashboardResource {

    private final BiReportLinkService reports;
    private final AuditService audit;

    public DashboardResource(BiReportLinkService reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    @GetMapping("/dashboards")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<BiReportLinkDto> list = reports.listPublished(null, null, null);
        List<Map<String, Object>> dashboards = list
            .stream()
            .filter(r -> r != null && "HETU".equalsIgnoreCase(String.valueOf(r.engine())))
            .map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", r.code());
                m.put("name", r.title());
                m.put("level", r.classification());
                m.put("url", r.url());
                m.put("engine", r.engine());
                return m;
            })
            .toList();

        audit.audit("READ", "dashboard.list", "visible=" + dashboards.size());
        return ApiResponses.ok(dashboards);
    }

    @PostMapping("/dashboards/visit")
    public ApiResponse<Map<String, Object>> visit(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (body != null) {
            payload.putAll(body);
        }
        payload.putIfAbsent("ts", Instant.now().toString());
        String code = body != null ? text(body.get("code")) : null;
        String url = body != null ? text(body.get("url")) : null;
        audit.recordAuxiliary("OPEN", "vis", "dashboard", code != null ? code : (url != null ? url : "unknown"), payload);
        return ApiResponses.ok(Map.of("ok", true));
    }

    private String text(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}

