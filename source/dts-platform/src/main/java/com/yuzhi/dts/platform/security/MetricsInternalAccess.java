package com.yuzhi.dts.platform.security;

import com.yuzhi.dts.platform.config.metrics.DtsMetricsCapabilityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MetricsInternalAccess {

    private final DtsMetricsCapabilityProperties metricsProperties;

    public MetricsInternalAccess(DtsMetricsCapabilityProperties metricsProperties) {
        this.metricsProperties = metricsProperties;
    }

    public boolean isMetricsService(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String serviceName = StringUtils.hasText(metricsProperties.getServiceName())
            ? metricsProperties.getServiceName().trim()
            : "dts-metrics";
        return ("service:" + serviceName).equals(authentication.getName());
    }
}
