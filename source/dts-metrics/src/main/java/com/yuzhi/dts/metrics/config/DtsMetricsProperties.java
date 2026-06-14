package com.yuzhi.dts.metrics.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dts.metrics")
public class DtsMetricsProperties {

    @NotBlank
    private String edition = "foundation";

    @NotBlank
    private String serviceName = "dts-metrics";

    private final Platform platform = new Platform();

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Platform getPlatform() {
        return platform;
    }

    public static class Platform {

        @NotBlank
        private String baseUrl = "http://dts-platform:8081";

        @NotBlank
        private String apiPath = "/api";

        private String serviceToken = "";

        /**
         * Connect timeout (ms) for platform internal contract calls. Fail-closed: a timeout surfaces as
         * PlatformContractException -> 503, never an unbounded hang. (Sprint-35b F4-T01)
         */
        private int connectTimeoutMs = 2000;

        /** Read timeout (ms) for platform internal contract calls. */
        private int readTimeoutMs = 5000;

        /**
         * Emit platform audit events for high-risk lifecycle actions (F2-T04). The receiver
         * {@code /internal/audit-events} is owned by dts-platform; until that endpoint is live (F3-T03
         * 联调依赖) this stays disabled so the metrics side does not block on a 404.
         */
        private boolean auditEventsEnabled = false;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiPath() {
            return apiPath;
        }

        public void setApiPath(String apiPath) {
            this.apiPath = apiPath;
        }

        public String getServiceToken() {
            return serviceToken;
        }

        public void setServiceToken(String serviceToken) {
            this.serviceToken = serviceToken;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public boolean isAuditEventsEnabled() {
            return auditEventsEnabled;
        }

        public void setAuditEventsEnabled(boolean auditEventsEnabled) {
            this.auditEventsEnabled = auditEventsEnabled;
        }
    }
}
