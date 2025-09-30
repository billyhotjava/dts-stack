package com.yuzhi.dts.platform.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsResource {

    @GetMapping("/published")
    public ApiResponse<List<Map<String, Object>>> published(@RequestParam(required = false) String category) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("id", "r-1", "title", "部门数据看板", "url", "https://bi.example.com/dashboards/1", "category", "业务"));
        out.add(Map.of("id", "r-2", "title", "安全统计", "url", "https://bi.example.com/dashboards/2", "category", "安全"));
        return ApiResponses.ok(out);
    }
}
