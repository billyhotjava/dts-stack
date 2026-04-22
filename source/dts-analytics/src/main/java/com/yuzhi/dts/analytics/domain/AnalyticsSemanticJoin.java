package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_semantic_join")
public class AnalyticsSemanticJoin implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "from_model", nullable = false, length = 255)
    private String fromModel;

    @Column(name = "to_model", nullable = false, length = 255)
    private String toModel;

    @Column(name = "join_type", length = 64)
    private String joinType;

    @Column(name = "relationship", length = 32)
    private String relationship;

    @Column(name = "on_clause", nullable = false, columnDefinition = "text")
    private String onClause;

    @Column(name = "fanout_warning", nullable = false)
    private boolean fanoutWarning;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "meta_json", columnDefinition = "text")
    private String metaJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getFromModel() {
        return fromModel;
    }

    public void setFromModel(String fromModel) {
        this.fromModel = fromModel;
    }

    public String getToModel() {
        return toModel;
    }

    public void setToModel(String toModel) {
        this.toModel = toModel;
    }

    public String getJoinType() {
        return joinType;
    }

    public void setJoinType(String joinType) {
        this.joinType = joinType;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getOnClause() {
        return onClause;
    }

    public void setOnClause(String onClause) {
        this.onClause = onClause;
    }

    public boolean isFanoutWarning() {
        return fanoutWarning;
    }

    public void setFanoutWarning(boolean fanoutWarning) {
        this.fanoutWarning = fanoutWarning;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetaJson() {
        return metaJson;
    }

    public void setMetaJson(String metaJson) {
        this.metaJson = metaJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
