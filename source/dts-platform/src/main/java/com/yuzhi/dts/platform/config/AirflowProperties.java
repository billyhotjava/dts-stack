package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.airflow")
public class AirflowProperties {

    private boolean enabled = true;
    private String baseUrl;
    private String username;
    private String password;
    private String dagId = "dbt_load";
    private String dagsDir;
    private int dagReadyWaitSeconds = 30;
    private int dagReadyPollSeconds = 1;
    private String dockerNetwork = "dts-core";
    private boolean dockerPrivileged = true;

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

    public int getDagReadyWaitSeconds() {
        return dagReadyWaitSeconds;
    }

    public void setDagReadyWaitSeconds(int dagReadyWaitSeconds) {
        this.dagReadyWaitSeconds = dagReadyWaitSeconds;
    }

    public int getDagReadyPollSeconds() {
        return dagReadyPollSeconds;
    }

    public void setDagReadyPollSeconds(int dagReadyPollSeconds) {
        this.dagReadyPollSeconds = dagReadyPollSeconds;
    }

    public String getDockerNetwork() {
        return dockerNetwork;
    }

    public void setDockerNetwork(String dockerNetwork) {
        this.dockerNetwork = dockerNetwork;
    }

    public boolean isDockerPrivileged() {
        return dockerPrivileged;
    }

    public void setDockerPrivileged(boolean dockerPrivileged) {
        this.dockerPrivileged = dockerPrivileged;
    }
}
