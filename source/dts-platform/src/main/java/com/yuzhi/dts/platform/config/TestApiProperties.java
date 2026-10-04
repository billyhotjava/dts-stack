package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Test-only assist API configuration. Must remain disabled in prod profile.
 */
@ConfigurationProperties(prefix = "app.test-api")
public class TestApiProperties {

    private boolean enabled = false;
    private String token = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
