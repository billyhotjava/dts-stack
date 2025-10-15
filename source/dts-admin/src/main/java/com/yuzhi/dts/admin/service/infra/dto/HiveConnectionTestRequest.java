package com.yuzhi.dts.admin.service.infra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

public class HiveConnectionTestRequest {

    @NotBlank
    private String jdbcUrl;

    @NotBlank
    private String loginPrincipal;

    @NotNull
    private HiveAuthMethod authMethod = HiveAuthMethod.KEYTAB;

    private String krb5Conf;

    private String keytabBase64;

    private String keytabFileName;

    private String password;

    private Map<String, String> jdbcProperties;

    private String proxyUser;

    private String testQuery;

    private String remarks;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getLoginPrincipal() {
        return loginPrincipal;
    }

    public void setLoginPrincipal(String loginPrincipal) {
        this.loginPrincipal = loginPrincipal;
    }

    public HiveAuthMethod getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(HiveAuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public String getKrb5Conf() {
        return krb5Conf;
    }

    public void setKrb5Conf(String krb5Conf) {
        this.krb5Conf = krb5Conf;
    }

    public String getKeytabBase64() {
        return keytabBase64;
    }

    public void setKeytabBase64(String keytabBase64) {
        this.keytabBase64 = keytabBase64;
    }

    public String getKeytabFileName() {
        return keytabFileName;
    }

    public void setKeytabFileName(String keytabFileName) {
        this.keytabFileName = keytabFileName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Map<String, String> getJdbcProperties() {
        if (jdbcProperties == null) {
            jdbcProperties = new HashMap<>();
        }
        return jdbcProperties;
    }

    public void setJdbcProperties(Map<String, String> jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getTestQuery() {
        return testQuery;
    }

    public void setTestQuery(String testQuery) {
        this.testQuery = testQuery;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
