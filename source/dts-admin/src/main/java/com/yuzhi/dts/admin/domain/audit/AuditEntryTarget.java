package com.yuzhi.dts.admin.domain.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "audit_entry_target")
public class AuditEntryTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    @JsonIgnore
    private AuditEntry entry;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "target_table", nullable = false, length = 128)
    private String targetTable;

    @Column(name = "target_id", nullable = false, length = 256)
    private String targetId;

    @Column(name = "target_label", length = 256)
    private String targetLabel;

    public AuditEntryTarget() {}

    public AuditEntryTarget(int position, String targetTable, String targetId, String targetLabel) {
        this.position = position;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
    }

    public Long getId() {
        return id;
    }

    public AuditEntry getEntry() {
        return entry;
    }

    public void setEntry(AuditEntry entry) {
        this.entry = entry;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }
}
