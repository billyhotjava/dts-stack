package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.airbyte")
public class AirbyteProperties {

    private boolean enabled = true;
    private String baseUrl;
    private String apiPath = "/api/v1";
    private String username;
    private String password;
    private String authUserId = "00000000-0000-0000-0000-000000000000";
    private String organizationId;
    private String organizationName = "dts-org";
    private String workspaceId;
    private String defaultDestinationId;
    private String defaultDestinationName = "dts-ods-destination";
    private String defaultDestinationDefinitionId;
    private String defaultDestinationConfigJson;
    private String defaultDestinationImage;

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

    public String getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(String authUserId) {
        this.authUserId = authUserId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getDefaultDestinationId() {
        return defaultDestinationId;
    }

    public void setDefaultDestinationId(String defaultDestinationId) {
        this.defaultDestinationId = defaultDestinationId;
    }

    public String getDefaultDestinationName() {
        return defaultDestinationName;
    }

    public void setDefaultDestinationName(String defaultDestinationName) {
        this.defaultDestinationName = defaultDestinationName;
    }

    public String getDefaultDestinationDefinitionId() {
        return defaultDestinationDefinitionId;
    }

    public void setDefaultDestinationDefinitionId(String defaultDestinationDefinitionId) {
        this.defaultDestinationDefinitionId = defaultDestinationDefinitionId;
    }

    public String getDefaultDestinationConfigJson() {
        return defaultDestinationConfigJson;
    }

    public void setDefaultDestinationConfigJson(String defaultDestinationConfigJson) {
        this.defaultDestinationConfigJson = defaultDestinationConfigJson;
    }

    public String getDefaultDestinationImage() {
        return defaultDestinationImage;
    }

    public void setDefaultDestinationImage(String defaultDestinationImage) {
        this.defaultDestinationImage = defaultDestinationImage;
    }
}
