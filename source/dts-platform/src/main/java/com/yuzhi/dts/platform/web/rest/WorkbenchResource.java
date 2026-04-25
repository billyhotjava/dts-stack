package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.workbench.WorkbenchAuditRateLimiter;
import com.yuzhi.dts.platform.service.workbench.WorkbenchLeaderOverviewService;
import com.yuzhi.dts.platform.service.workbench.WorkbenchService;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchResource {

    private final WorkbenchService workbenchService;
    private final WorkbenchLeaderOverviewService leaderOverviewService;
    private final AuditService auditService;
    private final WorkbenchAuditRateLimiter auditRateLimiter;

    public WorkbenchResource(
        WorkbenchService workbenchService,
        WorkbenchLeaderOverviewService leaderOverviewService,
        AuditService auditService,
        WorkbenchAuditRateLimiter auditRateLimiter
    ) {
        this.workbenchService = workbenchService;
        this.leaderOverviewService = leaderOverviewService;
        this.auditService = auditService;
        this.auditRateLimiter = auditRateLimiter;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        Map<String, Object> payload = workbenchService.overview(user);
        auditService.audit("READ", "workbench.overview", user);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/todos")
    public ApiResponse<List<Map<String, Object>>> todos(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> items = workbenchService.todoItems(activeDept);
        auditService.audit("READ", "workbench.todos", "count=" + items.size());
        return ApiResponses.ok(items);
    }

    @GetMapping("/leader-overview")
    public ApiResponse<LeaderOverviewResponse> leaderOverview(
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "deptCode", required = false) String deptCode,
        @RequestParam(value = "bizDomain", required = false) String bizDomain,
        @RequestParam(value = "timeRange", required = false, defaultValue = "MONTH") String timeRange
    ) {
        String user = SecurityUtils.getCurrentUserLogin().orElseThrow();
        List<String> roles = SecurityUtils.getCurrentUserAuthorities();
        String userDept = SecurityUtils.getCurrentUserDept().orElse(null);
        LeaderOverviewResponse payload = leaderOverviewService.build(
            user,
            roles,
            userDept,
            scope,
            deptCode,
            bizDomain,
            timeRange
        );
        auditService.audit(
            "READ",
            "workbench.leader-overview",
            "requested=" + scope + ",effective=" + payload.scope() + ",dept=" + payload.effectiveDeptCode()
        );
        return ApiResponses.ok(payload);
    }

    /**
     * Sprint-15 F6/T01 — Receive front-end client-side audit events (e.g.
     * WORKBENCH_OVERVIEW_VIEW, WORKBENCH_FILTER_CHANGE). Untrusted payloads
     * are coerced to string and truncated before being forwarded to the
     * shared {@link AuditService}, so we record evidence without introducing
     * a generic "audit anything" ingest endpoint.
     */
    @PostMapping("/audit")
    public ResponseEntity<?> recordClientAudit(@RequestBody(required = false) Map<String, Object> body) {
        String user = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        if (!auditRateLimiter.tryAcquire(user)) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "reason", "rate_limited"));
        }
        String event = body == null ? null : String.valueOf(body.get("event"));
        if (event == null || event.isBlank() || "null".equalsIgnoreCase(event)) {
            return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", false)));
        }
        // Whitelist: only accept our WORKBENCH_* prefix to keep the endpoint
        // from degrading into a generic audit sink.
        if (!event.startsWith("WORKBENCH_")) {
            return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", false)));
        }
        Object payload = body.get("payload");
        String payloadStr = payload == null ? "" : String.valueOf(payload);
        if (payloadStr.length() > 512) {
            payloadStr = payloadStr.substring(0, 512);
        }
        auditService.audit("READ", "workbench.client-event", event + (payloadStr.isEmpty() ? "" : (":" + payloadStr)));
        return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", true)));
    }
}
