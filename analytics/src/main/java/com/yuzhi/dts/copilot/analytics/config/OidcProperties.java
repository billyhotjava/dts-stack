package com.yuzhi.dts.copilot.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics.oidc", ignoreUnknownFields = false)
public record OidcProperties(
        boolean enabled,
        String issuerUri,
        String clientId,
        String clientSecret,
        List<String> scopes,
        String adminRole) {

    public OidcProperties {
        if (scopes == null || scopes.isEmpty()) {
            scopes = List.of("openid", "profile", "email");
        }
        if (adminRole == null || adminRole.isBlank()) {
            adminRole = "analytics-admin";
        }
    }
}
