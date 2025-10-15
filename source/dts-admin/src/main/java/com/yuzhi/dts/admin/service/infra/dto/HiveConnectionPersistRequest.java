package com.yuzhi.dts.admin.service.infra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class HiveConnectionPersistRequest extends HiveConnectionTestRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String servicePrincipal;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    @NotBlank
    private String database;

    private boolean useHttpTransport;

    private String httpPath;

    private boolean useSsl;

    private boolean useCustomJdbc;

    private String customJdbcUrl;

    private Long lastTestElapsedMillis;

    private String engineVersion;

    private String driverVersion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServicePrincipal() {
        return servicePrincipal;
    }

    public void setServicePrincipal(String servicePrincipal) {
        this.servicePrincipal = servicePrincipal;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public boolean isUseHttpTransport() {
        return useHttpTransport;
    }

    public void setUseHttpTransport(boolean useHttpTransport) {
        this.useHttpTransport = useHttpTransport;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public void setHttpPath(String httpPath) {
        this.httpPath = httpPath;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public boolean isUseCustomJdbc() {
        return useCustomJdbc;
    }

    public void setUseCustomJdbc(boolean useCustomJdbc) {
        this.useCustomJdbc = useCustomJdbc;
    }

    public String getCustomJdbcUrl() {
        return customJdbcUrl;
    }

    public void setCustomJdbcUrl(String customJdbcUrl) {
        this.customJdbcUrl = customJdbcUrl;
    }

    public Long getLastTestElapsedMillis() {
        return lastTestElapsedMillis;
    }

    public void setLastTestElapsedMillis(Long lastTestElapsedMillis) {
        this.lastTestElapsedMillis = lastTestElapsedMillis;
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
}
