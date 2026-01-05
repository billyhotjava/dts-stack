package com.yuzhi.dts.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics.platform-auth", ignoreUnknownFields = false)
public record PlatformAuthProperties(
        boolean enabled,
        boolean requireForwardedHeaders,
        String emailDomain,
        List<String> superuserRoles,
        Boolean allowBearerFallback,
        String forwardAuthUrl,
        int forwardAuthTimeoutMs) {

    public PlatformAuthProperties {
        if (emailDomain == null || emailDomain.isBlank()) {
            emailDomain = "platform.local";
        }
        if (superuserRoles == null || superuserRoles.isEmpty()) {
            superuserRoles = List.of("ROLE_OP_ADMIN");
        }
        if (allowBearerFallback == null) {
            allowBearerFallback = Boolean.TRUE;
        }
        if (forwardAuthUrl == null || forwardAuthUrl.isBlank()) {
            forwardAuthUrl = "http://dts-platform:8081/api/forward-auth";
        }
        if (forwardAuthTimeoutMs <= 0) {
            forwardAuthTimeoutMs = 2000;
        }
    }
}
