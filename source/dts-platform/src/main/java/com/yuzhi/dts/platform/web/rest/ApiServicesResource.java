package com.yuzhi.dts.platform.web.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services/apis")
public class ApiServicesResource {

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String method, @RequestParam(required = false) String status) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "svc-" + i);
            m.put("name", "用户查询接口-" + i);
            m.put("dataset", "user_view_v" + i);
            m.put("method", (i % 2 == 0) ? "GET" : "POST");
            m.put("path", "/openapi/users/" + i);
            m.put("classification", (i % 2 == 0) ? "内部" : "公开");
            m.put("qps", i * 3);
            m.put("qpsLimit", 100);
            m.put("dailyLimit", 10000);
            m.put("status", (i % 3 == 0) ? "OFFLINE" : "PUBLISHED");
            m.put("recentCalls", i * 120);
            m.put("sparkline", List.of(2, 4, 6, 3, 8, 5));
            out.add(m);
        }
        return ApiResponses.ok(out);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", "用户查询接口");
        m.put("dataset", "user_view");
        m.put("method", "GET");
        m.put("path", "/openapi/users");
        m.put("classification", "内部");
        m.put("qps", 10);
        m.put("qpsLimit", 100);
        m.put("dailyLimit", 10000);
        m.put("status", "PUBLISHED");
        m.put("recentCalls", 1200);
        m.put("sparkline", List.of(1, 3, 2, 5, 4, 6));
        m.put("input", List.of(field("username", "string", false, "用户名")));
        m.put("output", List.of(field("id", "string", false, "用户ID"), field("email", "string", true, "邮箱")));
        m.put("policy", Map.of("minLevel", "内部", "maskedColumns", List.of("email"), "rowFilter", "level in ('内部','公开')"));
        m.put("quotas", Map.of("qpsLimit", 100, "dailyLimit", 10000, "dailyRemaining", 8765));
        m.put("audit", Map.of("last24hCalls", 230, "maskedHits", 33, "denies", 2));
        return ApiResponses.ok(m);
    }

    @PostMapping("/{id}/try")
    public ApiResponse<Map<String, Object>> tryInvoke(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponses.ok(Map.of(
            "columns",
            List.of("id", "username", "email"),
            "maskedColumns",
            List.of("email"),
            "rows",
            List.of(Map.of("id", "1", "username", "alice", "email", "a***@example.com"), Map.of("id", "2", "username", "bob", "email", "b***@example.com")),
            "filteredRowCount",
            2,
            "policyHits",
            List.of("MASK:email")
        ));
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<Map<String, Object>> metrics(@PathVariable String id) {
        List<Map<String, Object>> series = new ArrayList<>();
        for (int i = 0; i < 24; i++) series.add(Map.of("timestamp", System.currentTimeMillis() - i * 3600_000L, "calls", i * 10, "qps", i));
        return ApiResponses.ok(Map.of(
            "series",
            series,
            "levelDistribution",
            List.of(Map.of("label", "公开", "value", 60), Map.of("label", "内部", "value", 40)),
            "recentCalls",
            List.of(Map.of("user", "alice", "level", "内部", "rowCount", 20, "policy", "MASK:email"))
        ));
    }

    private Map<String, Object> field(String name, String type, boolean masked, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("masked", masked);
        m.put("description", desc);
        return m;
    }
}
