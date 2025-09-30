package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Data Service public APIs mapping to tasks: test/publish/execute.
 */
@RestController
@RequestMapping("/api/apis")
@Transactional
public class ApiGatewayResource {

    private final AuditService audit;

    public ApiGatewayResource(AuditService audit) {
        this.audit = audit;
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id, @RequestBody(required = false) Map<String, Object> input) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columns", List.of("id", "username", "email"));
        m.put("maskedColumns", List.of("email"));
        m.put("rows", List.of(
            Map.of("id", "1", "username", "alice", "email", "a***@example.com"),
            Map.of("id", "2", "username", "bob", "email", "b***@example.com")
        ));
        audit.audit("EXECUTE", "api.test", id);
        return ApiResponses.ok(m);
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String id, @RequestBody(required = false) Map<String, Object> input) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("version", input != null ? input.getOrDefault("version", "v1") : "v1");
        m.put("status", "PUBLISHED");
        m.put("basePath", "/openapi/" + id);
        audit.audit("PUBLISH", "api.publish", id);
        return ApiResponses.ok(m);
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String id, @RequestBody(required = false) Map<String, Object> input) {
        // Simulate auth, rate limit and audit
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestId", UUID.randomUUID().toString());
        m.put("status", 200);
        m.put("columns", List.of("id", "value"));
        m.put("rows", List.of(Map.of("id", "1", "value", "ok")));
        audit.audit("EXECUTE", "api.execute", id);
        return ApiResponses.ok(m);
    }
}

