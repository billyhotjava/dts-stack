package com.yuzhi.dts.admin.service.infra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JdbcConnectionTestRequest {

    private String jdbcUrl;

    private String driverClass;

    private String driverVersion;

    private String username;

    private String password;

    private Map<String, String> jdbcProperties;

    private String testQuery;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public void setDriverVersion(String driverVersion) {
        this.driverVersion = driverVersion;
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

    public Map<String, String> getJdbcProperties() {
        return jdbcProperties == null ? Collections.emptyMap() : jdbcProperties;
    }

    public void setJdbcProperties(Map<String, String> jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
    }

    public String getTestQuery() {
        return testQuery;
    }

    public void setTestQuery(String testQuery) {
        this.testQuery = testQuery;
    }
}
