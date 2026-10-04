package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "svc_data_product")
public class SvcDataProduct extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Column(name = "product_type", length = 32)
    private String productType;

    @Column(name = "classification", length = 32)
    private String classification;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "sla", length = 64)
    private String sla;

    @Column(name = "refresh_frequency", length = 64)
    private String refreshFrequency;

    @Column(name = "latency_objective", length = 64)
    private String latencyObjective;

    @Column(name = "failure_policy", length = 256)
    private String failurePolicy;

    @Column(name = "subscriptions")
    private Integer subscriptions;

    @Column(name = "current_version", length = 32)
    private String currentVersion;

    @Column(name = "description", length = 2048)
    private String description;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSla() {
        return sla;
    }

    public void setSla(String sla) {
        this.sla = sla;
    }

    public String getRefreshFrequency() {
        return refreshFrequency;
    }

    public void setRefreshFrequency(String refreshFrequency) {
        this.refreshFrequency = refreshFrequency;
    }

    public String getLatencyObjective() {
        return latencyObjective;
    }

    public void setLatencyObjective(String latencyObjective) {
        this.latencyObjective = latencyObjective;
    }

    public String getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(String failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public Integer getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Integer subscriptions) {
        this.subscriptions = subscriptions;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
