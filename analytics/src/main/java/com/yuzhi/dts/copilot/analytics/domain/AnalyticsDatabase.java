package com.yuzhi.dts.copilot.analytics.domain;

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
@Table(name = "analytics_database")
public class AnalyticsDatabase implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "engine", nullable = false, length = 64)
    private String engine;

    @Column(name = "database_role", length = 64)
    private String databaseRole;

    @Column(name = "details_json", nullable = false, columnDefinition = "text")
    private String detailsJson;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_sample", nullable = false)
    private boolean sample;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "metadata_sync_schedule", length = 64)
    private String metadataSyncSchedule;

    @Column(name = "cache_field_values_schedule", length = 64)
    private String cacheFieldValuesSchedule;

    @Column(name = "auto_run_queries", nullable = false)
    private boolean autoRunQueries = true;

    @Column(name = "is_full_sync", nullable = false)
    private boolean fullSync = true;

    @Column(name = "is_on_demand", nullable = false)
    private boolean onDemand;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public AnalyticsDatabaseRole getDatabaseRole() {
        if (databaseRole == null || databaseRole.isBlank()) {
            return null;
        }
        return AnalyticsDatabaseRole.valueOf(databaseRole);
    }

    public void setDatabaseRole(AnalyticsDatabaseRole databaseRole) {
        this.databaseRole = databaseRole == null ? null : databaseRole.name();
    }

    public String getDatabaseRoleValue() {
        return databaseRole;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSample() {
        return sample;
    }

    public void setSample(boolean sample) {
        this.sample = sample;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getMetadataSyncSchedule() {
        return metadataSyncSchedule;
    }

    public void setMetadataSyncSchedule(String metadataSyncSchedule) {
        this.metadataSyncSchedule = metadataSyncSchedule;
    }

    public String getCacheFieldValuesSchedule() {
        return cacheFieldValuesSchedule;
    }

    public void setCacheFieldValuesSchedule(String cacheFieldValuesSchedule) {
        this.cacheFieldValuesSchedule = cacheFieldValuesSchedule;
    }

    public boolean isAutoRunQueries() {
        return autoRunQueries;
    }

    public void setAutoRunQueries(boolean autoRunQueries) {
        this.autoRunQueries = autoRunQueries;
    }

    public boolean isFullSync() {
        return fullSync;
    }

    public void setFullSync(boolean fullSync) {
        this.fullSync = fullSync;
    }

    public boolean isOnDemand() {
        return onDemand;
    }

    public void setOnDemand(boolean onDemand) {
        this.onDemand = onDemand;
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
