package com.yuzhi.dts.admin.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admin_approval_request")
public class AdminApprovalRequest extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "requester", nullable = false, length = 128)
    private String requester;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "approver", length = 128)
    private String approver;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("seqNumber ASC")
    private List<AdminApprovalItem> items = new ArrayList<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public List<AdminApprovalItem> getItems() {
        return items;
    }

    public void setItems(List<AdminApprovalItem> items) {
        this.items = items;
    }

    public void addItem(AdminApprovalItem item) {
        if (item == null) {
            return;
        }
        item.setRequest(this);
        this.items.add(item);
    }

    public void removeItem(AdminApprovalItem item) {
        if (item == null) {
            return;
        }
        this.items.remove(item);
        item.setRequest(null);
    }
}
