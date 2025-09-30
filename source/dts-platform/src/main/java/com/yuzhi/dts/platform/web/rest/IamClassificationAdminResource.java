package com.yuzhi.dts.platform.web.rest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/classification")
public class IamClassificationAdminResource {

    @GetMapping("/users/search")
    public ApiResponse<List<Map<String, Object>>> searchUsers(@RequestParam String keyword) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ThreadLocalRandom.current().nextInt(1000, 9999));
            m.put("username", keyword + i);
            m.put("displayName", keyword + " 用户" + i);
            m.put("orgPath", List.of("总公司", "部门" + i));
            m.put("roles", List.of("DATA_VIEWER"));
            m.put("projects", List.of("demo"));
            m.put("securityLevel", i % 2 == 0 ? "内部" : "公开");
            m.put("updatedAt", Instant.now().toString());
            out.add(m);
        }
        return ApiResponses.ok(out);
    }

    @PostMapping("/users/{id}/refresh")
    public ApiResponse<Map<String, Object>> refreshUser(@PathVariable long id) {
        return ApiResponses.ok(Map.of("id", id, "username", "user" + id, "displayName", "用户" + id, "orgPath", List.of("总公司"), "roles", List.of("DATA_VIEWER"), "projects", List.of("demo"), "securityLevel", "内部", "updatedAt", Instant.now().toString()));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<Map<String, Object>>> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            out.add(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "name",
                    "dataset_" + i,
                    "domain",
                    "domain" + (i % 3),
                    "owner",
                    "owner" + (i % 4),
                    "classification",
                    i % 2 == 0 ? "公开" : "内部"
                )
            );
        }
        return ApiResponses.ok(out);
    }

    @GetMapping("/sync/status")
    public ApiResponse<Map<String, Object>> syncStatus() {
        List<Map<String, Object>> failures = List.of(
            Map.of("id", "f1", "type", "USER", "target", "bob", "reason", "not found"),
            Map.of("id", "f2", "type", "DATASET", "target", "ds_x", "reason", "policy mismatch")
        );
        return ApiResponses.ok(Map.of("lastSyncAt", Instant.now().toString(), "deltaCount", 2, "failures", failures));
    }

    @PostMapping("/sync/execute")
    public ApiResponse<Map<String, Object>> runSync() { return syncStatus(); }

    @PostMapping("/sync/retry/{id}")
    public ApiResponse<Map<String, Object>> retry(@PathVariable String id) { return syncStatus(); }
}
