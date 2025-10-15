package com.yuzhi.dts.admin.service.infra.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfraDataSourceDto {

    private UUID id;

    private String name;

    private String type;

    private String jdbcUrl;

    private String username;

    private String description;

    private Map<String, Object> props = new HashMap<>();

    private Instant createdAt;

    private Instant lastUpdatedAt;

    private Instant lastVerifiedAt;

    private String status;

    private boolean hasSecrets;

    private String engineVersion;

    private String driverVersion;

    private Long lastTestElapsedMillis;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getProps() {
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props != null ? new HashMap<>(props) : new HashMap<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isHasSecrets() {
        return hasSecrets;
    }

    public void setHasSecrets(boolean hasSecrets) {
        this.hasSecrets = hasSecrets;
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

    public InfraDataSourceDto copy() {
        InfraDataSourceDto dto = new InfraDataSourceDto();
        dto.setId(id);
        dto.setName(name);
        dto.setType(type);
        dto.setJdbcUrl(jdbcUrl);
        dto.setUsername(username);
        dto.setDescription(description);
        dto.setProps(props);
        dto.setCreatedAt(createdAt);
        dto.setLastUpdatedAt(lastUpdatedAt);
        dto.setLastVerifiedAt(lastVerifiedAt);
        dto.setStatus(status);
        dto.setHasSecrets(hasSecrets);
        dto.setEngineVersion(engineVersion);
        dto.setDriverVersion(driverVersion);
        dto.setLastTestElapsedMillis(lastTestElapsedMillis);
        dto.setLastHeartbeatAt(lastHeartbeatAt);
        dto.setHeartbeatStatus(heartbeatStatus);
        dto.setHeartbeatFailureCount(heartbeatFailureCount);
        dto.setLastError(lastError);
        return dto;
    }
}
