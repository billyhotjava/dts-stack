package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_masking_rule")
public class CatalogMaskingRule extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private CatalogDataset dataset;

    @Column(name = "column_name", length = 128)
    private String column;

    @Column(name = "function", length = 64)
    private String function;

    @Column(name = "args", length = 512)
    private String args;

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

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }
}

