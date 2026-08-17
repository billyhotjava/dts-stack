package com.yuzhi.dts.analytics.web.interceptor;

import com.yuzhi.dts.analytics.service.observability.BiObservabilityMetrics;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class BiObservabilityInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(BiObservabilityInterceptor.class);
    private static final String START_NANOS = BiObservabilityInterceptor.class.getName() + ".startNanos";
    private final BiObservabilityMetrics metrics;

    public BiObservabilityInterceptor(BiObservabilityMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception failure
    ) {
        String path = request.getRequestURI();
        String surface = surface(path);
        String operation = request.getMethod().toUpperCase();
        int status = response.getStatus();
        Object started = request.getAttribute(START_NANOS);
        long duration = started instanceof Long value ? System.nanoTime() - value : 0L;
        metrics.recordHttp(surface, operation, status, duration);
        if (surface.startsWith("legacy_")) metrics.recordLegacyCall(surface, operation);
        if (isPublish(path)) {
            metrics.recordPublication(path.contains("/dashboard/") ? "dashboard" : "analysis", status < 400 ? "success" : "failure", status);
        }
        if (status >= 400) {
            LOG.warn(
                "Governed BI request failed correlationId={} actor={} assetKey={} surface={} operation={} status={} outcome={}",
                requestId(), actor(), assetKey(path), surface, operation, status,
                failure == null ? "http_error" : failure.getClass().getSimpleName()
            );
        }
    }

    private String surface(String path) {
        if (path.startsWith("/api/analysis")) return "governed_analysis";
        if (isGovernedDashboardAction(path)) return "governed_dashboard";
        if (path.startsWith("/api/card")) return "legacy_card";
        if (path.startsWith("/api/dataset")) return "legacy_dataset";
        if (path.startsWith("/api/dashboard")) return "legacy_dashboard";
        if (path.startsWith("/api/semantic")) return "semantic_query";
        return "bi_other";
    }

    private boolean isGovernedDashboardAction(String path) {
        return path.matches("/api/dashboard/\\d+/(validate|publish|versions(?:/.*)?|registration/retry)");
    }

    private boolean isPublish(String path) {
        return path.matches("/api/(analysis|dashboard)/\\d+/publish");
    }

    private String assetKey(String path) {
        String[] parts = path.split("/");
        if (parts.length > 3 && ("analysis".equals(parts[2]) || "dashboard".equals(parts[2]))) {
            return parts[2] + ":" + parts[3];
        }
        return "unknown";
    }

    private String requestId() {
        String requestId = RequestContextUtils.resolveRequestId();
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null ? "anonymous" : authentication.getName();
    }
}
