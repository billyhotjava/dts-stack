package com.yuzhi.dts.admin.service.infra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformDataLakeConfigResponse {

    private UUID id;

    private String name;

    private String type;

    private String description;

    private String jdbcUrl;

    private String username;

    private String password;

    private boolean defaulted;

    private Map<String, String> jdbcProperties = new HashMap<>();

    private String destinationId;

    private String destinationName;

    private String destinationDefinitionId;

    private Map<String, Object> destinationConfig = new HashMap<>();

    private String status;

    private String heartbeatStatus;

    private Integer heartbeatFailureCount;

    private String lastError;

    private Long lastTestElapsedMillis;

    private String engineVersion;

    private String driverVersion;

    private Instant lastVerifiedAt;

    private Instant lastHeartbeatAt;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public boolean isDefaulted() {
        return defaulted;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
    }

    public Map<String, String> getJdbcProperties() {
        return jdbcProperties;
    }

    public void setJdbcProperties(Map<String, String> jdbcProperties) {
        this.jdbcProperties = jdbcProperties != null ? new HashMap<>(jdbcProperties) : new HashMap<>();
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getDestinationDefinitionId() {
        return destinationDefinitionId;
    }

    public void setDestinationDefinitionId(String destinationDefinitionId) {
        this.destinationDefinitionId = destinationDefinitionId;
    }

    public Map<String, Object> getDestinationConfig() {
        return destinationConfig;
    }

    public void setDestinationConfig(Map<String, Object> destinationConfig) {
        this.destinationConfig = destinationConfig != null ? new HashMap<>(destinationConfig) : new HashMap<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
