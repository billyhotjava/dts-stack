package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.infra")
public class InfraSecurityProperties {

    private boolean multiSourceEnabled = false;
    private String encryptionKey;
    private String keyVersion = "v1";

    public boolean isMultiSourceEnabled() {
        return multiSourceEnabled;
    }

    public void setMultiSourceEnabled(boolean multiSourceEnabled) {
        this.multiSourceEnabled = multiSourceEnabled;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(String keyVersion) {
        this.keyVersion = keyVersion;
    }
}
