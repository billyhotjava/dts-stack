package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "svc_api")
public class SvcApi extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    @Column(name = "dataset_name", length = 160)
    private String datasetName;

    @Column(name = "method", length = 16)
    private String method;

    @Column(name = "path", length = 256)
    private String path;

    @Column(name = "classification", length = 32)
    private String classification;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "qps_limit")
    private Integer qpsLimit;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Column(name = "current_qps")
    private Integer currentQps;

    @Lob
    @Column(name = "policy_json")
    private String policyJson;

    @Lob
    @Column(name = "request_schema_json")
    private String requestSchemaJson;

    @Lob
    @Column(name = "response_schema_json")
    private String responseSchemaJson;

    @Column(name = "description", length = 2048)
    private String description;

    @Column(name = "latest_version", length = 32)
    private String latestVersion;

    @Column(name = "last_published_at")
    private Instant lastPublishedAt;

    @Column(name = "tags", length = 256)
    private String tags;

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

    public UUID getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(UUID datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public Integer getQpsLimit() {
        return qpsLimit;
    }

    public void setQpsLimit(Integer qpsLimit) {
        this.qpsLimit = qpsLimit;
    }

    public Integer getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(Integer dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public Integer getCurrentQps() {
        return currentQps;
    }

    public void setCurrentQps(Integer currentQps) {
        this.currentQps = currentQps;
    }

    public String getPolicyJson() {
        return policyJson;
    }

    public void setPolicyJson(String policyJson) {
        this.policyJson = policyJson;
    }

    public String getRequestSchemaJson() {
        return requestSchemaJson;
    }

    public void setRequestSchemaJson(String requestSchemaJson) {
        this.requestSchemaJson = requestSchemaJson;
    }

    public String getResponseSchemaJson() {
        return responseSchemaJson;
    }

    public void setResponseSchemaJson(String responseSchemaJson) {
        this.responseSchemaJson = responseSchemaJson;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public Instant getLastPublishedAt() {
        return lastPublishedAt;
    }

    public void setLastPublishedAt(Instant lastPublishedAt) {
        this.lastPublishedAt = lastPublishedAt;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
