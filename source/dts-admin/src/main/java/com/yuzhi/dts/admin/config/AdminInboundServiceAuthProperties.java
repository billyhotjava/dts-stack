package com.yuzhi.dts.admin.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Pairwise credentials accepted by narrow dts-admin internal APIs. */
@Component
@ConfigurationProperties(prefix = "dts.admin.inbound.service-auth")
public class AdminInboundServiceAuthProperties {

    private Map<String, String> trustedServices = new LinkedHashMap<>();

    public Map<String, String> getTrustedServices() {
        return trustedServices;
    }

    public void setTrustedServices(Map<String, String> trustedServices) {
        this.trustedServices = trustedServices == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(trustedServices);
    }

    public String canonicalServiceName(String candidate) {
        if (!StringUtils.hasText(candidate)) return null;
        for (String configured : trustedServices.keySet()) {
            if (
                StringUtils.hasText(configured) &&
                configured.trim().equalsIgnoreCase(candidate.trim())
            ) {
                return configured.trim();
            }
        }
        return null;
    }

    public String expectedToken(String serviceName) {
        String canonical = canonicalServiceName(serviceName);
        if (canonical == null) return null;
        String token = trustedServices.get(canonical);
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
