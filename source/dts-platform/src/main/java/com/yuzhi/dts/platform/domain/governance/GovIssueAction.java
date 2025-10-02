package com.yuzhi.dts.platform.domain.governance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "gov_issue_action")
public class GovIssueAction extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnoreProperties(value = { "actions", "complianceBatch" }, allowSetters = true)
    private GovIssueTicket ticket;

    @Column(name = "action_type", length = 32)
    private String actionType;

    @Column(name = "actor", length = 64)
    private String actor;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Column(name = "attachments_json", columnDefinition = "jsonb")
    private String attachmentsJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public GovIssueTicket getTicket() {
        return ticket;
    }

    public void setTicket(GovIssueTicket ticket) {
        this.ticket = ticket;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(String attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }
}

