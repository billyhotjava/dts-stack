package com.yuzhi.dts.platform.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.data-standard")
public class DataStandardProperties {

    private String encryptionKey;
    private String keyVersion = "v1";
    private final Attachment attachment = new Attachment();

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

    public Attachment getAttachment() {
        return attachment;
    }

    public static class Attachment {

        private long maxFileSize = 209_715_200L; // 200 MB
        private Set<String> allowedExtensions = new LinkedHashSet<>(
            Set.of("docx", "wps", "pdf", "xlsx", "xls", "md", "txt")
        );
        private String storageStrategy = "database";

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public Set<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(Set<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public String getStorageStrategy() {
            return storageStrategy;
        }

        public void setStorageStrategy(String storageStrategy) {
            this.storageStrategy = storageStrategy;
        }
    }
}
