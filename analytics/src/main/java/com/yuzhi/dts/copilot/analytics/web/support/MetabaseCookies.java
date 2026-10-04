package com.yuzhi.dts.copilot.analytics.web.support;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseCookie;

public final class MetabaseCookies {

    private static final Duration TIMEOUT_TTL = Duration.ofDays(14);
    private static final Duration DEVICE_TTL = Duration.ofDays(365 * 20L);

    private MetabaseCookies() {}

    public static List<String> deviceCookieHeaders(UUID deviceId, boolean secure) {
        ResponseCookie deviceCookie = ResponseCookie.from("metabase.DEVICE", deviceId.toString())
                .path("/")
                .httpOnly(true)
                .maxAge(DEVICE_TTL)
                .sameSite("Lax")
                .secure(secure)
                .build();
        return List.of(deviceCookie.toString());
    }

    public static List<String> loginCookieHeaders(UUID sessionId, UUID deviceId, boolean secure) {
        ResponseCookie timeoutCookie = ResponseCookie.from("metabase.TIMEOUT", "alive")
                .path("/")
                .maxAge(TIMEOUT_TTL)
                .sameSite("Lax")
                .secure(secure)
                .build();
        ResponseCookie sessionCookie = ResponseCookie.from("metabase.SESSION", sessionId.toString())
                .path("/")
                .httpOnly(true)
                .sameSite("Lax")
                .secure(secure)
                .build();
        ResponseCookie deviceCookie = ResponseCookie.from("metabase.DEVICE", deviceId.toString())
                .path("/")
                .httpOnly(true)
                .maxAge(DEVICE_TTL)
                .sameSite("Lax")
                .secure(secure)
                .build();
        return List.of(timeoutCookie.toString(), sessionCookie.toString(), deviceCookie.toString());
    }

    public static List<String> logoutCookieHeaders(boolean secure) {
        ResponseCookie timeoutCookie = ResponseCookie.from("metabase.TIMEOUT", "dead")
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .secure(secure)
                .build();
        ResponseCookie sessionCookie = ResponseCookie.from("metabase.SESSION", "")
                .path("/")
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .secure(secure)
                .build();
        return List.of(timeoutCookie.toString(), sessionCookie.toString());
    }
}

