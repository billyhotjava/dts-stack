package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organization_node")
public class OrganizationNode extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "data_level", nullable = false, length = 64)
    private String dataLevel; // DATA_PUBLIC / DATA_INTERNAL / DATA_SECRET / DATA_CONFIDENTIAL

    @Column(name = "contact")
    private String contact;

    @Column(name = "dept_code", unique = true, length = 128)
    private String deptCode;

    @Column(name = "org_code", length = 128)
    private String orgCode;

    @Column(name = "parent_code", length = 128)
    private String parentCode;

    @Column(name = "short_name", length = 256)
    private String shortName;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "sort_order", length = 64)
    private String sortOrder;

    @Column(name = "mdm_type", length = 64)
    private String mdmType;

    @Column(name = "phone")
    private String phone;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "keycloak_group_id", unique = true)
    private String keycloakGroupId;

    @Column(name = "is_root", nullable = false)
    private boolean root;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrganizationNode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrganizationNode> children = new ArrayList<>();

    @Override
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

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getMdmType() {
        return mdmType;
    }

    public void setMdmType(String mdmType) {
        this.mdmType = mdmType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKeycloakGroupId() {
        return keycloakGroupId;
    }

    public void setKeycloakGroupId(String keycloakGroupId) {
        this.keycloakGroupId = keycloakGroupId;
    }

    public boolean isRoot() {
        return root;
    }

    public void setRoot(boolean root) {
        this.root = root;
    }

    public OrganizationNode getParent() {
        return parent;
    }

    public void setParent(OrganizationNode parent) {
        this.parent = parent;
    }

    public List<OrganizationNode> getChildren() {
        return children;
    }

    public void setChildren(List<OrganizationNode> children) {
        this.children = children;
    }
}
