package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_access_policy")
public class CatalogAccessPolicy extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private CatalogDataset dataset;

    // Comma separated roles, e.g. ROLE_PUBLIC,ROLE_INTERNAL
    @Column(name = "allow_roles", length = 512)
    private String allowRoles;

    @Column(name = "row_filter", length = 2048)
    private String rowFilter;

    // default masking strategy for unspecified columns, e.g. NONE/HASH/PARTIAL
    @Column(name = "default_masking", length = 64)
    private String defaultMasking;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CatalogDataset getDataset() {
        return dataset;
    }

    public void setDataset(CatalogDataset dataset) {
        this.dataset = dataset;
    }

    public String getAllowRoles() {
        return allowRoles;
    }

    public void setAllowRoles(String allowRoles) {
        this.allowRoles = allowRoles;
    }

    public String getRowFilter() {
        return rowFilter;
    }

    public void setRowFilter(String rowFilter) {
        this.rowFilter = rowFilter;
    }

    public String getDefaultMasking() {
        return defaultMasking;
    }

    public void setDefaultMasking(String defaultMasking) {
        this.defaultMasking = defaultMasking;
    }
}
