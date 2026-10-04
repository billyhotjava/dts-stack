package com.yuzhi.dts.platform.domain.infra;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_catalog_sync_plan")
public class InfraCatalogSyncPlan extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "dag_id", length = 128)
    private String dagId;

    @Column(name = "cron", length = 128)
    private String cron;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "description", length = 512)
    private String description;

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

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDagId() {
        return dagId;
    }

    public void setDagId(String dagId) {
        this.dagId = dagId;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
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
}
