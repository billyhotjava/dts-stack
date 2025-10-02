package com.yuzhi.dts.platform.domain.governance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "gov_rule_version")
public class GovRuleVersion extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    @JsonIgnoreProperties(value = { "versions", "latestVersion" }, allowSetters = true)
    private GovRule rule;

    @Column(name = "version")
    private Integer version;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "definition", columnDefinition = "jsonb")
    private String definition;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "notes", length = 1024)
    private String notes;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @OneToMany(mappedBy = "ruleVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = { "ruleVersion" }, allowSetters = true)
    private Set<GovRuleBinding> bindings = new LinkedHashSet<>();

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovRule getRule() {
        return rule;
    }

    public void setRule(GovRule rule) {
        this.rule = rule;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Set<GovRuleBinding> getBindings() {
        return bindings;
    }

    public void setBindings(Set<GovRuleBinding> bindings) {
        this.bindings = bindings;
    }
}

