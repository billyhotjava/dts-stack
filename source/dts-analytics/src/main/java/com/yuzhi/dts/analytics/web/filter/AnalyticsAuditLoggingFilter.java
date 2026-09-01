package com.yuzhi.dts.analytics.web.filter;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRequestContext;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import com.yuzhi.dts.common.net.ClientIpTrace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AnalyticsAuditLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAuditLoggingFilter.class);
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
        if (AnalysisRequestContext.hasSpecializedAuditRecorded(request)) {
            return;
        }
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
        ClientIpTrace ipTrace = ClientIpTrace.from(request::getHeader, request.getRemoteAddr());
        ipTrace.logInfo(log, "analytics-audit", method, uri);
        boolean read = "GET".equalsIgnoreCase(method);
        if (read && isSupplementaryRead(uri)) {
            return;
        }
        AnalyticsAuditEvent event = new AnalyticsAuditEvent(
            actor,
            resolveActorName(user),
            resolveModule(uri),
            deriveActionCode(method, uri),
            deriveAction(method, uri),
            deriveOperationType(method, uri),
            resolveResourceType(uri),
            resolveResourceId(uri),
            response.getStatus() >= 400 ? "FAILED" : "SUCCESS",
            method,
            uri,
            ipTrace.resolved(),
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
        if (StringUtils.hasText(user.getPlatformUsername())) {
            return user.getPlatformUsername().trim();
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim();
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
        return "analytics." + canonicalSegment(uri);
    }

    private String resolveResourceType(String uri) {
        String segment = canonicalSegment(uri);
        if ("screen".equals(segment)) {
            return "SCREEN";
        }
        if ("data-portal".equals(segment)) {
            return "DATA_PORTAL";
        }
        return "analytics." + segment;
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
        String friendly = friendlyName(canonicalSegment(uri));
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
        if ("DELETE".equalsIgnoreCase(method) && containsAny(lowerUri, "/grant", "/grants", "/public_link", "/public-link")) {
            return "撤销授权" + friendly;
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

    private String deriveActionCode(String method, String uri) {
        String segment = canonicalSegment(uri);
        String operationType = deriveOperationType(method, uri);
        String lowerUri = uri == null ? "" : uri.toLowerCase(Locale.ROOT);
        return switch (segment) {
            case "screen" -> screenActionCode(operationType, lowerUri);
            case "dashboard" -> dashboardActionCode(operationType);
            case "semantic" -> semanticActionCode(operationType, lowerUri);
            default -> "ANALYTICS_" + normalizeActionSegment(segment) + "_" + operationType;
        };
    }

    private String screenActionCode(String operationType, String lowerUri) {
        if (containsAny(lowerUri, "/public_link", "/public-link")) {
            if ("DELETE".equals(operationType) || "REVOKE".equals(operationType)) {
                return "SCREEN_PUBLIC_LINK_DISABLE";
            }
            return "SCREEN_PUBLIC_LINK_ENABLE";
        }
        if ("GRANT".equals(operationType)) {
            return "SCREEN_ACL_GRANT";
        }
        if ("REVOKE".equals(operationType)) {
            return "SCREEN_ACL_REVOKE";
        }
        if ("EXPORT".equals(operationType)) {
            if (lowerUri.endsWith(".pdf") || lowerUri.contains("/pdf")) {
                return "SCREEN_EXPORT_PDF";
            }
            if (lowerUri.endsWith(".png") || lowerUri.contains("/image") || lowerUri.contains("/render")) {
                return "SCREEN_EXPORT_IMAGE";
            }
            if (lowerUri.endsWith(".json") || lowerUri.contains("/json")) {
                return "SCREEN_EXPORT_JSON";
            }
            return "SCREEN_EXPORT";
        }
        return switch (operationType) {
            case "CREATE" -> "SCREEN_CREATE";
            case "UPDATE" -> "SCREEN_UPDATE";
            case "DELETE", "ARCHIVE" -> "SCREEN_DELETE";
            case "PUBLISH" -> "SCREEN_PUBLISH";
            case "IMPORT" -> "SCREEN_IMPORT";
            case "REFRESH" -> "SCREEN_REFRESH";
            default -> "SCREEN_VIEW";
        };
    }

    private String dashboardActionCode(String operationType) {
        return switch (operationType) {
            case "PUBLISH" -> "VIS_DASHBOARD_PUBLISH";
            case "UPDATE", "CREATE", "IMPORT" -> "VIS_DASHBOARD_EDIT";
            case "EXPORT" -> "VIS_DASHBOARD_EXPORT";
            default -> "VIS_DASHBOARD_VIEW";
        };
    }

    private String semanticActionCode(String operationType, String lowerUri) {
        if (lowerUri.contains("/vds") || lowerUri.contains("/virtual-dataset") || lowerUri.contains("/virtual-datasets")) {
            return switch (operationType) {
                case "CREATE" -> "SEMANTIC_VDS_CREATE";
                case "UPDATE" -> "SEMANTIC_VDS_UPDATE";
                case "DELETE" -> "SEMANTIC_VDS_DELETE";
                case "EXECUTE", "PUBLISH" -> "SEMANTIC_VDS_PROMOTE";
                default -> "SEMANTIC_VDS_LIST";
            };
        }
        if (lowerUri.contains("preview") || lowerUri.contains("explain")) {
            return "SEMANTIC_QUERY_PREVIEW";
        }
        if (lowerUri.contains("query") || lowerUri.contains("execute") || lowerUri.contains("run")) {
            return "SEMANTIC_QUERY_EXECUTE";
        }
        if (lowerUri.contains("graph")) {
            return "SEMANTIC_GRAPH_VIEW";
        }
        if (lowerUri.contains("contract") || lowerUri.contains("publish")) {
            return "SEMANTIC_CONTRACT_PUBLISH";
        }
        return "SEMANTIC_WORKBENCH_VIEW";
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
        if ("DELETE".equalsIgnoreCase(method) && containsAny(lowerUri, "/grant", "/grants", "/public_link", "/public-link")) {
            return "REVOKE";
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
                Map.entry("data-portal", "数据门户"),
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

    private String canonicalSegment(String uri) {
        String segment = firstSegment(uri);
        if ("screens".equals(segment)) {
            return "screen";
        }
        return segment;
    }

    private String normalizeActionSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "GENERAL";
        }
        String normalized = value
            .trim()
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("^_+|_+$", "")
            .toUpperCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : "GENERAL";
    }

}
