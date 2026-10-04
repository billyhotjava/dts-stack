package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "admin_dataset")
public class AdminDataset extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "business_code", nullable = false, unique = true)
    private String businessCode;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "data_level", nullable = false, length = 64)
    private String dataLevel;

    @Column(name = "owner_org_id")
    private Long ownerOrgId;

    @Column(name = "is_institute_shared")
    private Boolean isInstituteShared;

    @Column(name = "row_count")
    private Long rowCount;

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDataLevel() { return dataLevel; }
    public void setDataLevel(String dataLevel) { this.dataLevel = dataLevel; }
    public Long getOwnerOrgId() { return ownerOrgId; }
    public void setOwnerOrgId(Long ownerOrgId) { this.ownerOrgId = ownerOrgId; }
    public Boolean getIsInstituteShared() { return isInstituteShared; }
    public void setIsInstituteShared(Boolean instituteShared) { isInstituteShared = instituteShared; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
}

