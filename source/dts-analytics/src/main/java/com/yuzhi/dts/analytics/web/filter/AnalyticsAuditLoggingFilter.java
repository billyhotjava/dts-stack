package com.yuzhi.dts.analytics.web.filter;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import com.yuzhi.dts.analytics.web.support.RequestContext;
import com.yuzhi.dts.analytics.web.support.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AnalyticsAuditLoggingFilter extends OncePerRequestFilter {

    private static final long READ_DEDUPE_WINDOW_MS = 2_000L;
    private static final int READ_DEDUPE_MAX_SIZE = 2_048;

    private final AnalyticsSessionService sessionService;
    private final AnalyticsAuditForwarderService auditForwarder;
    private final ConcurrentHashMap<String, Long> recentReadEvents = new ConcurrentHashMap<>();

    public AnalyticsAuditLoggingFilter(AnalyticsSessionService sessionService, AnalyticsAuditForwarderService auditForwarder) {
        this.sessionService = sessionService;
        this.auditForwarder = auditForwarder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        return !uri.startsWith("/api/") ||
            uri.startsWith("/api/public/") ||
            uri.startsWith("/api/embed/") ||
            uri.startsWith("/api/health") ||
            uri.startsWith("/api/session");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            recordIfNeeded(request, response, start);
        }
    }

    private void recordIfNeeded(HttpServletRequest request, HttpServletResponse response, long startNanos) {
        Optional<AnalyticsUser> maybeUser = sessionService.resolveUser(request);
        if (maybeUser.isEmpty()) {
            return;
        }
        AnalyticsUser user = maybeUser.orElseThrow();
        String actor = resolveActor(user);
        if (!StringUtils.hasText(actor)) {
            return;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        boolean read = "GET".equalsIgnoreCase(method);
        if (read && isSupplementaryRead(uri)) {
            return;
        }
        AnalyticsAuditEvent event = new AnalyticsAuditEvent(
            actor,
            resolveActorName(user),
            resolveModule(uri),
            deriveAction(method, uri),
            deriveOperationType(method, uri),
            resolveResourceType(uri),
            resolveResourceId(uri),
            response.getStatus() >= 400 ? "FAILED" : "SUCCESS",
            method,
            uri,
            resolveClientIp(request),
            request.getHeader("User-Agent"),
            (int) ((System.nanoTime() - startNanos) / 1_000_000)
        );
        if (read && shouldDedupeRead(actor, event)) {
            return;
        }
        auditForwarder.record(event);
    }

    private boolean isSupplementaryRead(String uri) {
        return uri.startsWith("/api/user/current") ||
            uri.startsWith("/api/user/recipients") ||
            uri.startsWith("/api/util/") ||
            uri.startsWith("/api/setting") ||
            uri.startsWith("/api/permissions/graph");
    }

    private boolean shouldDedupeRead(String actor, AnalyticsAuditEvent event) {
        String key = actor + "|" + event.httpMethod() + "|" + event.requestUri();
        long now = System.currentTimeMillis();
        Long previous = recentReadEvents.put(key, now);
        if (recentReadEvents.size() > READ_DEDUPE_MAX_SIZE) {
            long threshold = now - READ_DEDUPE_WINDOW_MS;
            recentReadEvents.entrySet().removeIf(entry -> entry.getValue() < threshold);
        }
        return previous != null && (now - previous) <= READ_DEDUPE_WINDOW_MS;
    }

    private String resolveActor(AnalyticsUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim();
        }
        if (StringUtils.hasText(user.getPlatformUsername())) {
            return user.getPlatformUsername().trim();
        }
        return user.getId() == null ? null : "user:" + user.getId();
    }

    private String resolveActorName(AnalyticsUser user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String name = (first + " " + last).trim();
        return StringUtils.hasText(name) ? name : resolveActor(user);
    }

    private String resolveModule(String uri) {
        return "analytics." + firstSegment(uri);
    }

    private String resolveResourceType(String uri) {
        return "analytics." + firstSegment(uri);
    }

    private String resolveResourceId(String uri) {
        if (!StringUtils.hasText(uri)) {
            return null;
        }
        String[] parts = uri.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i] == null ? "" : parts[i].trim();
            if (part.matches("\\d+") || part.matches("[0-9a-fA-F-]{32,36}")) {
                return part;
            }
        }
        return null;
    }

    private String deriveAction(String method, String uri) {
        String friendly = friendlyName(firstSegment(uri));
        String lowerUri = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (lowerUri.contains("export") || lowerUri.contains("download") || lowerUri.endsWith("/csv") || lowerUri.endsWith("/xlsx")) {
            return "导出" + friendly;
        }
        if (containsAny(lowerUri, "/import", "/upload")) {
            return "导入" + friendly;
        }
        if (containsAny(lowerUri, "/save", "/save-as", "/copy", "/clone")) {
            return "保存" + friendly;
        }
        if (containsAny(lowerUri, "/sync", "/sync-schema", "/sync_schema", "/rescan", "/refresh", "/rebuild")) {
            return "同步" + friendly;
        }
        if (containsAny(lowerUri, "/publish", "/submit", "/release")) {
            return "发布" + friendly;
        }
        if (containsAny(lowerUri, "/archive", "/offline")) {
            return "归档" + friendly;
        }
        if (containsAny(lowerUri, "/restore", "/rollback", "/revert")) {
            return "回滚" + friendly;
        }
        if (containsAny(lowerUri, "/approve", "/decide")) {
            return "审批" + friendly;
        }
        if (containsAny(lowerUri, "/reject", "/cancel", "/close")) {
            return "驳回" + friendly;
        }
        if (containsAny(lowerUri, "/grant", "/public_link", "/public-link")) {
            return "授权" + friendly;
        }
        if (containsAny(lowerUri, "/revoke", "/ungrant")) {
            return "撤销授权" + friendly;
        }
        if (containsAny(lowerUri, "/query", "/preview", "/explain", "/search", "/validate", "/diagnostics", "/diff", "/impact")) {
            return "查询" + friendly;
        }
        if (lowerUri.contains("query") || lowerUri.contains("run") || lowerUri.contains("execute") ||
            containsAny(lowerUri, "/trigger", "/apply", "/test", "/compile", "/docs", "/generate")) {
            return "执行" + friendly;
        }
        return switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "新增" + friendly;
            case "PUT", "PATCH" -> "修改" + friendly;
            case "DELETE" -> "删除" + friendly;
            case "GET" -> "查看" + friendly;
            default -> "操作" + friendly;
        };
    }

    private String deriveOperationType(String method, String uri) {
        String lowerUri = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        if (lowerUri.contains("export") || lowerUri.contains("download") || lowerUri.endsWith("/csv") || lowerUri.endsWith("/xlsx")) {
            return "EXPORT";
        }
        if (containsAny(lowerUri, "/import", "/upload")) {
            return "IMPORT";
        }
        if (containsAny(lowerUri, "/save", "/save-as", "/copy", "/clone", "/restore", "/rollback", "/revert")) {
            return "UPDATE";
        }
        if (containsAny(lowerUri, "/sync", "/sync-schema", "/sync_schema", "/rescan", "/refresh", "/rebuild")) {
            return "REFRESH";
        }
        if (containsAny(lowerUri, "/publish", "/submit", "/release")) {
            return "PUBLISH";
        }
        if (containsAny(lowerUri, "/archive", "/offline")) {
            return "ARCHIVE";
        }
        if (containsAny(lowerUri, "/approve", "/decide")) {
            return "APPROVE";
        }
        if (containsAny(lowerUri, "/reject", "/cancel", "/close")) {
            return "REJECT";
        }
        if (containsAny(lowerUri, "/grant", "/public_link", "/public-link")) {
            return "GRANT";
        }
        if (containsAny(lowerUri, "/revoke", "/ungrant")) {
            return "REVOKE";
        }
        if (containsAny(lowerUri, "/query", "/preview", "/explain", "/search", "/validate", "/diagnostics", "/diff", "/impact")) {
            return "READ";
        }
        if (lowerUri.contains("query") || lowerUri.contains("run") || lowerUri.contains("execute") ||
            containsAny(lowerUri, "/trigger", "/apply", "/test", "/compile", "/docs", "/generate")) {
            return "EXECUTE";
        }
        return switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "READ";
        };
    }

    private String friendlyName(String key) {
        return Map
            .ofEntries(
                Map.entry("screen", "大屏"),
                Map.entry("dashboard", "仪表板"),
                Map.entry("card", "卡片"),
                Map.entry("dataset", "数据集"),
                Map.entry("database", "数据源"),
                Map.entry("collection", "集合"),
                Map.entry("screen-templates", "大屏模板"),
                Map.entry("screen-packs", "大屏项目包"),
                Map.entry("screen-plugins", "大屏插件"),
                Map.entry("marketplace", "组件市场"),
                Map.entry("semantic", "语义模型"),
                Map.entry("metric", "指标"),
                Map.entry("metrics", "指标"),
                Map.entry("metric-lens", "指标分析"),
                Map.entry("segment", "分群"),
                Map.entry("alert", "告警"),
                Map.entry("pulse", "订阅"),
                Map.entry("bookmark", "收藏"),
                Map.entry("query-trace", "查询追踪"),
                Map.entry("explore-session", "探索会话"),
                Map.entry("report-factory", "报告工厂"),
                Map.entry("nl2sql-eval", "NL2SQL评测"),
                Map.entry("project-cockpit", "项目驾驶舱"),
                Map.entry("field", "字段"),
                Map.entry("table", "数据表")
            )
            .getOrDefault(key, key);
    }

    private boolean containsAny(String source, String... needles) {
        if (!StringUtils.hasText(source) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (StringUtils.hasText(needle) && source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String firstSegment(String uri) {
        if (!StringUtils.hasText(uri)) {
            return "general";
        }
        String[] parts = uri.split("/");
        for (String part : parts) {
            if (!StringUtils.hasText(part) || "api".equals(part)) {
                continue;
            }
            return part.toLowerCase(Locale.ROOT);
        }
        return "general";
    }

    private String resolveClientIp(HttpServletRequest request) {
        RequestContext context = RequestContextHolder.current();
        String forwarded = request.getHeader("X-Forwarded-For");
        String realIp = request.getHeader("X-Real-IP");
        String contextIp = context == null ? null : context.clientIp();
        String remote = request.getRemoteAddr();
        for (String candidate : new String[] { forwarded, realIp, contextIp, remote }) {
            String ip = firstUsableIp(candidate);
            if (StringUtils.hasText(ip)) {
                return ip;
            }
        }
        return null;
    }

    private String firstUsableIp(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        for (String part : candidate.split(",")) {
            String ip = part == null ? "" : part.trim();
            if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip) || isContainerIp(ip)) {
                continue;
            }
            return ip;
        }
        return null;
    }

    private boolean isContainerIp(String ip) {
        return ip.startsWith("127.") || ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
            ip.startsWith("172.18.") || ip.startsWith("172.19.") || ip.startsWith("172.20.") ||
            ip.startsWith("172.21.") || ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
            ip.startsWith("172.24.") || ip.startsWith("172.25.") || ip.startsWith("172.26.") ||
            ip.startsWith("172.27.") || ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
            ip.startsWith("172.30.") || ip.startsWith("172.31.");
    }
}
