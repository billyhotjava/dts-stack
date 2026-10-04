package com.yuzhi.dts.copilot.analytics.web.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * Metabase v0.58.x expects locale keys like {@code zh} / {@code en} and will crash on unknown values
 * when resolving i18n bundles.
 */
public final class MetabaseLocale {

    public static final String ZH = "zh";
    public static final String EN = "en";

    private MetabaseLocale() {}

    public static String resolve(HttpServletRequest request) {
        String cookie = cookieValue(request, "dts_lang");
        if (EN.equalsIgnoreCase(cookie)) {
            return EN;
        }
        if (ZH.equalsIgnoreCase(cookie)) {
            return ZH;
        }

        String accept = request.getHeader("Accept-Language");
        if (accept != null) {
            String normalized = accept.toLowerCase(Locale.ROOT);
            if (normalized.contains("zh")) {
                return ZH;
            }
            if (normalized.contains("en")) {
                return EN;
            }
        }

        return ZH;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null) {
                return c.getValue().trim();
            }
        }
        return "";
    }
}
