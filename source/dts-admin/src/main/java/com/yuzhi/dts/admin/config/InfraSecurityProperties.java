package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.admin.infra")
public class InfraSecurityProperties {

    /**
     * Base64 encoded AES key used to encrypt persisted secrets. Optional; when absent secrets are stored in plaintext.
     */
    private String encryptionKey;

    /**
     * Logical version for the encryption key, defaults to v1.
     */
    private String keyVersion = "v1";

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
