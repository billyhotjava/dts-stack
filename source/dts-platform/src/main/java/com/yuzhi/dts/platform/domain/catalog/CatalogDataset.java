package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset")
public class CatalogDataset extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private CatalogDomain domain;

    @Column(name = "type", length = 32)
    private String type; // hive|jdbc|file

    @Column(name = "source_id", columnDefinition = "uuid")
    private UUID sourceId;

    @Column(name = "classification", length = 32)
    private String classification; // canonical data classification code from SecurityLevelCatalog

    @Column(name = "owner_dept", length = 64)
    private String ownerDept; // owning department code

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "hive_database", length = 128)
    private String hiveDatabase;

    @Column(name = "hive_table", length = 128)
    private String hiveTable;

    @Column(name = "tags", length = 1024)
    private String tags;

    @Column(name = "description", length = 2048)
    private String description;

    // ODS/DWD/DWS/ADS (optional)
    @Column(name = "warehouse_layer", length = 16)
    private String warehouseLayer;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    // VIEW | RANGER | API (how the dataset is exposed/consumed)
    @Column(name = "exposed_by", length = 16)
    private String exposedBy;

    // Optional Trino catalog for querying
    @Column(name = "trino_catalog", length = 64)
    private String trinoCatalog;

    @Column(name = "lifecycle_status", length = 32)
    private String lifecycleStatus;

    @Column(name = "harvest_status", length = 32)
    private String harvestStatus;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "snapshot_time")
    private Instant snapshotTime;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public long getVersion() { return version; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CatalogDomain getDomain() {
        return domain;
    }

    public void setDomain(CatalogDomain domain) {
        this.domain = domain;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getHiveDatabase() {
        return hiveDatabase;
    }

    public void setHiveDatabase(String hiveDatabase) {
        this.hiveDatabase = hiveDatabase;
    }

    public String getHiveTable() {
        return hiveTable;
    }

    public void setHiveTable(String hiveTable) {
        this.hiveTable = hiveTable;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWarehouseLayer() {
        return warehouseLayer;
    }

    public void setWarehouseLayer(String warehouseLayer) {
        this.warehouseLayer = warehouseLayer;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getExposedBy() {
        return exposedBy;
    }

    public void setExposedBy(String exposedBy) {
        this.exposedBy = exposedBy;
    }

    public String getTrinoCatalog() {
        return trinoCatalog;
    }

    public void setTrinoCatalog(String trinoCatalog) {
        this.trinoCatalog = trinoCatalog;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public String getHarvestStatus() {
        return harvestStatus;
    }

    public void setHarvestStatus(String harvestStatus) {
        this.harvestStatus = harvestStatus;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(Instant snapshotTime) {
        this.snapshotTime = snapshotTime;
    }
}
