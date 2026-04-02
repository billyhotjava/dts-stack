package com.yuzhi.dts.platform.security.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortalSessionBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    private final PortalSessionCookieService cookieService;

    public PortalSessionBearerTokenResolver(PortalSessionCookieService cookieService) {
        this.cookieService = cookieService;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (shouldBypassAuthentication(request)) {
            return null;
        }
        String cookieToken = cookieService.resolvePortalSessionToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        try {
            String headerToken = delegate.resolve(request);
            if (StringUtils.hasText(headerToken)) {
                return headerToken;
            }
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        }
        return null;
    }

    private boolean shouldBypassAuthentication(HttpServletRequest request) {
        String uri = request == null ? "" : String.valueOf(request.getRequestURI());
        // All auth endpoints are permitAll() in SecurityConfiguration — don't resolve
        // a token for them. If the browser carries a stale portal_session cookie,
        // the introspector would reject it and turn the permitAll into a 401.
        return uri.startsWith("/api/session/current")
            || uri.startsWith("/api/keycloak/auth/")
            || uri.startsWith("/api/keycloak/localization/")
            || uri.startsWith("/api/authenticate")
            || uri.startsWith("/api/auth-info");
    }
}
