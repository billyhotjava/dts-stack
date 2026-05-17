package com.yuzhi.dts.platform.security;

import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects an authenticated service principal when trusted services call platform APIs.
 * <p>
 * Sprint-28 F3: 启用 token 强校验,关闭 Sprint-27 的"白名单即权限"越权面。
 * 注入逻辑:
 * <ol>
 *   <li>X-DTS-Service header 必须在 trustedServices Map keys 或 trustedServiceNames List 内,否则拒绝;</li>
 *   <li>X-DTS-Service-Token header 必须存在,且与 trustedServices.get(serviceName) 或 sharedSecret 完全相等,
 *       或者通过 {@link SvcTokenAuthService#authenticateService} 命中数据库中动态颁发的 svc_token,缺一不注入。</li>
 * </ol>
 * <p>
 * 兼容开关 {@code legacy-header-only-mode} 开启时退回 Sprint-27 行为(仅看 header)。production 严禁开启。
 * 认证通过后只授予服务专用 authority,具体端点再按 service principal 收敛授权。
 */
public class ServiceDependencyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceDependencyAuthenticationFilter.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final PlatformInboundServiceAuthProperties authProperties;
    private final SvcTokenAuthService svcTokenAuthService;

    public ServiceDependencyAuthenticationFilter(PlatformInboundServiceAuthProperties authProperties) {
        this(authProperties, null);
    }

    public ServiceDependencyAuthenticationFilter(
        PlatformInboundServiceAuthProperties authProperties,
        SvcTokenAuthService svcTokenAuthService
    ) {
        this.authProperties = authProperties;
        this.svcTokenAuthService = svcTokenAuthService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            String serviceName = resolveServiceName(request);
            if (serviceName != null) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "service:" + serviceName,
                    null,
                    List.of(new SimpleGrantedAuthority(AuthoritiesConstants.SERVICE_INTERNAL))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated internal service call as {}", serviceName);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveServiceName(HttpServletRequest request) {
        if (authProperties == null || !authProperties.isEnabled()) {
            return null;
        }
        String declared = request.getHeader(SERVICE_HEADER);
        if (!StringUtils.hasText(declared)) {
            return null;
        }
        if (!authProperties.isTrustedServiceName(declared)) {
            log.warn("event=service_auth_denied service={} reason=service_unknown", declared.trim());
            return null;
        }
        String canonical = authProperties.canonicalServiceName(declared);
        if (!isServicePathAllowed(canonical, request)) {
            log.warn(
                "event=service_auth_denied service={} method={} path={} reason=endpoint_not_allowed",
                canonical,
                request.getMethod(),
                request.getRequestURI()
            );
            return null;
        }

        if (authProperties.isLegacyHeaderOnlyMode()) {
            // Sprint-27 兼容路径,仅 header 即放行。production 严禁开启。
            return canonical;
        }

        String suppliedToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(suppliedToken)) {
            log.warn("event=service_auth_denied service={} reason=token_missing", canonical);
            return null;
        }
        String trimmedToken = suppliedToken.trim();
        String expected = authProperties.resolveExpectedToken(canonical);
        if (StringUtils.hasText(expected) && expected.trim().equals(trimmedToken)) {
            return canonical;
        }
        if (svcTokenAuthService != null && svcTokenAuthService.authenticateService(trimmedToken, canonical) != null) {
            return canonical;
        }
        log.warn("event=service_auth_denied service={} reason=token_mismatch", canonical);
        return null;
    }

    private boolean isServicePathAllowed(String serviceName, HttpServletRequest request) {
        if (!StringUtils.hasText(serviceName) || request == null) {
            return false;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        String service = serviceName.trim().toLowerCase(java.util.Locale.ROOT);
        if ("dts-ingestion".equals(service)) {
            return isGetRuntimeDetail(method, path)
                || isPost(method, path, "/api/etl/dbt/run")
                || isPost(method, path, "/api/governance/quality/pre-check")
                || isPost(method, path, "/api/governance/quality/runs")
                || isPost(method, path, "/api/catalog/lineage/ingestion-executions");
        }
        if ("dts-analytics".equals(service)) {
            return isGet(method, path, "/api/infra/data-sources")
                || isGetInfraDataSourceDetail(method, path)
                || isGetRuntimeDetail(method, path)
                || isAnalyticsAssetPermission(method, path);
        }
        if ("dts-metrics".equals(service)) {
            return isGet(method, path, "/api/internal/capabilities")
                || isMetricsReferenceResolve(method, path)
                || isMetricsAssetPermission(method, path)
                || isMetricsCatalogRead(method, path)
                || isMetricsLineageDryRun(method, path)
                || isMetricsDbtPublishGateway(method, path);
        }
        return false;
    }

    private boolean isAnalyticsAssetPermission(String method, String path) {
        return isPost(method, path, "/api/internal/asset-permission/check")
            || isPost(method, path, "/api/internal/asset-permission/batch-check")
            || isPost(method, path, "/api/internal/asset-permission/accessible-ids")
            || isGet(method, path, "/api/internal/asset-permission/grants")
            || isPost(method, path, "/api/internal/asset-permission/grants")
            || isDelete(method, path, "/api/internal/asset-permission/grants/by-asset")
            || isDeleteAnalyticsGrant(method, path);
    }

    private boolean isMetricsAssetPermission(String method, String path) {
        return isPost(method, path, "/api/internal/asset-permission/check")
            || isPost(method, path, "/api/internal/v1/asset-permission/policy")
            || isPost(method, path, "/api/internal/asset-permission/policy")
            || isPost(method, path, "/api/internal/asset-permission/batch-check")
            || isPost(method, path, "/api/internal/asset-permission/accessible-ids")
            || isGet(method, path, "/api/internal/asset-permission/grants")
            || isGet(method, path, "/api/internal/v1/asset-permission/audit/denied")
            || isGet(method, path, "/api/internal/v1/asset-permission/audit/denied.csv");
    }

    private boolean isMetricsCatalogRead(String method, String path) {
        if (!HttpMethod.GET.matches(method) || path == null) {
            return false;
        }
        if ("/api/catalog/assets-v2".equals(path)) {
            return true;
        }
        return path.startsWith("/api/catalog/assets-v2/")
            && (path.endsWith("/contract") || path.endsWith("/schema-contract"));
    }

    private boolean isMetricsReferenceResolve(String method, String path) {
        return isPost(method, path, "/api/internal/glossary/terms/resolve")
            || isPost(method, path, "/api/internal/domains/resolve")
            || isPost(method, path, "/api/internal/data-standards/resolve");
    }

    private boolean isMetricsLineageDryRun(String method, String path) {
        return isGet(method, path, "/api/internal/v1/lineage/backfill/dry-run");
    }

    private boolean isMetricsDbtPublishGateway(String method, String path) {
        return isPost(method, path, "/api/etl/dbt/release-gate/check")
            || isPost(method, path, "/api/etl/dbt/release/submit");
    }

    private boolean isDeleteAnalyticsGrant(String method, String path) {
        return HttpMethod.DELETE.matches(method)
            && path != null
            && path.startsWith("/api/internal/asset-permission/grants/")
            && !path.endsWith("/by-asset");
    }

    private boolean isGetRuntimeDetail(String method, String path) {
        return HttpMethod.GET.matches(method)
            && path != null
            && path.startsWith("/api/infra/data-sources/")
            && path.endsWith("/runtime-detail");
    }

    private boolean isGetInfraDataSourceDetail(String method, String path) {
        return HttpMethod.GET.matches(method)
            && path != null
            && path.startsWith("/api/infra/data-sources/")
            && path.endsWith("/detail");
    }

    private boolean isGet(String method, String path, String expectedPath) {
        return HttpMethod.GET.matches(method) && expectedPath.equals(path);
    }

    private boolean isPost(String method, String path, String expectedPath) {
        return HttpMethod.POST.matches(method) && expectedPath.equals(path);
    }

    private boolean isDelete(String method, String path, String expectedPath) {
        return HttpMethod.DELETE.matches(method) && expectedPath.equals(path);
    }
}
