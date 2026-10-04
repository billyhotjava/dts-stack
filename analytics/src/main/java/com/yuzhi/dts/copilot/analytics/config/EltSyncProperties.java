package com.yuzhi.dts.copilot.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.elt")
public class EltSyncProperties {

    private boolean enabled = false;
    private String cron = "0 0 * * * *";
    private int batchSize = 1000;
    private BusinessDb businessDb = new BusinessDb();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public BusinessDb getBusinessDb() {
        return businessDb;
    }

    public void setBusinessDb(BusinessDb businessDb) {
        this.businessDb = businessDb;
    }

    public static class BusinessDb {

        private String url;
        private String username;
        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
