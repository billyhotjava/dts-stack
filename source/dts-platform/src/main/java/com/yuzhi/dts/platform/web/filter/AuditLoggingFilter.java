package com.yuzhi.dts.platform.web.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.net.IpAddressUtils;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
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

    private static final boolean AUDIT_CONTEXT_PRESENT;
    private static final Class<?> AUDIT_CONTEXT_CLASS;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private static final String ATTRIBUTE_BODY_CACHE = AuditLoggingFilter.class.getName() + ".JSON_BODY";

    static {
        boolean present;
        Class<?> ctxClass = null;
        try {
            ctxClass = Class.forName("com.yuzhi.dts.platform.service.audit.AuditRequestContext");
            present = true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            present = false;
        }
        AUDIT_CONTEXT_PRESENT = present;
        AUDIT_CONTEXT_CLASS = ctxClass;
    }

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
        clearAuditContext();
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
                boolean alreadyAudited = wasDomainAuditMarked();
                boolean fallbackRequested = consumeFallbackRequest();
                PendingAuditEvent event = buildEvent(wrapper, responseWrapper, System.nanoTime() - start);
                boolean suppressed = shouldSuppressAudit(wrapper, event);
                if (fallbackRequested && !alreadyAudited && !suppressed) {
                    AuditTrailService svc = auditServiceProvider.getIfAvailable();
                    if (svc != null) {
                        // 仅记录有人为操作上下文：必须是已认证用户，且排除 anonymous/anonymousUser
                        boolean authenticated = com.yuzhi.dts.platform.security.SecurityUtils.isAuthenticated();
                        String actor = event.actor == null ? "" : event.actor.trim();
                        if (authenticated && !actor.isEmpty() &&
                            !"anonymous".equalsIgnoreCase(actor) &&
                            !"anonymoususer".equalsIgnoreCase(actor)) {
                            svc.record(event);
                        } else if (log.isDebugEnabled()) {
                            log.debug("Skip HTTP fallback audit for actor='{}', uri={}", actor, request.getRequestURI());
                        }
                    } else if (log.isTraceEnabled()) {
                        log.trace(
                            "AuditTrailService not available or fallback not requested (fallbackRequested={}, alreadyAudited={}, uri={})",
                            fallbackRequested,
                            alreadyAudited,
                            request.getRequestURI()
                        );
                    } else {
                        log.trace("Audit fallback bypassed for {} {} (fallbackRequested={}, alreadyAudited={}, suppressed={})",
                            request.getMethod(),
                            request.getRequestURI(),
                            fallbackRequested,
                            alreadyAudited,
                            suppressed);
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
                    String safeIp = (event.clientIp == null || event.clientIp.isBlank()) ? "-" : event.clientIp;
                    log.info(
                        "[access] {} {} status={} user={} ua=\"{}\" ip={} dur={}ms",
                        wrapper.getMethod(),
                        uri,
                        responseWrapper.getStatus(),
                        user,
                        safeHeader(wrapper, "User-Agent"),
                        safeIp,
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

    private void clearAuditContext() {
        if (!AUDIT_CONTEXT_PRESENT) {
            return;
        }
        try {
            if (AUDIT_CONTEXT_CLASS != null) {
                AUDIT_CONTEXT_CLASS.getMethod("clear").invoke(null);
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {}
    }

    private boolean wasDomainAuditMarked() {
        if (!AUDIT_CONTEXT_PRESENT) {
            return false;
        }
        try {
            if (AUDIT_CONTEXT_CLASS != null) {
                Object result = AUDIT_CONTEXT_CLASS.getMethod("wasDomainAudited").invoke(null);
                if (result instanceof Boolean b) {
                    return b.booleanValue();
                }
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
        return false;
    }

    private boolean consumeFallbackRequest() {
        if (!AUDIT_CONTEXT_PRESENT) {
            return false;
        }
        try {
            if (AUDIT_CONTEXT_CLASS != null) {
                Object result = AUDIT_CONTEXT_CLASS.getMethod("consumeHttpFallbackRequest").invoke(null);
                if (result instanceof Boolean b) {
                    return b.booleanValue();
                }
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
            return false;
        }
        return false;
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
        String defaultResourceId = uri;
        event.resourceId = null;
        String forwardedCombined = request.getHeader("Forwarded");
        String forwardedHeader = request.getHeader("X-Forwarded-For");
        String realIpHeader = request.getHeader("X-Real-IP");
        String remoteAddress = request.getRemoteAddr();
        String[] ipCandidates = resolveClientIpCandidates(forwardedCombined, forwardedHeader, realIpHeader, remoteAddress);
        String resolvedClientIp = IpAddressUtils.resolveClientIp(ipCandidates);
        event.clientIp = resolvedClientIp;
        logClientIpTrace("platform", forwardedHeader, forwardedCombined, realIpHeader, remoteAddress, resolvedClientIp, ipCandidates);
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
        applySemanticHints(event, request, defaultResourceId);
        if (!StringUtils.hasText(event.resourceId) && !event.disableDefaultResourceFallback) {
            event.resourceId = defaultResourceId;
        }
        return event;
    }

    private boolean shouldSuppressAudit(ContentCachingRequestWrapper request, PendingAuditEvent event) {
        if (request != null) {
            String silentHeader = request.getHeader("X-Audit-Silent");
            if ("true".equalsIgnoreCase(silentHeader)) {
                return true;
            }
            String silentParam = request.getParameter("auditSilent");
            if ("true".equalsIgnoreCase(silentParam)) {
                return true;
            }
        }
        if (request != null && isSupplementaryQuery(request)) {
            return true;
        }
        String actor = event.actor;
        if (actor != null) {
            String normalizedActor = actor.trim().toLowerCase(Locale.ROOT);
            if (normalizedActor.startsWith("service:") || "system".equals(normalizedActor)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupplementaryQuery(ContentCachingRequestWrapper request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (!StringUtils.hasText(uri) || !StringUtils.hasText(method)) {
            return false;
        }
        if (uri.startsWith("/api/modeling/")) {
            // 所有数据标准相关接口由领域代码显式审计，过滤器不再重复记录
            return true;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if (
            uri.startsWith("/api/catalog/config") ||
            uri.startsWith("/api/catalog/summary") ||
            uri.startsWith("/api/catalog/domains") ||
            uri.startsWith("/api/catalog/classification-mapping") ||
            uri.startsWith("/api/catalog/masking-rules")
        ) {
            return true;
        }
        if (uri.startsWith("/api/catalog/datasets/") && uri.endsWith("/grants")) {
            return true;
        }
        if (uri.startsWith("/api/modeling/orgs") || uri.startsWith("/api/iam/orgs")) {
            // 组织树查询属于附带请求，不计入审计
            return true;
        }
        if (uri.startsWith("/api/directory/orgs") || uri.startsWith("/api/directory/users") || uri.startsWith("/api/directory/roles")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/infra/data-sources")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/infra/data-storages")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/infra/data-sources/test-logs")) {
            return true;
        }
        if (uri.startsWith("/api/catalog/domains") || uri.contains("/dictionary/") || uri.contains("/lookup/")) {
            return true;
        }
        return false;
    }

    private String[] splitSegments(String uri) {
        if (uri == null || uri.isBlank()) {
            return new String[0];
        }
        String sanitized = uri.startsWith("/") ? uri.substring(1) : uri;
        return sanitized.split("/");
    }

    private String[] resolveClientIpCandidates(String forwardedCombined, String forwarded, String realIp, String remote) {
        List<String> candidates = new ArrayList<>();
        appendForwardedHeaderCandidates(forwardedCombined, candidates);
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            for (String part : parts) {
                String trimmed = part == null ? null : part.trim();
                if (StringUtils.hasText(trimmed)) {
                    candidates.add(trimmed);
                }
            }
        }
        if (StringUtils.hasText(realIp)) {
            candidates.add(realIp.trim());
        }
        if (StringUtils.hasText(remote)) {
            candidates.add(remote.trim());
        }
        return candidates.toArray(new String[0]);
    }

    private void logClientIpTrace(String marker, String forwarded, String forwardedCombined, String realIp, String remote, String resolved, String[] candidates) {
        boolean fallbackToRemote = StringUtils.hasText(resolved)
            && StringUtils.hasText(remote)
            && resolved.trim().equals(remote.trim());
        boolean missingForwarded = !StringUtils.hasText(forwarded);
        if (log.isInfoEnabled()) {
            log.info("[{}-client-ip] resolved={} forwarded='{}' forwardedStd='{}' real='{}' remote='{}' candidates={} fallbackToRemote={} missingForwarded={}",
                marker,
                nullSafe(resolved),
                nullSafe(forwarded),
                nullSafe(forwardedCombined),
                nullSafe(realIp),
                nullSafe(remote),
                Arrays.toString(candidates),
                fallbackToRemote,
                missingForwarded
            );
        } else if (log.isDebugEnabled()) {
            log.debug("[{}-client-ip] resolved={} forwarded='{}' forwardedStd='{}' real='{}' remote='{}' candidates={} fallbackToRemote={} missingForwarded={}",
                marker,
                nullSafe(resolved),
                nullSafe(forwarded),
                nullSafe(forwardedCombined),
                nullSafe(realIp),
                nullSafe(remote),
                Arrays.toString(candidates),
                fallbackToRemote,
                missingForwarded
            );
        }
    }

    private void appendForwardedHeaderCandidates(String header, List<String> candidates) {
        if (!StringUtils.hasText(header)) {
            return;
        }
        String[] segments = header.split(",");
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            String[] parts = segment.split(";");
            for (String part : parts) {
                String trimmed = part == null ? "" : part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int idx = trimmed.toLowerCase(Locale.ROOT).indexOf("for=");
                if (idx != 0) {
                    continue;
                }
                String value = trimmed.substring(4).trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.startsWith("[")) {
                    int closeIdx = value.indexOf(']');
                    if (closeIdx > 0) {
                        String ipv6 = value.substring(1, closeIdx);
                        if (!ipv6.isBlank()) {
                            candidates.add(ipv6);
                            continue;
                        }
                    }
                }
                int colon = value.indexOf(':');
                if (colon > 0 && value.indexOf(':', colon + 1) == -1) {
                    value = value.substring(0, colon);
                }
                if (!value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                    candidates.add(value);
                }
            }
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
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
        String friendly = friendlyName(resourceType);
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        return switch (method) {
            case "POST" -> "新增" + friendly;
            case "PUT", "PATCH" -> "修改" + friendly;
            case "DELETE" -> "删除" + friendly;
            case "GET" -> "查看" + friendly;
            default -> "操作" + friendly;
        };
    }

    private void applySemanticHints(PendingAuditEvent event, ContentCachingRequestWrapper request, String defaultResourceId) {
        String uri = request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        Map<String, String[]> params = request.getParameterMap();
        boolean paged = params.containsKey("page") || params.containsKey("size") || params.containsKey("limit");

        if (uri.startsWith("/api/explore/query/preview")) {
            event.module = "platform.explore";
            event.resourceType = "explore.dataset";
            String datasetId = extractJsonField(request, "datasetId");
            setAction(event, "预览数据集", datasetId, true, false);
            return;
        }

        if (uri.startsWith("/api/explore/explain")) {
            event.module = "platform.explore";
            event.resourceType = "explore.query";
            if ("POST".equals(method)) {
                setAction(event, "查看查询计划", null, true, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/save-result/")) {
            String id = extractTrailingId(uri, "/api/explore/save-result");
            event.module = "platform.explore";
            event.resourceType = "explore.resultSet";
            if ("POST".equals(method)) {
                setAction(event, "保存查询结果", id, false, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/result-preview")) {
            String id = extractTrailingId(uri, "/api/explore/result-preview");
            event.module = "platform.explore";
            event.resourceType = "explore.resultSet";
            if ("GET".equals(method)) {
                setAction(event, "预览查询结果", id, false, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/result-sets/cleanup")) {
            event.module = "platform.explore";
            event.resourceType = "explore.resultSet";
            if ("POST".equals(method)) {
                setAction(event, "清理查询结果集", null, true, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/result-sets")) {
            String id = extractTrailingId(uri, "/api/explore/result-sets");
            event.module = "platform.explore";
            event.resourceType = "explore.resultSet";
            boolean isDownload = uri.contains("/download");
            if ("GET".equals(method) && !StringUtils.hasText(id)) {
                setAction(event, "查看查询结果列表", null, true, true);
            } else if ("GET".equals(method) && isDownload) {
                setAction(event, "下载查询结果", id, false, false);
            } else if ("GET".equals(method)) {
                setAction(event, "查看查询结果", id, false, false);
            } else if ("POST".equals(method) && !StringUtils.hasText(id)) {
                setAction(event, "提交查询任务", null, true, false);
            } else if ("DELETE".equals(method) && StringUtils.hasText(id)) {
                setAction(event, "删除查询结果", id, false, false);
            }
            return;
        }

        if (uri.startsWith("/api/menu/tree")) {
            event.module = "platform.menu";
            event.resourceType = "portal.menu";
            setAction(event, "获取菜单树", null, true, true);
            return;
        }

        if (uri.startsWith("/api/approval-requests")) {
            String id = extractTrailingId(uri, "/api/approval-requests");
            event.module = "platform.approval";
            event.resourceType = "approval.request";
            if ("GET".equals(method) && !StringUtils.hasText(id)) {
                setAction(event, "查看审批请求列表", null, true, true);
            } else if ("GET".equals(method)) {
                setAction(event, "查看审批请求", id, false, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/execute")) {
            event.module = "platform.explore";
            event.resourceType = "explore.query";
            if ("POST".equals(method)) {
                String datasetId = extractJsonField(request, "datasetId");
                setAction(event, "执行数据查询", datasetId, true, false);
            }
            return;
        }

        if (uri.startsWith("/api/explore/query-executions")) {
            event.module = "platform.explore";
            event.resourceType = "explore.execution";
            if ("GET".equals(method)) {
                setAction(event, "查看查询任务历史", null, true, true);
            }
            return;
        }

        if (uri.startsWith("/api/explore/saved-queries")) {
            event.module = "platform.explore";
            boolean run = uri.endsWith("/run") || uri.contains("/run?");
            String id = extractFirstPathSegment(uri, "/api/explore/saved-queries/");
            if (!StringUtils.hasText(id)) {
                id = extractTrailingId(uri, "/api/explore/saved-queries");
            }
            if (run) {
                event.resourceType = "explore.savedQuery";
                if ("POST".equals(method)) {
                    setAction(event, "运行保存查询", id, false, false);
                }
                return;
            }
            event.resourceType = "explore.savedQuery";
            switch (method) {
                case "GET" -> {
                    if (!StringUtils.hasText(id) || uri.endsWith("/saved-queries")) {
                        setAction(event, "查看保存查询列表", null, true, true);
                    } else {
                        setAction(event, "查看保存查询", id, false, false);
                    }
                }
                case "POST" -> {
                    String name = extractJsonField(request, "name");
                    setAction(event, "新建保存查询", name, true, false);
                }
                case "PUT" -> setAction(event, "更新保存查询", id, false, false);
                case "DELETE" -> setAction(event, "删除保存查询", id, false, false);
                default -> {}
            }
            return;
        }

        if (uri.startsWith("/api/catalog/datasets")) {
            String id = extractTrailingId(uri, "/api/catalog/datasets");
            event.module = "platform.catalog";
            event.resourceType = "catalog.dataset";
            if ("GET".equals(method) && !StringUtils.hasText(id)) {
                setAction(event, "查看数据集列表", null, true, true);
            } else if ("GET".equals(method)) {
                setAction(event, "查看数据集详情", id, false, false);
            }
            return;
        }

        if (uri.startsWith("/api/governance/compliance/batches")) {
            String id = extractTrailingId(uri, "/api/governance/compliance/batches");
            event.module = "platform.governance";
            event.resourceType = "governance.compliance";
            switch (method) {
                case "GET" -> {
                    if (!StringUtils.hasText(id)) {
                        setAction(event, "查看合规批次列表", null, true, true);
                    } else {
                        setAction(event, "查看合规批次", id, false, false);
                    }
                }
                case "POST" -> setAction(event, "新建合规批次", null, true, false);
                case "DELETE" -> setAction(event, "删除合规批次", id, false, false);
                default -> {}
            }
            return;
        }

        if (uri.startsWith("/api/governance/compliance/items")) {
            String id = extractTrailingId(uri, "/api/governance/compliance/items");
            event.module = "platform.governance";
            event.resourceType = "governance.compliance.item";
            if ("PUT".equals(method)) {
                setAction(event, "更新合规批次项", id, false, false);
            } else if ("DELETE".equals(method)) {
                setAction(event, "删除合规批次项", id, false, false);
            } else if ("GET".equals(method)) {
                if (!StringUtils.hasText(id)) {
                    setAction(event, "查看合规批次项列表", null, true, true);
                } else {
                    setAction(event, "查看合规批次项", id, false, false);
                }
            }
            return;
        }

        if (uri.startsWith("/api/governance/quality/rules")) {
            boolean toggle = uri.endsWith("/toggle");
            String id = extractTrailingId(uri, "/api/governance/quality/rules");
            if (toggle && !StringUtils.hasText(id)) {
                id = extractFirstPathSegment(uri, "/api/governance/quality/rules/");
            }
            event.module = "platform.governance";
            event.resourceType = "governance.quality.rule";
            switch (method) {
                case "GET" -> {
                    if (!StringUtils.hasText(id) || uri.endsWith("/rules")) {
                        setAction(event, "查看质量规则列表", null, true, true);
                    } else if (!toggle) {
                        setAction(event, "查看质量规则", id, false, false);
                    }
                }
                case "POST" -> {
                    if (toggle) {
                        setAction(event, "调整质量规则状态", id, false, false);
                    } else {
                        setAction(event, "新建质量规则", null, true, false);
                    }
                }
                case "PUT" -> setAction(event, "更新质量规则", id, false, false);
                case "DELETE" -> setAction(event, "删除质量规则", id, false, false);
                default -> {}
            }
            return;
        }

        if (uri.startsWith("/api/modeling/standards")) {
            String basePrefix = "/api/modeling/standards/";
            String standardId = extractFirstPathSegment(uri, basePrefix);
            boolean attachments = uri.contains("/attachments");
            event.module = "platform.modeling";
            if (attachments) {
                event.resourceType = "modeling.standard.attachment";
                String attachmentBase = standardId == null ? "/api/modeling/standards" : basePrefix + standardId + "/attachments";
                String attachmentId = standardId == null ? null : extractTrailingId(uri, attachmentBase);
                boolean isDownload = uri.contains("/download");
                if ("GET".equals(method) && !StringUtils.hasText(attachmentId)) {
                    setAction(event, "查看数据标准附件列表", standardId, true, true);
                } else if ("GET".equals(method) && isDownload) {
                    setAction(event, "下载数据标准附件", attachmentId, false, false);
                    event.operationType = "DOWNLOAD";
                } else if ("GET".equals(method)) {
                    setAction(event, "查看数据标准附件", attachmentId, false, false);
                } else if ("POST".equals(method)) {
                    setAction(event, "上传数据标准附件", standardId, false, false);
                } else if ("DELETE".equals(method)) {
                    setAction(event, "删除数据标准附件", attachmentId, false, false);
                }
            } else {
                event.resourceType = "modeling.standard";
                String id = extractTrailingId(uri, "/api/modeling/standards");
                if (!StringUtils.hasText(id)) {
                    id = standardId;
                }
                switch (method) {
                    case "GET" -> {
                        if (!StringUtils.hasText(id) || uri.endsWith("/standards")) {
                            setAction(event, "查看数据标准列表", null, true, true);
                        } else {
                            setAction(event, "查看数据标准", id, false, false);
                        }
                    }
                    case "POST" -> setAction(event, "新建数据标准", null, true, false);
                    case "PUT", "PATCH" -> setAction(event, "更新数据标准", id, false, false);
                    case "DELETE" -> setAction(event, "删除数据标准", id, false, false);
                    default -> {}
                }
            }
            return;
        }

        if ("GET".equals(method) && paged) {
            String friendly = friendlyName(event.resourceType);
            setAction(event, "查看" + friendly + "列表", null, true, true);
            return;
        }

        if ("GET".equals(method) && defaultResourceId != null && defaultResourceId.contains("/api")) {
            // Detail view heuristic: use last path segment as id when numeric/uuid
            String id = extractLastSegment(defaultResourceId);
            if (id != null && !id.isBlank() && !id.contains("/")) {
                setAction(event, "查看" + friendlyName(event.resourceType), id, false, false);
                return;
            }
        }
    }

    private void setAction(
        AuditTrailService.PendingAuditEvent event,
        String action,
        String resourceId,
        boolean disableFallback,
        boolean markList
    ) {
        if (event == null) {
            return;
        }
        if (StringUtils.hasText(action)) {
            event.action = action;
        }
        if (disableFallback) {
            event.disableDefaultResourceFallback = true;
        }
        if (markList) {
            event.extraTags = appendExtraTag(event.extraTags, "scope=list");
        }
        if (resourceId != null) {
            String trimmed = resourceId.trim();
            event.resourceId = trimmed.isEmpty() ? null : trimmed;
        } else {
            event.resourceId = null;
        }
    }

    private String extractTrailingId(String uri, String basePath) {
        if (uri == null || basePath == null) {
            return null;
        }
        if (!uri.startsWith(basePath)) {
            return null;
        }
        String remainder = uri.substring(basePath.length());
        if (remainder.isEmpty() || "/".equals(remainder)) {
            return null;
        }
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }
        int slash = remainder.indexOf('/');
        return slash >= 0 ? remainder.substring(0, slash) : remainder;
    }

    private String extractLastSegment(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String trimmed = uri;
        int queryIdx = trimmed.indexOf('?');
        if (queryIdx >= 0) {
            trimmed = trimmed.substring(0, queryIdx);
        }
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        int slash = trimmed.lastIndexOf('/');
        if (slash < 0) {
            return null;
        }
        return trimmed.substring(slash + 1);
    }

    private String friendlyName(String resourceType) {
        if (!StringUtils.hasText(resourceType)) {
            return "资源";
        }
        String key = resourceType.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "explore.resultset", "explore.result-set", "explore.result" -> "查询结果";
            case "explore.dataset" -> "数据集";
            case "explore.query" -> "查询";
            case "explore.execution" -> "查询任务";
            case "explore.savedquery", "explore.saved-query" -> "保存查询";
            case "portal.menu", "portal_menu", "menu" -> "菜单";
            case "approval.request", "approval_request" -> "审批请求";
            case "organization", "organization_node" -> "组织";
            case "dataset", "data.dataset" -> "数据集";
            case "governance.compliance", "governance.compliance.batch" -> "合规批次";
            case "governance.compliance.item", "compliance.item" -> "合规批次项";
            case "governance.quality.rule", "quality.rule" -> "质量规则";
            case "modeling.standard", "modeling.standard.definition" -> "数据标准";
            case "modeling.standard.attachment", "standard.attachment" -> "数据标准附件";
            default -> resourceType;
        };
    }

    private String extractFirstPathSegment(String uri, String prefix) {
        if (!StringUtils.hasText(uri) || !StringUtils.hasText(prefix)) {
            return null;
        }
        String working = uri;
        int queryIdx = working.indexOf('?');
        if (queryIdx >= 0) {
            working = working.substring(0, queryIdx);
        }
        if (!working.startsWith(prefix)) {
            return null;
        }
        String remainder = working.substring(prefix.length());
        if (remainder.startsWith("/")) {
            remainder = remainder.substring(1);
        }
        if (remainder.isEmpty()) {
            return null;
        }
        int slash = remainder.indexOf('/');
        String segment = slash >= 0 ? remainder.substring(0, slash) : remainder;
        return trimToNull(segment);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String appendExtraTag(String existing, String addition) {
        if (!StringUtils.hasText(addition)) {
            return existing;
        }
        if (!StringUtils.hasText(existing)) {
            return addition;
        }
        if (existing.contains(addition)) {
            return existing;
        }
        return existing + "," + addition;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonBody(ContentCachingRequestWrapper request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        Object cached = request.getAttribute(ATTRIBUTE_BODY_CACHE);
        if (cached instanceof Map<?, ?> cachedMap) {
            return (Map<String, Object>) cachedMap;
        }
        byte[] body = request.getContentAsByteArray();
        if (body == null || body.length == 0) {
            request.setAttribute(ATTRIBUTE_BODY_CACHE, Collections.emptyMap());
            return Collections.emptyMap();
        }
        String text = new String(body, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            request.setAttribute(ATTRIBUTE_BODY_CACHE, Collections.emptyMap());
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JSON.readValue(text, MAP_TYPE);
            request.setAttribute(ATTRIBUTE_BODY_CACHE, parsed);
            return parsed;
        } catch (Exception ex) {
            request.setAttribute(ATTRIBUTE_BODY_CACHE, Collections.emptyMap());
            return Collections.emptyMap();
        }
    }

    private String extractJsonField(ContentCachingRequestWrapper request, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Map<String, Object> body = readJsonBody(request);
        if (body.isEmpty()) {
            return null;
        }
        Object current = body;
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        if (current instanceof List<?> list) {
            current = list.isEmpty() ? null : list.get(0);
        }
        if (current == null) {
            return null;
        }
        return trimToNull(String.valueOf(current));
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
