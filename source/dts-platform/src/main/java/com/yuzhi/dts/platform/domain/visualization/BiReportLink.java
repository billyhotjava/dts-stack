package com.yuzhi.dts.platform.domain.visualization;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bi_report_link")
public class BiReportLink extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 128, nullable = false, unique = true)
    private String code;

    @Column(name = "title", length = 256, nullable = false)
    private String title;

    @Column(name = "engine", length = 32, nullable = false)
    private String engine = "HETU";

    @Column(name = "report_type", length = 64)
    private String reportType;

    @Column(name = "dept_codes", columnDefinition = "text")
    private String deptCodes;

    @Column(name = "role_codes", columnDefinition = "text")
    private String roleCodes;

    @Column(name = "classification", length = 32, nullable = false)
    private String classification;

    @Column(name = "url", columnDefinition = "text", nullable = false)
    private String url;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "query_dataset_id", columnDefinition = "uuid")
    private UUID queryDatasetId;

    @Column(name = "query_dataset_version")
    private Integer queryDatasetVersion;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_visited_at")
    private Instant lastVisitedAt;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getDeptCodes() {
        return deptCodes;
    }

    public void setDeptCodes(String deptCodes) {
        this.deptCodes = deptCodes;
    }

    public String getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(String roleCodes) {
        this.roleCodes = roleCodes;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getQueryDatasetId() {
        return queryDatasetId;
    }

    public void setQueryDatasetId(UUID queryDatasetId) {
        this.queryDatasetId = queryDatasetId;
    }

    public Integer getQueryDatasetVersion() {
        return queryDatasetVersion;
    }

    public void setQueryDatasetVersion(Integer queryDatasetVersion) {
        this.queryDatasetVersion = queryDatasetVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastVisitedAt() {
        return lastVisitedAt;
    }

    public void setLastVisitedAt(Instant lastVisitedAt) {
        this.lastVisitedAt = lastVisitedAt;
    }
}
