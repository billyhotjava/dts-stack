package com.yuzhi.dts.analytics.service.analysis;

import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public record AnalysisRequestContext(
    String department,
    String classification,
    String roles,
    String correlationId,
    String requestUri,
    String clientIp
) {
    public static final String SPECIALIZED_AUDIT_RECORDED_ATTRIBUTE = AnalysisRequestContext.class.getName() + ".specializedAuditRecorded";

    public static AnalysisRequestContext from(HttpServletRequest request) {
        PlatformContext platform = PlatformContext.from(request);
        return new AnalysisRequestContext(
            platform.dept(),
            platform.classification(),
            platform.roles(),
            firstHeader(request, "X-Correlation-Id", "X-Request-Id"),
            request == null ? null : request.getRequestURI(),
            clientIp(request)
        );
    }

    public static void markSpecializedAuditRecorded() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(SPECIALIZED_AUDIT_RECORDED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static boolean hasSpecializedAuditRecorded(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(SPECIALIZED_AUDIT_RECORDED_ATTRIBUTE));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = firstHeader(request, "X-Forwarded-For", "X-Real-IP");
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded : forwarded.substring(0, comma).trim();
        }
        return request == null ? null : request.getRemoteAddr();
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        if (request == null) return null;
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
