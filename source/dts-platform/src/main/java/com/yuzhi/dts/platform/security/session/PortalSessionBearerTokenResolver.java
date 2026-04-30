package com.yuzhi.dts.platform.security.session;

import com.yuzhi.dts.common.security.AuthEndpointPaths;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.StringUtils;

/**
 * Resolves the platform opaque token from the HttpOnly portal session cookie first.
 *
 * <p>Browser platform APIs are cookie-only. This avoids stale localStorage
 * Authorization headers overriding a valid server-side session.
 */
public class PortalSessionBearerTokenResolver implements BearerTokenResolver {

    private final PortalSessionCookieService cookieService;

    public PortalSessionBearerTokenResolver(PortalSessionCookieService cookieService) {
        this.cookieService = cookieService;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (isExplicitlyUnauthenticatedEndpoint(request)) {
            return null;
        }
        String cookieToken = cookieService == null ? null : cookieService.resolvePortalSessionToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        return null;
    }

    private boolean isExplicitlyUnauthenticatedEndpoint(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return (
            AuthEndpointPaths.isKeycloakAuthEndpoint(uri) ||
            AuthEndpointPaths.isKeycloakLocalizationEndpoint(uri) ||
            AuthEndpointPaths.isPortalSessionStatusEndpoint(uri)
        );
    }
}
