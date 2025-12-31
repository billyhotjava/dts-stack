package com.yuzhi.dts.analytics.config;

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

    public OidcProperties() {
        this(false, null, null, null, List.of("openid", "profile", "email"), "analytics-admin");
    }
}

