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
        // Clear per-request audit marker so we can decide later whether a domain log already happened
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.clear();
        try {
            filterChain.doFilter(wrapper, responseWrapper);
        } finally {
            responseWrapper.flushBuffer();
            try {
                boolean alreadyAudited = com.yuzhi.dts.admin.service.audit.AuditRequestContext.wasDomainAudited();
                if (!alreadyAudited) {
                    PendingAuditEvent event = buildEvent(wrapper, responseWrapper, System.nanoTime() - start);
                    auditService.record(event);
                }
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
        String uri = request.getRequestURI();
        String[] seg = splitSegments(uri);
        String module = resolveModuleFromSegments(seg);
        String resourceType = resolveResourceTypeFromSegments(seg);
        event.module = module;
        event.action = request.getMethod() + " " + uri;
        event.resourceType = resourceType;
        event.resourceId = uri;
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

    private String[] splitSegments(String uri) {
        if (uri == null || uri.isBlank()) {
            return new String[0];
        }
        String sanitized = uri.startsWith("/") ? uri.substring(1) : uri;
        return sanitized.split("/");
    }

    private boolean isGenericPrefix(String s) {
        String k = s.toLowerCase();
        return k.equals("api") || k.equals("v1") || k.equals("v2");
    }

    private String resolveModuleFromSegments(String[] seg) {
        if (seg == null || seg.length == 0) return "general";
        int i = 0;
        while (i < seg.length && (seg[i] == null || seg[i].isBlank() || isGenericPrefix(seg[i]))) i++;
        return i < seg.length ? seg[i] : "general";
    }

    private String resolveResourceTypeFromSegments(String[] seg) {
        if (seg == null || seg.length == 0) return "general";
        int i = 0;
        while (i < seg.length && (seg[i] == null || seg[i].isBlank() || isGenericPrefix(seg[i]))) i++;
        if (i >= seg.length) return "general";
        String s1 = seg[i].toLowerCase();
        String s2 = (i + 1) < seg.length ? seg[i + 1].toLowerCase() : null;
        String s3 = (i + 2) < seg.length ? seg[i + 2].toLowerCase() : null;
        // Map well-known admin endpoints
        if ("keycloak".equals(s1) && "auth".equals(s2)) {
            return "admin.auth"; // -> admin_keycloak_user
        }
        if ("admin".equals(s1)) {
            return "admin"; // -> admin_keycloak_user
        }
        if ("portal".equals(s1) && "menus".equals(s2)) {
            return "portal_menu";
        }
        // Fallbacks
        if (s2 != null && !s2.isBlank()) {
            return s2;
        }
        return s1;
    }
}
