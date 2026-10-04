package com.yuzhi.dts.analytics.web.filter;

import com.yuzhi.dts.analytics.service.PlatformPermissionClient;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.PermissionResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepts asset-related requests and checks permissions via platform API.
 * Must run after PlatformSessionBridgeFilter (which sets X-DTS-User headers).
 */
@Component
@Order(200)
public class PlatformPermissionFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformPermissionFilter.class);

    private static final String HEADER_USER = "X-DTS-User";
    private static final String HEADER_ROLES = "X-DTS-Roles";
    private static final String HEADER_DEPT = "X-DTS-Dept-Code";

    /** URL patterns that map to asset references: group(1)=assetType, group(2)=assetId */
    private static final Pattern CARD_PATTERN = Pattern.compile("^/api/card/(\\d+)(?:/.*)?$");
    private static final Pattern ANALYSIS_PATTERN = Pattern.compile("^/api/analysis/(\\d+)(?:/.*)?$");
    private static final Pattern DASHBOARD_PATTERN = Pattern.compile("^/api/dashboard/(\\d+)(?:/.*)?$");
    // SCREEN removed: screen permissions are handled by ScreenPermissionService in the controller layer
    private static final Pattern TABLE_PATTERN = Pattern.compile("^/api/table/(\\d+)(?:/.*)?$");

    /** Paths that should NOT be filtered (non-asset or pre-auth paths) */
    private static final Set<String> SKIP_PREFIXES = Set.of(
        "/api/session",
        "/api/user",
        "/api/database",
        "/api/health",
        "/api/info",
        "/api/echo",
        "/api/setting",
        "/api/public",
        "/api/explore-session",
        "/api/request-context",
        "/api/screen-templates",
        "/api/screen-plugins",
        "/api/screen-packs",
        "/api/screen-compliance",
        "/api/screens",
        "/actuator",
        "/auth"
    );

    private final PlatformPermissionClient permissionClient;

    public PlatformPermissionFilter(PlatformPermissionClient permissionClient) {
        this.permissionClient = permissionClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        // Skip non-API paths
        if (!path.startsWith("/api/")) {
            return true;
        }
        // Skip known non-asset paths
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        // Only filter if we can resolve an asset from the URL
        return resolveAsset(path) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = request.getHeader(HEADER_USER);
        if (username == null || username.isBlank()) {
            // No user context — let other filters handle auth
            chain.doFilter(request, response);
            return;
        }

        String roles = request.getHeader(HEADER_ROLES);
        String deptCode = request.getHeader(HEADER_DEPT);
        String path = request.getRequestURI();
        AssetRef asset = resolveAsset(path);

        if (asset == null) {
            chain.doFilter(request, response);
            return;
        }

        String action = actionFor(request.getMethod(), path);
        PermissionResult result = "CARD".equals(asset.type) || "DASHBOARD".equals(asset.type)
            ? permissionClient.authorize(username, roles, deptCode, asset.type, asset.id, action)
            : permissionClient.check(username, roles, deptCode, asset.type, asset.id, null, null, action);
        if (!result.allowed()) {
            LOG.debug("Permission denied: user={} asset={}:{} reason={}", username, asset.type, asset.id, result.reason());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"No permission for this asset\"}");
            return;
        }

        // Store permission result for downstream use
        request.setAttribute("assetPermission", result);
        request.setAttribute("assetRef", asset);
        chain.doFilter(request, response);
    }

    static AssetRef resolveAsset(String path) {
        Matcher m;

        m = CARD_PATTERN.matcher(path);
        if (m.matches()) return new AssetRef("CARD", m.group(1));

        m = ANALYSIS_PATTERN.matcher(path);
        if (m.matches()) return new AssetRef("CARD", m.group(1));

        m = DASHBOARD_PATTERN.matcher(path);
        if (m.matches()) return new AssetRef("DASHBOARD", m.group(1));

        m = TABLE_PATTERN.matcher(path);
        if (m.matches()) return new AssetRef("TABLE", m.group(1));

        return null;
    }

    static String actionFor(String method, String path) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return "READ";
        }
        if (path.endsWith("/favorite")) {
            return "READ";
        }
        if (path.endsWith("/publish") || path.endsWith("/registration/retry")) {
            return "MANAGE";
        }
        if (
            "POST".equals(method) &&
            (path.endsWith("/query/csv") || path.endsWith("/query/xlsx") || path.endsWith("/query/json"))
        ) {
            return "EXPORT";
        }
        if ("POST".equals(method)
                && (path.contains("/query") || path.contains("/execute/") || path.endsWith("/copy"))) {
            return "READ";
        }
        if ("DELETE".equals(method)
                || path.endsWith("/public_link")
                || path.endsWith("/persist")
                || path.endsWith("/unpersist")
                || path.endsWith("/refresh")) {
            return "MANAGE";
        }
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            return "EDIT";
        }
        return "READ";
    }

    record AssetRef(String type, String id) {}
}
