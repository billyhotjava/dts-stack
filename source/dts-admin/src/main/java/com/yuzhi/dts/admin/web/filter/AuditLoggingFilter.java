package com.yuzhi.dts.admin.web.filter;

import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.PendingAuditEvent;
import com.yuzhi.dts.common.net.IpAddressUtils;
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
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingFilter.class);
    private static final String[] EXCLUDED_PATH_PREFIXES = new String[] {
        "/management",
        "/actuator",
        "/v3/api-docs",
        "/swagger",
        "/webjars",
        // Avoid noisy anonymous entries around auth handshakes; dedicated listeners emit login/logout events
        "/oauth2",
        "/login",
        "/logout",
        "/sso",
        "/auth",
        // Admin KC auth endpoints (permitAll during login/token exchange)
        "/api/keycloak/auth",
        // App auth bootstrap endpoints
        "/api/authenticate",
        "/api/auth-info",
        // Localization endpoints are permitAll and fetched pre-login by FE
        "/keycloak/localization",
        "/api/keycloak/localization"
    };

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
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        // Clear per-request audit marker so we can decide later whether a domain log already happened
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.clear();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            // Ensure response body is copied back to client
            try { responseWrapper.copyBodyToResponse(); } catch (Exception ignore) {}
            try {
                boolean alreadyAudited = com.yuzhi.dts.admin.service.audit.AuditRequestContext.wasDomainAudited();
                if (!alreadyAudited) {
                    PendingAuditEvent event = buildEvent(requestWrapper, responseWrapper, System.nanoTime() - start);
                    // 仅记录有人为操作上下文：必须是已认证用户，且排除 anonymous/anonymousUser
                    boolean authenticated = com.yuzhi.dts.admin.security.SecurityUtils.isAuthenticated();
                    String actor = event.actor == null ? "" : event.actor.trim();
                    if (authenticated && !actor.isEmpty() &&
                        !"anonymous".equalsIgnoreCase(actor) &&
                        !"anonymoususer".equalsIgnoreCase(actor)) {
                        auditService.record(event);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to record audit trail for {} {}", request.getMethod(), request.getRequestURI(), ex);
            }
        }
    }

    private PendingAuditEvent buildEvent(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long elapsedNanos) {
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = SecurityUtils.getCurrentUserLogin().orElse("");
        event.actorRole = SecurityUtils.getCurrentUserPrimaryAuthority();
        String uri = request.getRequestURI();
        String[] seg = splitSegments(uri);
        String module = resolveModuleFromSegments(seg);
        String resourceType = resolveResourceTypeFromSegments(seg);
        event.module = module;
        event.resourceType = resourceType;
        // Normalize action to a semantic code, not raw "METHOD URI"
        event.action = deriveActionCode(resourceType, request.getMethod());
        event.resourceId = uri;
        // IP 获取：XFF 首段 -> X-Real-IP -> remoteAddr（跳过回环/内网地址）
        String forwarded = request.getHeader("X-Forwarded-For");
        String xfip = (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : null;
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        event.clientIp = IpAddressUtils.resolveClientIp(xfip, realIp, remote);
        event.clientAgent = request.getHeader("User-Agent");
        event.requestUri = request.getRequestURI();
        event.httpMethod = request.getMethod();
        // 结果判定：HTTP 与业务级（ApiResponse）双重判断
        boolean httpFail = response.getStatus() >= HttpStatus.BAD_REQUEST.value();
        boolean bizFail = detectBizFailure(response);
        event.result = (httpFail || bizFail) ? "FAILED" : "SUCCESS";
        event.latencyMs = (int) (elapsedNanos / 1_000_000);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", response.getStatus());
        payload.put("query", request.getQueryString());
        payload.put("responseSize", (long) response.getContentAsByteArray().length);
        payload.put("requestSize", request.getContentLengthLong());
        event.payload = payload;
        return event;
    }

    private String deriveActionCode(String resourceType, String httpMethod) {
        String entity = mapEntityKey(resourceType);
        String op = switch (httpMethod == null ? "" : httpMethod.toUpperCase()) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            case "GET" -> "VIEW";
            default -> "CALL";
        };
        return entity + "_" + op;
    }

    private String mapEntityKey(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) return "GENERAL";
        String r = resourceType.trim().toLowerCase();
        if (r.equals("admin.auth") || r.equals("auth")) return "AUTH";
        if (r.equals("admin") || r.equals("user") || r.equals("admin_keycloak_user")) return "USER";
        if (r.equals("role") || r.contains("role_assignment")) return "ROLE";
        if (r.equals("portal_menu") || r.equals("menu") || r.contains("portal-menus")) return "MENU";
        if (r.equals("org") || r.contains("organization")) return "ORG";
        return r.replaceAll("[^a-z0-9]+", "_").toUpperCase();
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

    private boolean detectBizFailure(ContentCachingResponseWrapper response) {
        try {
            byte[] body = response.getContentAsByteArray();
            if (body == null || body.length == 0) return false;
            String text = new String(body, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return false;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = om.readTree(text);
            if (node == null) return false;
            // 管理端 ApiResponse: { status: "SUCCESS" | "ERROR" }
            if (node.has("status") && node.get("status").isTextual()) {
                String s = node.get("status").asText("").trim().toUpperCase();
                return !("SUCCESS".equals(s));
            }
            // 兼容 { status: 200 | -1 }
            if (node.has("status") && node.get("status").isInt()) {
                int code = node.get("status").asInt(200);
                return code != 200;
            }
            return false;
        } catch (Exception ignore) {
            return false;
        }
    }

}
