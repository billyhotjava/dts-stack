package com.yuzhi.dts.admin.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

@ConfigurationProperties(prefix = "dts.personnel")
public class PersonnelSyncProperties {

    private final Api api = new Api();
    private final Excel excel = new Excel();
    private final Logging logging = new Logging();

    public Api getApi() {
        return api;
    }

    public Excel getExcel() {
        return excel;
    }

    public Logging getLogging() {
        return logging;
    }

    public static class Api {

        private boolean enabled = false;
        private String endpoint = "";
        private HttpMethod method = HttpMethod.GET;
        private Map<String, String> headers = new LinkedHashMap<>();
        private String authToken = "";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public HttpMethod getMethod() {
            return method;
        }

        public void setMethod(HttpMethod method) {
            this.method = method;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Excel {

        private boolean enabled = true;
        private int maxRows = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }
    }

    public static class Logging {

        private String operationsLog = "../../logs/dts-admin/personnel-operations.log";
        private long maxSizeBytes = 100L * 1024 * 1024;

        public String getOperationsLog() {
            return operationsLog;
        }

        public void setOperationsLog(String operationsLog) {
            this.operationsLog = operationsLog;
        }

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }
    }
}
