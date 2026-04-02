package com.yuzhi.dts.platform.security.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortalSessionCookieService {

    private static final Logger log = LoggerFactory.getLogger(PortalSessionCookieService.class);

    private final String browserCookieName;
    private final String portalSessionCookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String sameSite;
    private final byte[] browserCookieSigningSecret;

    public PortalSessionCookieService(
        @Value("${dts.platform.session.browser-cookie-name:browser_id}") String browserCookieName,
        @Value("${dts.platform.session.portal-cookie-name:portal_session}") String portalSessionCookieName,
        @Value("${dts.platform.session.cookie-path:/}") String cookiePath,
        @Value("${dts.platform.session.cookie-secure:true}") boolean cookieSecure,
        @Value("${dts.platform.session.cookie-same-site:Lax}") String sameSite,
        @Value("${dts.platform.session.browser-cookie-signing-secret:${OAUTH2_PLATFORM_CLIENT_SECRET:}}") String browserCookieSigningSecret
    ) {
        this.browserCookieName = StringUtils.hasText(browserCookieName) ? browserCookieName.trim() : "browser_id";
        this.portalSessionCookieName =
            StringUtils.hasText(portalSessionCookieName) ? portalSessionCookieName.trim() : "portal_session";
        this.cookiePath = StringUtils.hasText(cookiePath) ? cookiePath.trim() : "/";
        this.cookieSecure = cookieSecure;
        this.sameSite = StringUtils.hasText(sameSite) ? sameSite.trim() : "Lax";
        String signingSecret = StringUtils.hasText(browserCookieSigningSecret) ? browserCookieSigningSecret.trim() : null;
        if (!StringUtils.hasText(signingSecret)) {
            throw new IllegalStateException("browser_cookie_signing_secret_missing");
        }
        this.browserCookieSigningSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String resolveBrowserId(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null && browserCookieName.equals(cookie.getName())) {
                    String value = sanitize(cookie.getValue(), 256);
                    if (value != null) {
                        String browserId = verifySignedBrowserId(value);
                        if (browserId != null) {
                            return browserId;
                        }
                        log.warn("Ignoring invalid browser_id cookie");
                    }
                }
            }
        }
        return "browser-" + UUID.randomUUID();
    }

    public String resolvePortalSessionToken(HttpServletRequest request) {
        return resolveCookieValue(request, portalSessionCookieName, 512);
    }

    public ResponseCookie buildBrowserIdCookie(String browserId) {
        String value = sanitize(browserId, 128);
        if (value == null) {
            value = "browser-" + UUID.randomUUID();
        }
        String signedValue = signBrowserId(value);
        return ResponseCookie
            .from(browserCookieName, signedValue)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(Duration.ofDays(365))
            .build();
    }

    public ResponseCookie buildPortalSessionCookie(String accessToken) {
        String value = sanitize(accessToken, 512);
        if (value == null) {
            throw new IllegalArgumentException("portal_session_token_invalid");
        }
        return ResponseCookie
            .from(portalSessionCookieName, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(sameSite)
            .path(cookiePath)
            .build();
    }

    public ResponseCookie clearPortalSessionCookie() {
        return ResponseCookie
            .from(portalSessionCookieName, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(sameSite)
            .path(cookiePath)
            .maxAge(Duration.ZERO)
            .build();
    }

    private String resolveCookieValue(HttpServletRequest request, String cookieName, int maxLength) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie != null && cookieName.equals(cookie.getName())) {
                    String value = sanitize(cookie.getValue(), maxLength);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String verifySignedBrowserId(String cookieValue) {
        int separator = cookieValue.lastIndexOf('.');
        if (separator <= 0 || separator >= cookieValue.length() - 1) {
            return null;
        }
        String browserId = sanitize(cookieValue.substring(0, separator), 128);
        String signature = sanitize(cookieValue.substring(separator + 1), 128);
        if (browserId == null || signature == null) {
            return null;
        }
        String expectedSignature = computeSignature(browserId);
        byte[] actualBytes = signature.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(actualBytes, expectedBytes) ? browserId : null;
    }

    private String signBrowserId(String browserId) {
        return browserId + "." + computeSignature(browserId);
    }

    private String computeSignature(String browserId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(browserCookieSigningSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(browserId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("browser_cookie_signing_failed", ex);
        }
    }

    private String sanitize(String candidate, int maxLength) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String value = candidate.trim();
        if (value.length() > maxLength) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9')
                || ch == '-'
                || ch == '_'
                || ch == '.';
            if (!allowed) {
                return null;
            }
        }
        return value;
    }
}
