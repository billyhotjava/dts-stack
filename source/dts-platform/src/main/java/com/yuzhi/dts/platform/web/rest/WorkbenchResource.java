package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.workbench.WorkbenchAuditRateLimiter;
import com.yuzhi.dts.platform.service.workbench.WorkbenchLeaderOverviewService;
import com.yuzhi.dts.platform.service.workbench.WorkbenchService;
import com.yuzhi.dts.platform.service.workbench.dto.LeaderOverviewResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchResource {

    private static final Logger log = LoggerFactory.getLogger(WorkbenchResource.class);

    /** Defense-in-depth allowlist for any user-supplied deptCode that could reach a SQL LIKE clause. */
    private static final Pattern DEPT_CODE_ALLOWLIST = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Whitelist of audit event prefixes accepted by the client-event sink. */
    private static final String CLIENT_EVENT_PREFIX = "WORKBENCH_";

    /** Audit value sanitizer: strip CR/LF/control chars that would let a payload forge log lines. */
    private static final Pattern AUDIT_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");

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
        // P0-1: server-side actor binding. Non-institute-privileged users
        // cannot peek other departments by spoofing the X-Active-Dept header
        // — we ignore the header and force the user's own dept_code claim.
        String effectiveDept = resolveEffectiveActiveDept(activeDept);
        List<Map<String, Object>> items = workbenchService.todoItems(effectiveDept);
        auditService.audit(
            "READ",
            "workbench.todos",
            buildAuditDetail(Map.of(
                "count", String.valueOf(items.size()),
                "effectiveDept", effectiveDept == null ? "" : effectiveDept
            ))
        );
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
            buildAuditDetail(Map.of(
                "requested", normalizeScope(scope),
                "effective", payload.scope() == null ? "" : payload.scope(),
                "dept", payload.effectiveDeptCode() == null ? "" : payload.effectiveDeptCode()
            ))
        );
        return ApiResponses.ok(payload);
    }

    /**
     * Honours the X-Active-Dept header only for institute-privileged callers
     * (INST_LEADER / SUPER_ADMIN). For everyone else we replace the header
     * with the user's authoritative dept_code claim so a malicious header
     * cannot leak other departments' data through the WorkbenchService query
     * path. Wildcard / oversized values are silently dropped.
     */
    private String resolveEffectiveActiveDept(String requested) {
        boolean privileged =
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES) ||
            SecurityUtils.isOpAdminAccount();
        String userDept = SecurityUtils.getCurrentUserDept().orElse(null);
        if (!privileged) {
            return trimAndAllowlist(userDept);
        }
        String allowlisted = trimAndAllowlist(requested);
        return allowlisted != null ? allowlisted : trimAndAllowlist(userDept);
    }

    private static String trimAndAllowlist(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        return DEPT_CODE_ALLOWLIST.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String normalizeScope(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.trim().toUpperCase(Locale.ROOT);
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
        String event = body == null ? null : sanitizeAuditValue(String.valueOf(body.get("event")));
        if (event == null || event.isBlank() || "null".equalsIgnoreCase(event)) {
            return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", false)));
        }
        // Whitelist: only accept our WORKBENCH_* prefix to keep the endpoint
        // from degrading into a generic audit sink.
        if (!event.startsWith(CLIENT_EVENT_PREFIX)) {
            return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", false)));
        }
        // Only retain a small set of expected payload keys; reject unknown
        // free-form text so the audit table cannot be used to plant PII or
        // forged structured records.
        Map<String, String> safe = new LinkedHashMap<>();
        Object payload = body.get("payload");
        if (payload instanceof Map<?, ?> raw) {
            for (String key : List.of("scope", "timeRange", "deptCode", "bizDomain", "domain", "role")) {
                Object value = raw.get(key);
                if (value == null) continue;
                String s = sanitizeAuditValue(String.valueOf(value));
                if (s == null || s.isBlank()) continue;
                if (s.length() > 64) s = s.substring(0, 64);
                safe.put(key, s);
            }
        }
        auditService.audit(
            "READ",
            "workbench.client-event",
            buildAuditDetail(prepend("event", event, safe))
        );
        return ResponseEntity.ok(ApiResponses.ok(Map.of("ok", true)));
    }

    /**
     * Strips ASCII control characters (CR, LF, tab, etc.) so attacker-controlled
     * audit values cannot break out of one log line into a forged second line.
     * Returns null for null inputs.
     */
    private static String sanitizeAuditValue(String raw) {
        if (raw == null) return null;
        return AUDIT_CONTROL_CHARS.matcher(raw).replaceAll(" ").trim();
    }

    private static Map<String, String> prepend(String key, String value, Map<String, String> tail) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(key, value);
        out.putAll(tail);
        return out;
    }

    /**
     * Build a structured audit detail string with sanitized values. Always escapes
     * commas in values (so the {@code key=value,key=value} envelope is unambiguous).
     */
    private static String buildAuditDetail(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=');
            String v = sanitizeAuditValue(e.getValue());
            if (v == null) v = "";
            // Escape commas inside the value so the envelope stays parseable.
            sb.append(v.replace(",", "\\,"));
        }
        return sb.toString();
    }
}
