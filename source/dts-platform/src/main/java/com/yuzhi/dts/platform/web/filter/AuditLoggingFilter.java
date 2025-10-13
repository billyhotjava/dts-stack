package com.yuzhi.dts.platform.web.filter;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.audit.AuditTrailService;
import com.yuzhi.dts.platform.service.audit.AuditTrailService.PendingAuditEvent;
import com.yuzhi.dts.common.audit.AuditStage;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingFilter.class);
    private static final String[] EXCLUDED_PATH_PREFIXES = new String[] {
        "/management",
        "/actuator",
        "/v3/api-docs",
        "/swagger",
        "/webjars",
        "/error",
        // auth APIs are domain-audited explicitly; skip generic request log to avoid anonymousUser actor
        "/api/keycloak/auth",
        // localization is permitAll and fetched pre-login by FE
        "/api/keycloak/localization"
    };

    private final ObjectProvider<AuditTrailService> auditServiceProvider;
    private final AuditFlowManager flowManager;
    private final boolean accessLogEnabled;

    public AuditLoggingFilter(
        ObjectProvider<AuditTrailService> auditServiceProvider,
        AuditFlowManager flowManager,
        @Value("${dts.http-access-log:false}") boolean accessLogEnabled
    ) {
        this.auditServiceProvider = auditServiceProvider;
        this.flowManager = flowManager;
        this.accessLogEnabled = accessLogEnabled;
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
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        com.yuzhi.dts.platform.service.audit.AuditRequestContext.clear();
        String actionHeader = wrapper.getHeader("X-Audit-Action");
        String flowHeader = wrapper.getHeader("X-Audit-Flow");
        String stageHeader = wrapper.getHeader("X-Audit-Stage");
        AuditStage declaredStage = StringUtils.hasText(stageHeader) ? AuditStage.fromString(stageHeader) : null;
        boolean flowOwned = false;
        Throwable failure = null;
        AuditFlowManager.FlowContext flowContext = null;
        if (StringUtils.hasText(actionHeader)) {
            String flowId = StringUtils.hasText(flowHeader) ? flowHeader : null;
            if (declaredStage == AuditStage.BEGIN) {
                flowContext = flowManager.begin(actionHeader, flowId);
                flowOwned = true;
            } else {
                flowContext = flowManager.attach(actionHeader, flowId);
                flowOwned = true;
            }
        }
        try {
            filterChain.doFilter(wrapper, responseWrapper);
        } catch (IOException | ServletException ex) {
            failure = ex;
            throw ex;
        } catch (RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            try { responseWrapper.copyBodyToResponse(); } catch (Exception ignore) {}
            try {
                boolean alreadyAudited = com.yuzhi.dts.platform.service.audit.AuditRequestContext.wasDomainAudited();
                PendingAuditEvent event = buildEvent(wrapper, responseWrapper, System.nanoTime() - start);
                if (!alreadyAudited) {
                    AuditTrailService svc = auditServiceProvider.getIfAvailable();
                    if (svc != null) {
                        // 仅记录有人为操作上下文：必须是已认证用户，且排除 anonymous/anonymousUser
                        boolean authenticated = com.yuzhi.dts.platform.security.SecurityUtils.isAuthenticated();
                        String actor = event.actor == null ? "" : event.actor.trim();
                        if (authenticated && !actor.isEmpty() &&
                            !"anonymous".equalsIgnoreCase(actor) &&
                            !"anonymoususer".equalsIgnoreCase(actor)) {
                            svc.record(event);
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("AuditTrailService not available; skipping audit record for {} {}", request.getMethod(), request.getRequestURI());
                        }
                    }
                }
                Map<String, Object> flowSummary = new HashMap<>();
                flowSummary.put("status", responseWrapper.getStatus());
                flowSummary.put("uri", wrapper.getRequestURI());
                flowSummary.put("method", wrapper.getMethod());
                flowSummary.put("durationMs", event.latencyMs);
                flowSummary.put("actor", event.actor);
                flowSummary.put("result", event.result);
                if (failure != null) {
                    flowSummary.put("exception", failure.getClass().getSimpleName());
                    flowSummary.put("errorMessage", failure.getMessage());
                }
                AuditStage effectiveStage = declaredStage;
                if (flowContext != null && effectiveStage == null) {
                    effectiveStage = "FAILED".equalsIgnoreCase(event.result) ? AuditStage.FAIL : AuditStage.SUCCESS;
                }
                if (flowContext != null) {
                    if (effectiveStage == AuditStage.SUCCESS) {
                        flowManager.completeSuccess(flowSummary);
                    } else if (effectiveStage == AuditStage.FAIL) {
                        flowManager.completeFailure(failure, flowSummary);
                    } else if (effectiveStage != AuditStage.BEGIN) {
                        flowManager.appendSupportingCall(wrapper.getMethod(), wrapper.getRequestURI(), flowSummary);
                    }
                }
                // Lightweight access log for quick dev troubleshooting
                if (accessLogEnabled && log.isInfoEnabled()) {
                    String user = event.actor == null || event.actor.isBlank() ? "anonymous" : event.actor;
                    String uri = event.requestUri == null ? wrapper.getRequestURI() : event.requestUri;
                    log.info(
                        "[access] {} {} status={} user={} ua=\"{}\" ip={} dur={}ms",
                        wrapper.getMethod(),
                        uri,
                        responseWrapper.getStatus(),
                        user,
                        safeHeader(wrapper, "User-Agent"),
                        event.clientIp,
                        event.latencyMs
                    );
                }
            } catch (Exception ex) {
                log.warn("Failed to record audit trail for {} {}", request.getMethod(), request.getRequestURI(), ex);
            } finally {
                if (flowOwned) {
                    flowManager.clear();
                }
            }
        }
    }

    private PendingAuditEvent buildEvent(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long elapsedNanos) {
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = SecurityUtils.getCurrentUserLogin().orElse("");
        event.actorRole = resolvePrimaryAuthority();
        String uri = request.getRequestURI();
        String[] seg = splitSegments(uri);
        String module = resolveModuleFromSegments(seg);
        String resourceType = resolveResourceTypeFromSegments(seg);
        event.module = module;
        event.resourceType = resourceType;
        // Derive a semantic action code instead of raw "METHOD URI"
        event.action = deriveActionCode(resourceType, request.getMethod());
        event.resourceId = uri;
        String forwarded = request.getHeader("X-Forwarded-For");
        String xfip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : null;
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        event.clientIp = firstNonBlank(xfip, realIp, remote, "127.0.0.1");
        event.clientAgent = request.getHeader("User-Agent");
        event.requestUri = request.getRequestURI();
        event.httpMethod = request.getMethod();
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

    private String[] splitSegments(String uri) {
        if (uri == null || uri.isBlank()) {
            return new String[0];
        }
        String sanitized = uri.startsWith("/") ? uri.substring(1) : uri;
        return sanitized.split("/");
    }

    // Prefer the first meaningful segment as module; skip common prefixes like "api", "v1", "v2"
    private String resolveModuleFromSegments(String[] seg) {
        if (seg == null || seg.length == 0) return "general";
        int i = 0;
        while (i < seg.length && (seg[i] == null || seg[i].isBlank() || isGenericPrefix(seg[i]))) i++;
        return i < seg.length ? seg[i] : "general";
    }

    private boolean isGenericPrefix(String s) {
        String k = s.toLowerCase();
        return k.equals("api") || k.equals("v1") || k.equals("v2");
    }

    // Derive a more specific resourceType from URI segments to avoid non-existent tables like "api"
    private String resolveResourceTypeFromSegments(String[] seg) {
        if (seg == null || seg.length == 0) return "general";
        int i = 0;
        while (i < seg.length && (seg[i] == null || seg[i].isBlank() || isGenericPrefix(seg[i]))) i++;
        if (i >= seg.length) return "general";
        String s1 = seg[i].toLowerCase();
        String s2 = (i + 1) < seg.length ? seg[i + 1].toLowerCase() : null;
        String s3 = (i + 2) < seg.length ? seg[i + 2].toLowerCase() : null;
        // Known mappings
        if ("keycloak".equals(s1) && "auth".equals(s2)) {
            return "admin.auth"; // maps to admin_keycloak_user
        }
        if ("admin".equals(s1)) {
            return "admin"; // maps to admin_keycloak_user in table mapping
        }
        if ("portal".equals(s1) && "menus".equals(s2)) {
            return "portal_menu";
        }
        // Fallbacks: prefer the next segment when present; otherwise use first meaningful
        if (s2 != null && !s2.isBlank()) {
            return s2;
        }
        return s1;
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
        // Modeling / services / governance common buckets
        if (r.contains("standard")) return "STANDARD";
        if (r.contains("svc") || r.contains("api")) return "SERVICE";
        if (r.contains("gov")) return "GOV";
        if (r.contains("catalog")) return "CATALOG";
        return r.replaceAll("[^a-z0-9]+", "_").toUpperCase();
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
            if (node.has("status") && node.get("status").isInt()) {
                int code = node.get("status").asInt(200);
                return code != 200;
            }
            if (node.has("status") && node.get("status").isTextual()) {
                String s = node.get("status").asText("");
                return !"SUCCESS".equalsIgnoreCase(s);
            }
            return false;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String resolvePrimaryAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication
            .getAuthorities()
            .stream()
            .findFirst()
            .map(GrantedAuthority::getAuthority)
            .orElse(null);
    }

    private String safeHeader(HttpServletRequest req, String name) {
        try {
            String v = req.getHeader(name);
            return v == null ? "" : v;
        } catch (Exception ignored) {
            return "";
        }
    }
}
