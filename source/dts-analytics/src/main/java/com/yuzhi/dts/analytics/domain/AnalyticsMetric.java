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
@Table(name = "analytics_metric")
public class AnalyticsMetric implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "metric_json", nullable = false, columnDefinition = "text")
    private String metricJson;

    @Column(name = "base_table_id")
    private Long baseTableId;

    @Column(name = "aggregation", length = 30)
    private String aggregation;

    @Column(name = "expression_field", length = 255)
    private String expressionField;

    @Column(name = "filter_json", columnDefinition = "text")
    private String filterJson;

    @Column(name = "time_dimension", length = 255)
    private String timeDimension;

    @Column(name = "time_grain", length = 20)
    private String timeGrain;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "visibility", length = 20)
    private String visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
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

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getMetricJson() {
        return metricJson;
    }

    public void setMetricJson(String metricJson) {
        this.metricJson = metricJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getBaseTableId() {
        return baseTableId;
    }

    public void setBaseTableId(Long baseTableId) {
        this.baseTableId = baseTableId;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    public String getExpressionField() {
        return expressionField;
    }

    public void setExpressionField(String expressionField) {
        this.expressionField = expressionField;
    }

    public String getFilterJson() {
        return filterJson;
    }

    public void setFilterJson(String filterJson) {
        this.filterJson = filterJson;
    }

    public String getTimeDimension() {
        return timeDimension;
    }

    public void setTimeDimension(String timeDimension) {
        this.timeDimension = timeDimension;
    }

    public String getTimeGrain() {
        return timeGrain;
    }

    public void setTimeGrain(String timeGrain) {
        this.timeGrain = timeGrain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
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

