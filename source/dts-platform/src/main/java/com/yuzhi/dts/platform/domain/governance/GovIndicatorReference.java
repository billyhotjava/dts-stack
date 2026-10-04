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
@Table(name = "gov_indicator_reference")
public class GovIndicatorReference extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id", nullable = false)
    private GovIndicatorDefinition indicator;

    @Column(name = "ref_type", length = 32, nullable = false)
    private String refType; // DATASET/INDICATOR/DIMENSION/REPORT/...

    @Column(name = "ref_target", length = 256, nullable = false)
    private String refTarget; // UUID or code or url key

    @Column(name = "ref_name", length = 256)
    private String refName;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovIndicatorDefinition getIndicator() {
        return indicator;
    }

    public void setIndicator(GovIndicatorDefinition indicator) {
        this.indicator = indicator;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefTarget() {
        return refTarget;
    }

    public void setRefTarget(String refTarget) {
        this.refTarget = refTarget;
    }

    public String getRefName() {
        return refName;
    }

    public void setRefName(String refName) {
        this.refName = refName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

