package com.yuzhi.dts.admin.web.filter;

import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.PendingAuditEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingFilter.class);
    private static final String[] EXCLUDED_PATH_PREFIXES = new String[] { "/management", "/actuator", "/v3/api-docs", "/swagger", "/webjars" };

    private final AdminAuditService auditService;

    public AuditLoggingFilter(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        for (String prefix : EXCLUDED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long start = System.nanoTime();
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        AuditResponseWrapper responseWrapper = new AuditResponseWrapper(response);
        try {
            filterChain.doFilter(wrapper, responseWrapper);
        } finally {
            responseWrapper.flushBuffer();
            try {
                PendingAuditEvent event = buildEvent(wrapper, responseWrapper, System.nanoTime() - start);
                auditService.record(event);
            } catch (Exception ex) {
                log.warn("Failed to record audit trail for {} {}", request.getMethod(), request.getRequestURI(), ex);
            }
        }
    }

    private PendingAuditEvent buildEvent(ContentCachingRequestWrapper request, AuditResponseWrapper response, long elapsedNanos) {
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        event.actorRole = SecurityUtils.getCurrentUserPrimaryAuthority();
        event.module = resolveModule(request.getRequestURI());
        event.action = request.getMethod() + " " + request.getRequestURI();
        event.resourceType = event.module;
        event.resourceId = request.getRequestURI();
        String forwarded = request.getHeader("X-Forwarded-For");
        event.clientIp = (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        event.clientAgent = request.getHeader("User-Agent");
        event.requestUri = request.getRequestURI();
        event.httpMethod = request.getMethod();
        event.result = response.getStatus() >= HttpStatus.BAD_REQUEST.value() ? "FAILURE" : "SUCCESS";
        event.latencyMs = (int) (elapsedNanos / 1_000_000);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", response.getStatus());
        payload.put("query", request.getQueryString());
        payload.put("responseSize", response.getContentSize());
        payload.put("requestSize", request.getContentLengthLong());
        event.payload = payload;
        return event;
    }

    private String resolveModule(String uri) {
        if (uri == null || uri.isBlank()) {
            return "general";
        }
        String sanitized = uri.startsWith("/") ? uri.substring(1) : uri;
        int idx = sanitized.indexOf('/');
        if (idx > 0) {
            return sanitized.substring(0, idx);
        }
        return sanitized.isEmpty() ? "general" : sanitized;
    }
}
