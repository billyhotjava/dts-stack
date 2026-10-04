package com.yuzhi.dts.platform.domain.security;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "security_baseline_remediation")
public class SecurityBaselineRemediation extends AbstractAuditingEntity<String> implements Serializable {

    @Id
    @Column(name = "check_key", length = 64)
    private String checkKey;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Override
    public String getId() {
        return checkKey;
    }

    public void setId(String id) {
        this.checkKey = id;
    }

    public String getCheckKey() {
        return checkKey;
    }

    public void setCheckKey(String checkKey) {
        this.checkKey = checkKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

