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
        if (shouldUseCookieOnly(request)) {
            return cookieService.resolvePortalSessionToken(request);
        }
        try {
            String headerToken = delegate.resolve(request);
            if (StringUtils.hasText(headerToken)) {
                return headerToken;
            }
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        }
        return cookieService.resolvePortalSessionToken(request);
    }

    private boolean shouldBypassAuthentication(HttpServletRequest request) {
        String uri = request == null ? "" : String.valueOf(request.getRequestURI());
        return uri.startsWith("/api/session/current");
    }

    private boolean shouldUseCookieOnly(HttpServletRequest request) {
        String uri = request == null ? "" : String.valueOf(request.getRequestURI());
        return uri.startsWith("/api/forward-auth");
    }
}
