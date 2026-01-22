package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "infra_airbyte_source")
public class InfraAirbyteSource extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "source_definition_id", length = 64)
    private String sourceDefinitionId;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_discovered_at")
    private Instant lastDiscoveredAt;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "config_json", columnDefinition = "text")
    private String configJson;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "secure_props", columnDefinition = "bytea")
    private byte[] secureProps;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "secure_iv", columnDefinition = "bytea")
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

    public String getSourceDefinitionId() {
        return sourceDefinitionId;
    }

    public void setSourceDefinitionId(String sourceDefinitionId) {
        this.sourceDefinitionId = sourceDefinitionId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getLastDiscoveredAt() {
        return lastDiscoveredAt;
    }

    public void setLastDiscoveredAt(Instant lastDiscoveredAt) {
        this.lastDiscoveredAt = lastDiscoveredAt;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
