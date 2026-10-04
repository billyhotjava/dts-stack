package com.yuzhi.dts.copilot.analytics.web.ui;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class ExternalPathResolver {

    private ExternalPathResolver() {}

    static String resolveForwardedPrefix(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-Prefix"))
                .map(h -> h.split(",")[0].trim())
                .filter(StringUtils::hasText)
                .orElse("");
    }

    static String resolveBaseHref(HttpServletRequest request) {
        String prefix = resolveForwardedPrefix(request);
        if (!StringUtils.hasText(prefix) || "/".equals(prefix)) {
            return "/";
        }
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    static String resolveExternalHost(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (StringUtils.hasText(forwardedHost)) {
            return forwardedHost.split(",")[0].trim();
        }
        return request.getServerName();
    }

    static String resolveExternalScheme(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            return forwardedProto.split(",")[0].trim();
        }
        return request.getScheme();
    }
}

