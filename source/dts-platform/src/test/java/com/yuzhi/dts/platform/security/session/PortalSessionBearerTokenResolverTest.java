package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class PortalSessionBearerTokenResolverTest {

    @Test
    void prefersPortalSessionCookieOverAuthorizationHeaderForBrowserApiRequests() {
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        PortalSessionBearerTokenResolver resolver = new PortalSessionBearerTokenResolver(cookieService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/catalog/datasets");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");
        when(cookieService.resolvePortalSessionToken(request)).thenReturn("cookie-token");

        assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
        verify(cookieService).resolvePortalSessionToken(request);
    }

    @Test
    void usesPortalSessionCookieOnlyForForwardAuthProbe() {
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        PortalSessionBearerTokenResolver resolver = new PortalSessionBearerTokenResolver(cookieService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/forward-auth");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");
        when(cookieService.resolvePortalSessionToken(request)).thenReturn("cookie-token");

        assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
        verify(cookieService).resolvePortalSessionToken(request);
    }

    @Test
    void fallsBackToPortalSessionCookieWhenHeaderMissing() {
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        PortalSessionBearerTokenResolver resolver = new PortalSessionBearerTokenResolver(cookieService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(cookieService.resolvePortalSessionToken(request)).thenReturn("cookie-token");

        assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
        verify(cookieService).resolvePortalSessionToken(request);
    }

    @Test
    void ignoresAuthorizationHeaderWhenPortalSessionCookieMissingForBrowserApiRequests() {
        PortalSessionCookieService cookieService = mock(PortalSessionCookieService.class);
        PortalSessionBearerTokenResolver resolver = new PortalSessionBearerTokenResolver(cookieService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/catalog/datasets");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer stale-header-token");
        when(cookieService.resolvePortalSessionToken(request)).thenReturn(null);

        assertThat(resolver.resolve(request)).isNull();
        verify(cookieService).resolvePortalSessionToken(request);
    }
}
