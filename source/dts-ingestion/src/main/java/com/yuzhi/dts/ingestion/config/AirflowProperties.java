package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.airflow")
public class AirflowProperties {

    private boolean enabled = true;
    private String baseUrl;
    private String apiPath = "/api/v1";
    private String username;
    private String password;
    private String dagId;
    private String dagsDir;
    private Integer dagReadyWaitSeconds = 60;
    private Integer dagReadyPollSeconds = 2;
    private boolean executionPollEnabled = true;
    private Long executionPollIntervalMs = 15000L;
    private Integer executionPollBatchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public String getDagId() {
        return dagId;
    }

    public void setDagId(String dagId) {
        this.dagId = dagId;
    }

    public String getDagsDir() {
        return dagsDir;
    }

    public void setDagsDir(String dagsDir) {
        this.dagsDir = dagsDir;
    }

    public Integer getDagReadyWaitSeconds() {
        return dagReadyWaitSeconds;
    }

    public void setDagReadyWaitSeconds(Integer dagReadyWaitSeconds) {
        this.dagReadyWaitSeconds = dagReadyWaitSeconds;
    }

    public Integer getDagReadyPollSeconds() {
        return dagReadyPollSeconds;
    }

    public void setDagReadyPollSeconds(Integer dagReadyPollSeconds) {
        this.dagReadyPollSeconds = dagReadyPollSeconds;
    }

    public boolean isExecutionPollEnabled() {
        return executionPollEnabled;
    }

    public void setExecutionPollEnabled(boolean executionPollEnabled) {
        this.executionPollEnabled = executionPollEnabled;
    }

    public Long getExecutionPollIntervalMs() {
        return executionPollIntervalMs;
    }

    public void setExecutionPollIntervalMs(Long executionPollIntervalMs) {
        this.executionPollIntervalMs = executionPollIntervalMs;
    }

    public Integer getExecutionPollBatchSize() {
        return executionPollBatchSize;
    }

    public void setExecutionPollBatchSize(Integer executionPollBatchSize) {
        this.executionPollBatchSize = executionPollBatchSize;
    }
}
