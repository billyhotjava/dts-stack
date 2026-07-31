package com.yuzhi.dts.ingestion.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Type;

/** Immutable, versioned defaults used to resolve an ingestion task revision. */
@Entity
@Table(name = "ingestion_access_default_policy")
public class IngestionAccessDefaultPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_key", length = 64, nullable = false)
    private String policyKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Type(JsonType.class)
    @Column(name = "defaults", columnDefinition = "jsonb", nullable = false)
    private JsonNode defaults;

    @Column(name = "checksum", length = 64, nullable = false)
    private String checksum;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPolicyKey() {
        return policyKey;
    }

    public void setPolicyKey(String policyKey) {
        this.policyKey = policyKey;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonNode getDefaults() {
        return defaults;
    }

    public void setDefaults(JsonNode defaults) {
        this.defaults = defaults;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
