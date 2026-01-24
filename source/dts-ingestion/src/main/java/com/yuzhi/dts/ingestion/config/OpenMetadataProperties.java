package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.openmetadata")
public class OpenMetadataProperties {

    private boolean enabled = true;
    private String baseUrl;
    private String apiPath = "/api/v1";
    private String authToken;
    private String tableFields = "columns,owner,tags,domain";
    private String sourceServiceName;
    private String sourceServiceType;
    private String destinationServiceName;
    private String destinationServiceType = "Postgres";
    private String destinationDatabase;
    private String destinationSchema;
    private String sourceDatabase;
    private String sourceSchema;
    private boolean ingestionEnabled = true;
    private String ingestionPipelinePrefix = "dts_ingest";
    private String ingestionDefaultSchedule = "0 * * * *";

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

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getTableFields() {
        return tableFields;
    }

    public void setTableFields(String tableFields) {
        this.tableFields = tableFields;
    }

    public String getSourceServiceName() {
        return sourceServiceName;
    }

    public void setSourceServiceName(String sourceServiceName) {
        this.sourceServiceName = sourceServiceName;
    }

    public String getSourceServiceType() {
        return sourceServiceType;
    }

    public void setSourceServiceType(String sourceServiceType) {
        this.sourceServiceType = sourceServiceType;
    }

    public String getDestinationServiceName() {
        return destinationServiceName;
    }

    public void setDestinationServiceName(String destinationServiceName) {
        this.destinationServiceName = destinationServiceName;
    }

    public String getDestinationServiceType() {
        return destinationServiceType;
    }

    public void setDestinationServiceType(String destinationServiceType) {
        this.destinationServiceType = destinationServiceType;
    }

    public String getDestinationDatabase() {
        return destinationDatabase;
    }

    public void setDestinationDatabase(String destinationDatabase) {
        this.destinationDatabase = destinationDatabase;
    }

    public String getDestinationSchema() {
        return destinationSchema;
    }

    public void setDestinationSchema(String destinationSchema) {
        this.destinationSchema = destinationSchema;
    }

    public String getSourceDatabase() {
        return sourceDatabase;
    }

    public void setSourceDatabase(String sourceDatabase) {
        this.sourceDatabase = sourceDatabase;
    }

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public boolean isIngestionEnabled() {
        return ingestionEnabled;
    }

    public void setIngestionEnabled(boolean ingestionEnabled) {
        this.ingestionEnabled = ingestionEnabled;
    }

    public String getIngestionPipelinePrefix() {
        return ingestionPipelinePrefix;
    }

    public void setIngestionPipelinePrefix(String ingestionPipelinePrefix) {
        this.ingestionPipelinePrefix = ingestionPipelinePrefix;
    }

    public String getIngestionDefaultSchedule() {
        return ingestionDefaultSchedule;
    }

    public void setIngestionDefaultSchedule(String ingestionDefaultSchedule) {
        this.ingestionDefaultSchedule = ingestionDefaultSchedule;
    }
}
