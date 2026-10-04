package com.yuzhi.dts.platform.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Pairwise credentials for services calling platform internal APIs. */
@ConfigurationProperties(prefix = "dts.platform.inbound.service-auth")
public class PlatformInboundServiceAuthProperties implements InitializingBean {

    /** 是否启用入站服务鉴权 filter,关闭时所有内部服务调用均匿名。 */
    private boolean enabled = true;

    /** Each configured service has one independent token; blank entries are fail-closed. */
    private Map<String, String> trustedServices = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getTrustedServices() {
        return trustedServices;
    }

    public void setTrustedServices(Map<String, String> trustedServices) {
        this.trustedServices = trustedServices == null ? new LinkedHashMap<>() : trustedServices;
    }

    public boolean isTrustedServiceName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String normalized = candidate.trim();
        if (trustedServices != null) {
            for (String key : trustedServices.keySet()) {
                if (
                    StringUtils.hasText(key) &&
                    StringUtils.hasText(trustedServices.get(key)) &&
                    key.trim().equalsIgnoreCase(normalized)
                ) {
                    return true;
                }
            }
        }
        return false;
    }

    public String resolveExpectedToken(String serviceName) {
        if (!StringUtils.hasText(serviceName)) {
            return null;
        }
        String normalized = serviceName.trim();
        if (trustedServices != null) {
            for (Map.Entry<String, String> entry : trustedServices.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && entry.getKey().trim().equalsIgnoreCase(normalized)) {
                    String value = entry.getValue();
                    return StringUtils.hasText(value) ? value.trim() : null;
                }
            }
        }
        return null;
    }

    /**
     * 返回与给定 serviceName 等价的标准白名单条目(保留原始大小写,便于日志输出)。
     */
    public String canonicalServiceName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = candidate.trim();
        if (trustedServices != null) {
            for (String key : trustedServices.keySet()) {
                if (StringUtils.hasText(key) && key.trim().equalsIgnoreCase(normalized)) {
                    return key.trim();
                }
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Override
    public void afterPropertiesSet() {
        if (trustedServices == null || trustedServices.isEmpty()) {
            trustedServices = new LinkedHashMap<>();
            return;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> tokens = new HashSet<>();
        trustedServices.forEach((rawName, rawToken) -> {
            if (!StringUtils.hasText(rawToken)) {
                return;
            }
            String name = StringUtils.hasText(rawName) ? rawName.trim().toLowerCase(Locale.ROOT) : "";
            String token = rawToken.trim();
            if (!name.matches("[a-z0-9][a-z0-9.-]{1,63}")) {
                throw new IllegalStateException("Invalid pairwise service name");
            }
            if (token.length() < 32) {
                throw new IllegalStateException("Pairwise service credential must contain at least 32 characters");
            }
            if (normalized.putIfAbsent(name, token) != null) {
                throw new IllegalStateException("Duplicate pairwise service name is not allowed");
            }
            if (!tokens.add(token)) {
                throw new IllegalStateException("Duplicate pairwise service credential is not allowed");
            }
        });
        trustedServices = normalized;
    }
}
