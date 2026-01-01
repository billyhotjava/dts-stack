package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "gov_dimension_item")
public class GovDimensionItem extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dimension_id", nullable = false)
    private GovDimensionDictionary dimension;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GovDimensionItem parent;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "status", length = 32, nullable = false)
    private String status; // ACTIVE/INACTIVE

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "attributes_json", columnDefinition = "text")
    private String attributesJson;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovDimensionDictionary getDimension() {
        return dimension;
    }

    public void setDimension(GovDimensionDictionary dimension) {
        this.dimension = dimension;
    }

    public GovDimensionItem getParent() {
        return parent;
    }

    public void setParent(GovDimensionItem parent) {
        this.parent = parent;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getAttributesJson() {
        return attributesJson;
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

