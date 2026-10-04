package com.yuzhi.dts.admin.service.infra.dto;

import java.util.ArrayList;
import java.util.List;

public class InfraFeatureFlags {

    private boolean multiSourceEnabled;

    private boolean hasActiveInceptor;

    private String inceptorStatus;

    private Boolean syncInProgress;

    private String defaultJdbcUrl;

    private String loginPrincipal;

    private String lastVerifiedAt;

    private String lastUpdatedAt;

    private String dataSourceName;

    private String description;

    private String authMethod;

    private String database;

    private String proxyUser;

    private String engineVersion;

    private String driverVersion;

    private Long lastTestElapsedMillis;

    private String lastHeartbeatAt;

    private String heartbeatStatus;

    private List<ModuleStatus> moduleStatuses = new ArrayList<>();

    private IntegrationStatus integrationStatus;

    public boolean isMultiSourceEnabled() {
        return multiSourceEnabled;
    }

    public void setMultiSourceEnabled(boolean multiSourceEnabled) {
        this.multiSourceEnabled = multiSourceEnabled;
    }

    public boolean isHasActiveInceptor() {
        return hasActiveInceptor;
    }

    public void setHasActiveInceptor(boolean hasActiveInceptor) {
        this.hasActiveInceptor = hasActiveInceptor;
    }

    public String getInceptorStatus() {
        return inceptorStatus;
    }

    public void setInceptorStatus(String inceptorStatus) {
        this.inceptorStatus = inceptorStatus;
    }

    public Boolean getSyncInProgress() {
        return syncInProgress;
    }

    public void setSyncInProgress(Boolean syncInProgress) {
        this.syncInProgress = syncInProgress;
    }

    public String getDefaultJdbcUrl() {
        return defaultJdbcUrl;
    }

    public void setDefaultJdbcUrl(String defaultJdbcUrl) {
        this.defaultJdbcUrl = defaultJdbcUrl;
    }

    public String getLoginPrincipal() {
        return loginPrincipal;
    }

    public void setLoginPrincipal(String loginPrincipal) {
        this.loginPrincipal = loginPrincipal;
    }

    public String getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(String lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(String lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
    }

    public Long getLastTestElapsedMillis() {
        return lastTestElapsedMillis;
    }

    public void setLastTestElapsedMillis(Long lastTestElapsedMillis) {
        this.lastTestElapsedMillis = lastTestElapsedMillis;
    }

    public String getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(String lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getHeartbeatStatus() {
        return heartbeatStatus;
    }

    public void setHeartbeatStatus(String heartbeatStatus) {
        this.heartbeatStatus = heartbeatStatus;
    }

    public List<ModuleStatus> getModuleStatuses() {
        return moduleStatuses;
    }

    public void setModuleStatuses(List<ModuleStatus> moduleStatuses) {
        this.moduleStatuses = moduleStatuses != null ? new ArrayList<>(moduleStatuses) : new ArrayList<>();
    }

    public IntegrationStatus getIntegrationStatus() {
        return integrationStatus;
    }

    public void setIntegrationStatus(IntegrationStatus integrationStatus) {
        this.integrationStatus = integrationStatus;
    }
}
