package com.yuzhi.dts.platform.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.platform.openmetadata")
public class OpenMetadataProperties {

    private boolean enabled = false;
    private String baseUrl = "http://dts-openmetadata:8585";
    private String apiPath = "/api/v1";
    private String uiBaseUrl;
    private String authToken;
    private String serviceName = "hive";
    private String defaultDatabase;
    private String defaultSchema;
    private String tableFqnPattern = "{service}.{database}.{table}";
    private String tableFields = "columns,owner,tags,domain,usageSummary,profile";
    private List<String> forbiddenDatabases = new ArrayList<>();

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

    public String getUiBaseUrl() {
        return uiBaseUrl;
    }

    public void setUiBaseUrl(String uiBaseUrl) {
        this.uiBaseUrl = uiBaseUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDefaultDatabase() {
        return defaultDatabase;
    }

    public void setDefaultDatabase(String defaultDatabase) {
        this.defaultDatabase = defaultDatabase;
    }

    public String getDefaultSchema() {
        return defaultSchema;
    }

    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    public String getTableFqnPattern() {
        return tableFqnPattern;
    }

    public void setTableFqnPattern(String tableFqnPattern) {
        this.tableFqnPattern = tableFqnPattern;
    }

    public String getTableFields() {
        return tableFields;
    }

    public void setTableFields(String tableFields) {
        this.tableFields = tableFields;
    }

    public List<String> getForbiddenDatabases() {
        return forbiddenDatabases;
    }

    public void setForbiddenDatabases(List<String> forbiddenDatabases) {
        this.forbiddenDatabases = forbiddenDatabases == null ? new ArrayList<>() : forbiddenDatabases;
    }
}
