package com.yuzhi.dts.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "infra_data_source")
public class InfraDataSource extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "jdbc_url", nullable = false, length = 512)
    private String jdbcUrl;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "description", length = 512)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", columnDefinition = "jsonb")
    private Map<String, Object> props = new HashMap<>();

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "has_secrets")
    private boolean hasSecrets;

    @Column(name = "engine_version", length = 64)
    private String engineVersion;

    @Column(name = "driver_version", length = 64)
    private String driverVersion;

    @Column(name = "last_test_elapsed_ms")
    private Long lastTestElapsedMillis;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "heartbeat_status", length = 32)
    private String heartbeatStatus;

    @Column(name = "heartbeat_failure_count")
    private Integer heartbeatFailureCount;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "secure_props")
    private byte[] secureProps;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "secure_iv")
    private byte[] secureIv;

    @Column(name = "secure_key_version", length = 32)
    private String secureKeyVersion;

    @Override
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
        if (props == null) {
            props = new HashMap<>();
        }
        return props;
    }

    public void setProps(Map<String, Object> props) {
        this.props = props != null ? new HashMap<>(props) : new HashMap<>();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public byte[] getSecureProps() {
        return secureProps;
    }

    public void setSecureProps(byte[] secureProps) {
        this.secureProps = secureProps;
    }

    public byte[] getSecureIv() {
        return secureIv;
    }

    public void setSecureIv(byte[] secureIv) {
        this.secureIv = secureIv;
    }

    public String getSecureKeyVersion() {
        return secureKeyVersion;
    }

    public void setSecureKeyVersion(String secureKeyVersion) {
        this.secureKeyVersion = secureKeyVersion;
    }
}
