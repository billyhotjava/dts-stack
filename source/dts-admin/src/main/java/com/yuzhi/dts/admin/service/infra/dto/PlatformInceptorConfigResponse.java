package com.yuzhi.dts.admin.service.infra.dto;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlatformInceptorConfigResponse {

    private UUID id;
    private String name;
    private String description;
    private String jdbcUrl;
    private String loginPrincipal;
    private String authMethod;
    private String krb5Conf;
    private String keytabBase64;
    private String keytabFileName;
    private String password;
    private Map<String, String> jdbcProperties = new HashMap<>();
    private String proxyUser;
    private String servicePrincipal;
    private String host;
    private Integer port;
    private String database;
    private boolean useHttpTransport;
    private String httpPath;
    private boolean useSsl;
    private boolean useCustomJdbc;
    private String customJdbcUrl;
    private Long lastTestElapsedMillis;
    private String engineVersion;
    private String driverVersion;
    private Instant lastVerifiedAt;
    private Instant lastUpdatedAt;
    private Instant lastHeartbeatAt;
    private String heartbeatStatus;
    private Integer heartbeatFailureCount;
    private String lastError;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
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
        return jdbcProperties;
    }

    public void setJdbcProperties(Map<String, String> jdbcProperties) {
        this.jdbcProperties = jdbcProperties != null ? new HashMap<>(jdbcProperties) : new HashMap<>();
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
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

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getHeartbeatStatus() {
        return heartbeatStatus;
    }

    public void setHeartbeatStatus(String heartbeatStatus) {
        this.heartbeatStatus = heartbeatStatus;
    }

    public Integer getHeartbeatFailureCount() {
        return heartbeatFailureCount;
    }

    public void setHeartbeatFailureCount(Integer heartbeatFailureCount) {
        this.heartbeatFailureCount = heartbeatFailureCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
