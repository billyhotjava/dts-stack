package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_row_filter_rule")
public class CatalogRowFilterRule extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id")
    private CatalogDataset dataset;

    @Column(name = "roles", length = 512)
    private String roles; // CSV of roles to which this rule applies

    @Column(name = "expression", length = 2048)
    private String expression; // SQL boolean expression

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

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}

