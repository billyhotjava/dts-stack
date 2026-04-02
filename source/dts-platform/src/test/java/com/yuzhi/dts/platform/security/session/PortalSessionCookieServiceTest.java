package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PortalSessionCookieServiceTest {

    @Test
    void resolvePortalSessionTokenReadsSanitizedCookieValue() {
        PortalSessionCookieService service = new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("portal_session", "demo-token"));

        assertThat(service.resolvePortalSessionToken(request)).isEqualTo("demo-token");
    }

    @Test
    void buildPortalSessionCookieIssuesHttpOnlySessionCookie() {
        PortalSessionCookieService service = new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax");

        String cookie = service.buildPortalSessionCookie("demo-token").toString();

        assertThat(cookie).contains("portal_session=demo-token");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Path=/");
        assertThat(cookie).contains("SameSite=Lax");
    }

    @Test
    void clearPortalSessionCookieExpiresCookieImmediately() {
        PortalSessionCookieService service = new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax");

        String cookie = service.clearPortalSessionCookie().toString();

        assertThat(cookie).contains("portal_session=");
        assertThat(cookie).contains("Max-Age=0");
    }
}
