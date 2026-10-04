package com.yuzhi.dts.copilot.analytics.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.analytics.platform-indicators")
public record PlatformIndicatorProperties(
        String baseUrl,
        String tokenUrl,
        String clientId,
        String clientSecret,
        String authToken,
        String serviceName,
        String serviceToken,
        int timeoutSeconds,
        int valueCacheTtlSeconds) {

    public PlatformIndicatorProperties {
        baseUrl = normalize(baseUrl);
        tokenUrl = normalize(tokenUrl);
        clientId = normalize(clientId);
        clientSecret = normalize(clientSecret);
        authToken = normalize(authToken);
        serviceName = normalize(serviceName);
        serviceToken = normalize(serviceToken);
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 5;
        }
        if (valueCacheTtlSeconds < 0) {
            valueCacheTtlSeconds = 0;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
