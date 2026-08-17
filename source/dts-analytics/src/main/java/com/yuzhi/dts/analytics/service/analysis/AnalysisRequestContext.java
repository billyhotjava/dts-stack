package com.yuzhi.dts.analytics.service.analysis;

import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;

public record AnalysisRequestContext(
    String department,
    String classification,
    String roles,
    String correlationId,
    String requestUri,
    String clientIp
) {
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
