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
        "/error"
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
        AuditResponseWrapper responseWrapper = new AuditResponseWrapper(response);
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
            responseWrapper.flushBuffer();
            try {
                PendingAuditEvent event = buildEvent(wrapper, responseWrapper, System.nanoTime() - start);
                AuditTrailService svc = auditServiceProvider.getIfAvailable();
                if (svc != null) {
                    svc.record(event);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("AuditTrailService not available; skipping audit record for {} {}", request.getMethod(), request.getRequestURI());
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
                    effectiveStage = "FAILURE".equalsIgnoreCase(event.result) ? AuditStage.FAIL : AuditStage.SUCCESS;
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

    private PendingAuditEvent buildEvent(ContentCachingRequestWrapper request, AuditResponseWrapper response, long elapsedNanos) {
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        event.actorRole = resolvePrimaryAuthority();
        event.module = resolveModule(request.getRequestURI());
        event.action = request.getMethod() + " " + request.getRequestURI();
        event.resourceType = event.module;
        event.resourceId = request.getRequestURI();
        String forwarded = request.getHeader("X-Forwarded-For");
        event.clientIp = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
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
