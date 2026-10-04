package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_domain")
public class CatalogDomain extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "description", length = 512)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CatalogDomain parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 16, nullable = false)
    private CatalogDomainLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_policy", length = 16, nullable = false)
    private CatalogDomainAccessPolicy accessPolicy;

    @PrePersist
    void applyCatalogFactDefaults() {
        if (lifecycleStatus == null) {
            lifecycleStatus = CatalogDomainLifecycleStatus.ACTIVE;
        }
        if (accessPolicy == null) {
            accessPolicy = CatalogDomainAccessPolicy.PUBLIC;
        }
    }

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CatalogDomain getParent() {
        return parent;
    }

    public void setParent(CatalogDomain parent) {
        this.parent = parent;
    }

    public CatalogDomainLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(CatalogDomainLifecycleStatus lifecycleStatus) {
        if (lifecycleStatus == null) {
            throw new IllegalArgumentException("lifecycleStatus must not be null");
        }
        this.lifecycleStatus = lifecycleStatus;
    }

    public CatalogDomainAccessPolicy getAccessPolicy() {
        return accessPolicy;
    }

    public void setAccessPolicy(CatalogDomainAccessPolicy accessPolicy) {
        if (accessPolicy == null) {
            throw new IllegalArgumentException("accessPolicy must not be null");
        }
        this.accessPolicy = accessPolicy;
    }
}
