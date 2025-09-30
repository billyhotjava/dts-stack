package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_secure_view")
public class CatalogSecureView extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private CatalogDataset dataset;

    @Column(name = "view_name", length = 128)
    private String viewName;

    // PUBLIC/INTERNAL/SECRET/TOP_SECRET
    @Column(name = "level", length = 32)
    private String level;

    // optional override row filter per view
    @Column(name = "row_filter", length = 2048)
    private String rowFilter;

    // CSV of masked columns or JSON spec, free text MVP
    @Column(name = "mask_columns", length = 2048)
    private String maskColumns;

    // NONE/SCHEDULED/ON_DEMAND
    @Column(name = "refresh", length = 32)
    private String refresh;

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

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getRowFilter() {
        return rowFilter;
    }

    public void setRowFilter(String rowFilter) {
        this.rowFilter = rowFilter;
    }

    public String getMaskColumns() {
        return maskColumns;
    }

    public void setMaskColumns(String maskColumns) {
        this.maskColumns = maskColumns;
    }

    public String getRefresh() {
        return refresh;
    }

    public void setRefresh(String refresh) {
        this.refresh = refresh;
    }
}

