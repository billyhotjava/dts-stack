package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vis")
public class VisualizationResource {

    private final AuditService audit;
    private final ClassificationUtils classificationUtils;

    public VisualizationResource(AuditService audit, ClassificationUtils classificationUtils) {
        this.audit = audit;
        this.classificationUtils = classificationUtils;
    }

    @GetMapping("/dashboards")
    public ApiResponse<Map<String, Object>> dashboards() {
        List<Map<String, Object>> all = List.of(
            dashboard("biz-overview", "业务总览", "经营", "PUBLIC", "/superset/dashboard/1", 96, 0.98),
            dashboard("finance", "财务看板", "财务", "INTERNAL", "/superset/dashboard/2", 120, 0.95),
            dashboard("risk", "风险控制", "风险", "SECRET", "/superset/dashboard/3", 240, 0.92),
            dashboard("ceo", "CEO 驾驶舱", "经营", "CONFIDENTIAL", "/superset/dashboard/4", 60, 0.99)
        );

        List<Map<String, Object>> visible = all
            .stream()
            .filter(d -> classificationUtils.canAccess(String.valueOf(d.get("level"))))
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now().toString());
        payload.put("total", visible.size());
        payload.put("items", visible);
        audit.audit("READ", "vis.dashboards", "visible=" + visible.size());
        return ApiResponses.ok(payload);
    }

    @GetMapping("/cockpit/metrics")
    public ApiResponse<Map<String, Object>> cockpit() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put(
            "kpi",
            List.of(
                Map.of("name", "总收入", "value", 125_000, "unit", "万元"),
                Map.of("name", "成本", "value", 78_000, "unit", "万元"),
                Map.of("name", "利润", "value", 47_000, "unit", "万元")
            )
        );
        data.put(
            "trend",
            List.of(
                Map.of("month", "04", "value", 12.1),
                Map.of("month", "05", "value", 18.4),
                Map.of("month", "06", "value", 22.7),
                Map.of("month", "07", "value", 30.3),
                Map.of("month", "08", "value", 28.5),
                Map.of("month", "09", "value", 35.2)
            )
        );
        data.put("sources", List.of("TDS Hive", "ODS Kafka", "财务 DW"));
        audit.audit("READ", "vis.cockpit", "metrics");
        return ApiResponses.ok(data);
    }

    @GetMapping("/projects/summary")
    public ApiResponse<Map<String, Object>> projects() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("count", 12);
        data.put("active", 5);
        data.put("delayed", 2);
        data.put("health", List.of(
            Map.of("stage", "需求分析", "completed", 8, "total", 10),
            Map.of("stage", "开发实现", "completed", 5, "total", 9),
            Map.of("stage", "上线评估", "completed", 3, "total", 6)
        ));
        audit.audit("READ", "vis.projects", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/finance/summary")
    public ApiResponse<Map<String, Object>> finance() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("revenue", Map.of("value", 320_000, "unit", "万元", "growth", 0.12));
        data.put("cost", Map.of("value", 210_000, "unit", "万元", "growth", 0.08));
        data.put("profit", Map.of("value", 110_000, "unit", "万元", "growth", 0.18));
        data.put("topSegments", List.of(
            Map.of("name", "制造业", "value", 95_000),
            Map.of("name", "零售业", "value", 62_000),
            Map.of("name", "政务", "value", 44_000)
        ));
        audit.audit("READ", "vis.finance", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/supply/summary")
    public ApiResponse<Map<String, Object>> supply() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("onTimeRate", 0.96);
        data.put("leadTimeDays", 5.2);
        data.put("alerts", List.of(
            Map.of("supplier", "SZ-001", "severity", "medium", "message", "原材料到货提前 2 天"),
            Map.of("supplier", "WH-008", "severity", "high", "message", "运输延迟 3 天")
        ));
        audit.audit("READ", "vis.supply", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/hr/summary")
    public ApiResponse<Map<String, Object>> hr() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now().toString());
        data.put("headcount", 860);
        data.put("attrition", 0.12);
        data.put("hiring", Map.of("opened", 45, "filled", 18, "critical", 7));
        data.put(
            "skills",
            List.of(
                Map.of("name", "数据治理", "coverage", 0.74),
                Map.of("name", "AI 算法", "coverage", 0.58),
                Map.of("name", "业务分析", "coverage", 0.81)
            )
        );
        audit.audit("READ", "vis.hr", "summary");
        return ApiResponses.ok(data);
    }

    private Map<String, Object> dashboard(
        String code,
        String name,
        String theme,
        String level,
        String url,
        int refreshMinutes,
        double availability
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("theme", theme);
        map.put("level", level.toUpperCase(Locale.ROOT));
        map.put("url", url);
        map.put("refreshMinutes", refreshMinutes);
        map.put("availability", availability);
        map.put("lastUpdatedAt", Instant.now().minusSeconds(refreshMinutes * 60L).toString());
        return map;
    }
}
