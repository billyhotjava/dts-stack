package com.yuzhi.dts.admin.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Pairwise credentials accepted by narrow dts-admin internal APIs. */
@Component
@ConfigurationProperties(prefix = "dts.admin.inbound.service-auth")
public class AdminInboundServiceAuthProperties implements InitializingBean {

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

    @Override
    public void afterPropertiesSet() {
        Set<String> canonicalNames = new HashSet<>();
        Set<String> credentials = new HashSet<>();
        trustedServices.forEach((rawName, rawToken) -> {
            if (!StringUtils.hasText(rawToken)) {
                return;
            }
            String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
            String token = rawToken.trim();
            if (!name.matches("^dts-[a-z0-9-]+$")) {
                throw new IllegalStateException("Invalid trusted service name in dts-admin pairwise credential configuration");
            }
            if (!canonicalNames.add(name)) {
                throw new IllegalStateException("Duplicate trusted service name in dts-admin pairwise credential configuration");
            }
            if (token.length() < 32) {
                throw new IllegalStateException("Pairwise service credentials must be at least 32 characters");
            }
            if (!credentials.add(token)) {
                throw new IllegalStateException("Duplicate pairwise service credential is not allowed");
            }
        });
    }
}
