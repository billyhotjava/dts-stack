package com.yuzhi.dts.platform.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.governance")
public class GovernanceProperties {

    private final Quality quality = new Quality();
    private final Compliance compliance = new Compliance();
    private final Issue issue = new Issue();
    private final Datasource datasource = new Datasource();

    public Quality getQuality() {
        return quality;
    }

    public Compliance getCompliance() {
        return compliance;
    }

    public Issue getIssue() {
        return issue;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public static class Quality {

        private boolean enabled = true;
        private String defaultExecutor = "hive";
        private int maxConcurrent = 5;
        private int retryCount = 1;
        private Duration timeout = Duration.ofMinutes(10);
        private boolean autoGenerateTicket = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultExecutor() {
            return defaultExecutor;
        }

        public void setDefaultExecutor(String defaultExecutor) {
            this.defaultExecutor = defaultExecutor;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isAutoGenerateTicket() {
            return autoGenerateTicket;
        }

        public void setAutoGenerateTicket(boolean autoGenerateTicket) {
            this.autoGenerateTicket = autoGenerateTicket;
        }
    }

    public static class Compliance {

        private boolean enabled = true;
        private boolean evidenceRequiredDefault = true;
        private boolean autoTicket = true;
        private Duration sla = Duration.ofDays(7);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEvidenceRequiredDefault() {
            return evidenceRequiredDefault;
        }

        public void setEvidenceRequiredDefault(boolean evidenceRequiredDefault) {
            this.evidenceRequiredDefault = evidenceRequiredDefault;
        }

        public boolean isAutoTicket() {
            return autoTicket;
        }

        public void setAutoTicket(boolean autoTicket) {
            this.autoTicket = autoTicket;
        }

        public Duration getSla() {
            return sla;
        }

        public void setSla(Duration sla) {
            this.sla = sla;
        }
    }

    public static class Issue {

        private String defaultAssignee;
        private String defaultPriority = "MEDIUM";
        private Duration reminderInterval = Duration.ofDays(3);
        private Duration autoCloseAfter = Duration.ofDays(30);

        public String getDefaultAssignee() {
            return defaultAssignee;
        }

        public void setDefaultAssignee(String defaultAssignee) {
            this.defaultAssignee = defaultAssignee;
        }

        public String getDefaultPriority() {
            return defaultPriority;
        }

        public void setDefaultPriority(String defaultPriority) {
            this.defaultPriority = defaultPriority;
        }

        public Duration getReminderInterval() {
            return reminderInterval;
        }

        public void setReminderInterval(Duration reminderInterval) {
            this.reminderInterval = reminderInterval;
        }

        public Duration getAutoCloseAfter() {
            return autoCloseAfter;
        }

        public void setAutoCloseAfter(Duration autoCloseAfter) {
            this.autoCloseAfter = autoCloseAfter;
        }
    }

    public static class Datasource {

        private String primary = "foundation-hive";
        private boolean allowMultiple = false;
        private Set<String> optionalSources = new LinkedHashSet<>();

        public String getPrimary() {
            return primary;
        }

        public void setPrimary(String primary) {
            this.primary = primary;
        }

        public boolean isAllowMultiple() {
            return allowMultiple;
        }

        public void setAllowMultiple(boolean allowMultiple) {
            this.allowMultiple = allowMultiple;
        }

        public Set<String> getOptionalSources() {
            return optionalSources;
        }

        public void setOptionalSources(Set<String> optionalSources) {
            this.optionalSources = optionalSources;
        }
    }
}
