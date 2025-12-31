package com.yuzhi.dts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public record ApplicationProperties(Service service) {

    public ApplicationProperties() {
        this(new Service(null, null, null));
    }

    public record Service(String name, String version, String environment) {}
}
