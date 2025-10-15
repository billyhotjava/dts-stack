package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_dataset")
public class CatalogDataset extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id")
    private CatalogDomain domain;

    @Column(name = "type", length = 32)
    private String type; // hive|jdbc|file

    @Column(name = "classification", length = 32)
    private String classification; // PUBLIC/INTERNAL/SECRET/CONFIDENTIAL/TOP_SECRET

    // New ABAC fields (MVP): prefer these when present
    @Column(name = "data_level", length = 32)
    private String dataLevel; // DATA_PUBLIC/DATA_INTERNAL/DATA_SECRET/DATA_TOP_SECRET

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

    // VIEW | RANGER | API (how the dataset is exposed/consumed)
    @Column(name = "exposed_by", length = 16)
    private String exposedBy;

    // Optional Trino catalog for querying
    @Column(name = "trino_catalog", length = 64)
    private String trinoCatalog;

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

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
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
}
