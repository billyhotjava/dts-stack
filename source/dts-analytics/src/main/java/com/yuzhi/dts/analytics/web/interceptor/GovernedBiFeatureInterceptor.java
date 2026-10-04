package com.yuzhi.dts.analytics.web.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.AnalyticsBiFeatureProperties;
import com.yuzhi.dts.analytics.web.support.RequestContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class GovernedBiFeatureInterceptor implements HandlerInterceptor {

    private final AnalyticsBiFeatureProperties features;
    private final ObjectMapper objectMapper;

    public GovernedBiFeatureInterceptor(AnalyticsBiFeatureProperties features, ObjectMapper objectMapper) {
        this.features = features;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (features.isGovernedBiEnabled() || !isGovernedPath(request.getRequestURI())) return true;
        String requestId = RequestContextUtils.resolveRequestId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", "GOVERNED_BI_DISABLED");
        body.put("message", "governed BI is temporarily disabled");
        if (requestId != null && !requestId.isBlank()) body.put("correlationId", requestId);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Error-Code", "GOVERNED_BI_DISABLED");
        objectMapper.writeValue(response.getOutputStream(), body);
        return false;
    }

    private boolean isGovernedPath(String path) {
        if (path.startsWith("/api/analysis")) return true;
        return path.matches("/api/dashboard/\\d+/(validate|publish|versions(?:/.*)?|registration/retry)");
    }
}
