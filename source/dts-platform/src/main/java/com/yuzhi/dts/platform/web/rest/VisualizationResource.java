package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vis")
public class VisualizationResource {

    private final AuditService audit;

    public VisualizationResource(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/cockpit/metrics")
    public ApiResponse<Map<String, Object>> cockpit() {
        Map<String, Object> data = Map.of(
            "kpi",
            List.of(
                Map.of("name", "总收入", "value", 125_000),
                Map.of("name", "成本", "value", 78_000),
                Map.of("name", "利润", "value", 47_000)
            ),
            "trend",
            List.of(12, 18, 22, 30, 28, 35)
        );
        audit.audit("READ", "vis.cockpit", "metrics");
        return ApiResponses.ok(data);
    }

    @GetMapping("/projects/summary")
    public ApiResponse<Map<String, Object>> projects() {
        Map<String, Object> data = Map.of("count", 12, "active", 5, "delayed", 2);
        audit.audit("READ", "vis.projects", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/finance/summary")
    public ApiResponse<Map<String, Object>> finance() {
        Map<String, Object> data = Map.of("revenue", 320_000, "cost", 210_000, "profit", 110_000);
        audit.audit("READ", "vis.finance", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/supply/summary")
    public ApiResponse<Map<String, Object>> supply() {
        Map<String, Object> data = Map.of("onTimeRate", 0.96, "leadTimeDays", 5.2);
        audit.audit("READ", "vis.supply", "summary");
        return ApiResponses.ok(data);
    }

    @GetMapping("/hr/summary")
    public ApiResponse<Map<String, Object>> hr() {
        Map<String, Object> data = Map.of("headcount", 860, "attrition", 0.12);
        audit.audit("READ", "vis.hr", "summary");
        return ApiResponses.ok(data);
    }
}

