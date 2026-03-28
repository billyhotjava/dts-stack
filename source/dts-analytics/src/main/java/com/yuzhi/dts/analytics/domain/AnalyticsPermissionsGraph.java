package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * @deprecated Superseded by platform-centralized asset permission model (Sprint-19).
 * Permission checks now go through PlatformPermissionClient → platform AssetPermissionService.
 * This entity and its table will be removed in a future release.
 */
@Deprecated(since = "2.2.2", forRemoval = true)

@Entity
@Table(name = "analytics_permissions_graph")
public class AnalyticsPermissionsGraph implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "graph_json", nullable = false, columnDefinition = "CLOB")
    private String graphJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getGraphJson() {
        return graphJson;
    }

    public void setGraphJson(String graphJson) {
        this.graphJson = graphJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

