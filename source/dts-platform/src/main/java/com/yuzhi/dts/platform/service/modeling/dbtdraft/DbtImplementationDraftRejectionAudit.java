package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Strict, body-free audit boundary for draft requests rejected before service dispatch. */
@Service
public class DbtImplementationDraftRejectionAudit {

    private static final Pattern ROUTE = Pattern.compile(
        "^/api/modeling/model-specs/([^/]+)/dbt-drafts(?:/([^/]+)/(files|validate|commit))?$"
    );

    private final DbtImplementationDraftAuditRecorder recorder;
    private final String tenantId;

    public DbtImplementationDraftRejectionAudit(
        DbtImplementationDraftAuditRecorder recorder,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenantId
    ) {
        this.recorder = recorder;
        this.tenantId = DbtImplementationDraftContract.requiredText(tenantId, "tenantId", 128);
    }

    public void recordBodyTooLarge(HttpServletRequest request, String correlationId, long observedBytes) {
        record(
            request,
            correlationId,
            "DBT_DRAFT_REQUEST_TOO_LARGE",
            "PAYLOAD_TOO_LARGE",
            Map.of("observedBytes", Math.max(0, observedBytes))
        );
    }

    public void recordValidationFailure(HttpServletRequest request, String correlationId, int violationCount) {
        record(
            request,
            correlationId,
            "DBT_DRAFT_REQUEST_INVALID",
            "BEAN_VALIDATION",
            Map.of("violationCount", Math.max(0, Math.min(violationCount, 10_000)))
        );
    }

    public void recordMalformedBody(HttpServletRequest request, String correlationId) {
        record(request, correlationId, "DBT_DRAFT_REQUEST_MALFORMED", "MALFORMED_JSON", Map.of());
    }

    private void record(
        HttpServletRequest request,
        String correlationId,
        String errorCode,
        String rejectionKind,
        Map<String, Object> boundedDetails
    ) {
        RouteAudit route = route(request);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", DbtImplementationDraftContract.requiredText(correlationId, "correlationId", 64));
        payload.put("tenantId", tenantId);
        payload.put("result", "FAILED");
        payload.put("errorCode", errorCode);
        payload.put("errorKind", rejectionKind);
        payload.put("operation", route.operation());
        payload.putAll(boundedDetails);
        recorder.recordFailure(route.actionCode(), route.resourceId(), Map.copyOf(payload));
    }

    private static RouteAudit route(HttpServletRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required for strict rejection audit");
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        Matcher route = path == null ? null : ROUTE.matcher(path);
        if (route == null || !route.matches()) {
            throw new IllegalArgumentException("Unsupported dbt draft rejection route");
        }
        String operation = route.group(3);
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (operation == null && "POST".equals(method)) {
            return new RouteAudit("MODELING_DBT_DRAFT_CREATE", "CREATE", safeUuid(route.group(1), "dbt-draft"));
        }
        if ("files".equals(operation) && "PUT".equals(method)) {
            return new RouteAudit("MODELING_DBT_DRAFT_SAVE", "SAVE", safeUuid(route.group(2), "dbt-draft"));
        }
        if ("validate".equals(operation) && "POST".equals(method)) {
            return new RouteAudit("MODELING_DBT_DRAFT_VALIDATE", "VALIDATE", safeUuid(route.group(2), "dbt-draft"));
        }
        if ("commit".equals(operation) && "POST".equals(method)) {
            return new RouteAudit("MODELING_DBT_DRAFT_COMMIT", "COMMIT", safeUuid(route.group(2), "dbt-draft"));
        }
        throw new IllegalArgumentException("Unsupported dbt draft rejection operation");
    }

    private static String safeUuid(String value, String fallback) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record RouteAudit(String actionCode, String operation, String resourceId) {}
}
