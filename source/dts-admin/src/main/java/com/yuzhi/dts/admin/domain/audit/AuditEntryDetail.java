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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_entry_detail")
public class AuditEntryDetail implements Serializable {

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

    @Column(name = "detail_key", nullable = false, length = 128)
    private String detailKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_value")
    private Object detailValue;

    public AuditEntryDetail() {}

    public AuditEntryDetail(int position, String detailKey, Object detailValue) {
        this.position = position;
        this.detailKey = detailKey;
        this.detailValue = detailValue;
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

    public String getDetailKey() {
        return detailKey;
    }

    public void setDetailKey(String detailKey) {
        this.detailKey = detailKey;
    }

    public Object getDetailValue() {
        return detailValue;
    }

    public void setDetailValue(Object detailValue) {
        this.detailValue = detailValue;
    }
}
