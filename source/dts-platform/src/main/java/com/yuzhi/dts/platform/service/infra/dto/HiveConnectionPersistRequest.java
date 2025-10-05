package com.yuzhi.dts.platform.service.infra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuzhi.dts.platform.web.rest.infra.HiveConnectionTestRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HiveConnectionPersistRequest extends HiveConnectionTestRequest {

    @NotBlank
    private String name;

    private String description;

    /** Hive 服务主体，如 hive/tdh01@TDH。 */
    @NotBlank
    private String servicePrincipal;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    @NotBlank
    private String database;

    private Boolean useHttpTransport;

    private String httpPath;

    private Boolean useSsl;

    private Boolean useCustomJdbc;

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

    public Boolean getUseHttpTransport() {
        return useHttpTransport;
    }

    public void setUseHttpTransport(Boolean useHttpTransport) {
        this.useHttpTransport = useHttpTransport;
    }

    public String getHttpPath() {
        return httpPath;
    }

    public void setHttpPath(String httpPath) {
        this.httpPath = httpPath;
    }

    public Boolean getUseSsl() {
        return useSsl;
    }

    public void setUseSsl(Boolean useSsl) {
        this.useSsl = useSsl;
    }

    public Boolean getUseCustomJdbc() {
        return useCustomJdbc;
    }

    public void setUseCustomJdbc(Boolean useCustomJdbc) {
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
