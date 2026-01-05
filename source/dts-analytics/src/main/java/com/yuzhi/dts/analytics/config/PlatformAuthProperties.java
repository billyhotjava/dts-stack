package com.yuzhi.dts.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics.platform-auth", ignoreUnknownFields = false)
public record PlatformAuthProperties(
        boolean enabled,
        boolean requireForwardedHeaders,
        String emailDomain,
        List<String> superuserRoles) {

    public PlatformAuthProperties {
        if (emailDomain == null || emailDomain.isBlank()) {
            emailDomain = "platform.local";
        }
        if (superuserRoles == null || superuserRoles.isEmpty()) {
            superuserRoles = List.of("ROLE_OP_ADMIN");
        }
    }
}

